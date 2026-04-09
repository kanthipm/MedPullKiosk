package com.medpull.kiosk.data.engine

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import com.medpull.kiosk.data.local.dao.AuditLogDao
import com.medpull.kiosk.data.local.entities.AuditLogEntity
import com.medpull.kiosk.data.models.FormField
import com.medpull.kiosk.data.models.IntakeAction
import com.medpull.kiosk.data.remote.ai.AiResponse
import com.medpull.kiosk.data.remote.ai.ClaudeApiService
import com.medpull.kiosk.data.repository.AuthRepository
import com.medpull.kiosk.data.repository.GuidedIntakeRepository
import com.medpull.kiosk.ui.screens.ai.ChatMessage
import com.medpull.kiosk.utils.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates the conversational intake session.
 *
 * Responsibilities (all as private methods — extract into separate classes when adding form #2):
 *   buildSystemPrompt()  — converts form schema + current state into Claude's system prompt
 *   processInput()       — calls Claude, parses JSON response into IntakeAction, handles retries
 *   parseResponse()      — JSON string → IntakeAction (confidence thresholds, action routing)
 *   validateField()      — checks type/required constraints
 *   checkEscalation()    — returns true if field should be auto-flagged for staff
 *
 * Data flow:
 *   ViewModel calls processInput(message, fields, history, language, clarificationCounts)
 *     → buildSystemPrompt(fields, language)
 *     → ClaudeApiService.sendMessage(message, history, systemPrompt)
 *     → parseResponse(json) → IntakeAction
 *     → if malformed: retry once, then Fallback
 *     → return IntakeAction to ViewModel
 *
 * The ViewModel owns all mutable state. The engine is stateless.
 */
@Singleton
class IntakeConversationEngine @Inject constructor(
    private val claudeApiService: ClaudeApiService,
    private val intakeRepository: GuidedIntakeRepository,
    private val auditLogDao: AuditLogDao,
    private val authRepository: AuthRepository,
    private val gson: Gson,
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "IntakeConversationEngine"
        private const val SCHEMA_FILE = "schemas/coastal_gateway_intake.json"
        private const val MAX_HISTORY_TURNS = 10
        private const val CONFIDENCE_ACCEPT = 0.8f
        private const val CONFIDENCE_CONFIRM = 0.5f
        private const val ESCALATION_THRESHOLD = 2
    }

    // Cached schema JSON — loaded once per app session
    private var cachedSchema: String? = null

    /**
     * Main entry point. Call from ViewModel for every patient message.
     *
     * @param message         Raw patient input
     * @param fields          All form fields with current values
     * @param history         Full conversation history (engine trims to last 10)
     * @param language        Patient's selected language code (e.g. "es")
     * @param clarificationCounts  Per-field count of clarification attempts this session
     * @param malformedCount  Number of malformed JSON responses so far this session
     * @param formId          For fallback question generation
     */
    suspend fun processInput(
        message: String,
        fields: List<FormField>,
        history: List<ChatMessage>,
        language: String,
        clarificationCounts: Map<String, Int>,
        malformedCount: Int,
        formId: String
    ): IntakeAction {
        val systemPrompt = buildSystemPrompt(fields, language, history)
        val trimmedHistory = trimHistory(history, fields)

        logAiQuery(message)

        return when (val response = claudeApiService.sendMessage(
            userMessage = message,
            conversationHistory = trimmedHistory,
            systemPrompt = systemPrompt,
            model = Constants.AI.CONVERSATION_MODEL,
            maxTokens = Constants.AI.CONVERSATION_MAX_TOKENS
        )) {
            is AiResponse.Success -> {
                parseResponseWithRetry(
                    responseText = response.message,
                    message = message,
                    trimmedHistory = trimmedHistory,
                    systemPrompt = systemPrompt,
                    fields = fields,
                    clarificationCounts = clarificationCounts,
                    malformedCount = malformedCount,
                    formId = formId,
                    language = language
                )
            }
            is AiResponse.Error -> {
                Log.e(TAG, "Claude API error: ${response.message}")
                logAudit("AI_ERROR", response.message)
                fallbackAction(fields, formId)
            }
        }
    }

    // ─── System Prompt ────────────────────────────────────────────────────────

    private fun buildSystemPrompt(
        fields: List<FormField>,
        language: String,
        history: List<ChatMessage>
    ): String {
        val languageName = when (language) {
            "es" -> "Spanish"; "zh" -> "Chinese"; "fr" -> "French"
            "hi" -> "Hindi"; "ar" -> "Arabic"; else -> "English"
        }

        val schema = loadSchema()

        // Filled fields as summary — only IDs to save tokens
        val filledIds = fields.filter { !it.value.isNullOrBlank() }.map { it.id }
        val filledSummary = if (filledIds.isEmpty()) "None yet"
        else filledIds.joinToString(", ")

        // Unfilled required fields — send full info
        val unfilledRequired = fields
            .filter { it.required && it.value.isNullOrBlank() }
            .joinToString("\n") { f ->
                "  - ${f.id} (${f.fieldType.name.lowercase()}): ${f.translatedText ?: f.fieldName}"
            }

        // Unfilled optional fields
        val unfilledOptional = fields
            .filter { !it.required && it.value.isNullOrBlank() }
            .joinToString("\n") { f ->
                "  - ${f.id} (${f.fieldType.name.lowercase()}): ${f.translatedText ?: f.fieldName}"
            }

        // Conversation summary for older turns
        val olderSummary = if (history.size > MAX_HISTORY_TURNS) {
            val confirmed = fields.filter { !it.value.isNullOrBlank() }
                .joinToString(", ") { "${it.id}=${it.value}" }
            "Previously collected: $confirmed"
        } else ""

        return """
You are a patient intake assistant at a community health center.
Your job: ask the patient questions ONE AT A TIME to fill out their intake form.
Always respond in $languageName. Be warm, clear, and simple — patients may have low literacy.

FORM SCHEMA:
$schema

CURRENT STATE:
Already filled (do not ask again): $filledSummary

Still needed (required):
$unfilledRequired

Still needed (optional — ask only if natural):
$unfilledOptional

${if (olderSummary.isNotEmpty()) "CONVERSATION SUMMARY:\n$olderSummary\n" else ""}
RULES:
- Ask one question at a time in $languageName
- If the patient's answer fills multiple fields, extract ALL of them in field_updates
- Skip fields that clearly don't apply based on prior answers (e.g. insurance fields if uninsured)
- If an answer is unclear, ask ONE clarifying question — do not repeat the same question twice
- Flag anything needing clinic staff attention
- When ALL required fields are filled, use action "transition_to_review"

RESPOND WITH VALID JSON ONLY — no other text:
{
  "action": "set_field" | "skip_section" | "flag_for_clinic" | "ask_clarification" | "transition_to_review",
  "question_text": "your next question or message to the patient in $languageName",
  "field_updates": [{"field_id": "...", "value": "...", "confidence": 0.0-1.0}],
  "skip_field_ids": ["field_id_1", "field_id_2"],
  "flag_field_id": "field_id",
  "flag_reason": "reason for flagging",
  "reasoning": "brief explanation for audit trail"
}
        """.trimIndent()
    }

    // ─── History Trimming ─────────────────────────────────────────────────────

    private fun trimHistory(history: List<ChatMessage>, fields: List<FormField>): List<ChatMessage> {
        return if (history.size <= MAX_HISTORY_TURNS) {
            history
        } else {
            // Keep last MAX_HISTORY_TURNS turns; older context is in the system prompt's CURRENT STATE
            history.takeLast(MAX_HISTORY_TURNS)
        }
    }

    // ─── Response Parsing ─────────────────────────────────────────────────────

    private suspend fun parseResponseWithRetry(
        responseText: String,
        message: String,
        trimmedHistory: List<ChatMessage>,
        systemPrompt: String,
        fields: List<FormField>,
        clarificationCounts: Map<String, Int>,
        malformedCount: Int,
        formId: String,
        language: String
    ): IntakeAction {
        val action = parseResponse(responseText, clarificationCounts, fields, formId)
        if (action != null) return action

        // Malformed JSON — retry once with explicit JSON instruction
        Log.w(TAG, "Malformed response, retrying with JSON instruction")
        logAudit("MALFORMED_RESPONSE", responseText.take(200))

        val retryHistory = trimmedHistory + ChatMessage(
            text = message, isFromUser = true, timestamp = System.currentTimeMillis()
        )
        val retryResponse = claudeApiService.sendMessage(
            userMessage = "You must respond with valid JSON matching the schema in the system prompt. No other text.",
            conversationHistory = retryHistory,
            systemPrompt = systemPrompt,
            model = Constants.AI.CONVERSATION_MODEL,
            maxTokens = Constants.AI.CONVERSATION_MAX_TOKENS
        )

        if (retryResponse is AiResponse.Success) {
            val retryAction = parseResponse(retryResponse.message, clarificationCounts, fields, formId)
            if (retryAction != null) return retryAction
        }

        // Both attempts failed
        val newMalformedCount = malformedCount + 1
        Log.e(TAG, "Both parse attempts failed. Session malformed count: $newMalformedCount")
        logAudit("MALFORMED_RESPONSE_FINAL", "Falling back to template question")

        if (newMalformedCount >= 3) {
            logAudit("ESCALATE_MALFORMED", "3+ malformed responses in session")
        }

        return fallbackAction(fields, formId)
    }

    private suspend fun parseResponse(
        json: String,
        clarificationCounts: Map<String, Int>,
        fields: List<FormField>,
        formId: String
    ): IntakeAction? {
        return try {
            // Strip markdown code fences if Claude wrapped the JSON
            val cleaned = json.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()

            val obj = JSONObject(cleaned)
            val action = obj.optString("action", "")
            val questionText = obj.optString("question_text", "")
            val reasoning = obj.optString("reasoning", "")

            when (action) {
                "set_field" -> {
                    val updates = obj.optJSONArray("field_updates")
                    if (updates == null || updates.length() == 0) return null

                    // Take the first field update (primary field being answered)
                    val first = updates.getJSONObject(0)
                    val fieldId = first.optString("field_id", "")
                    val value = first.optString("value", "")
                    val confidence = first.optDouble("confidence", 0.8).toFloat()

                    if (fieldId.isBlank() || value.isBlank()) return null

                    // Check staff escalation: 2+ clarifications and still low confidence
                    if (checkEscalation(fieldId, confidence, clarificationCounts)) {
                        logAudit("STAFF_ESCALATION", "Field $fieldId escalated after ${clarificationCounts[fieldId]} attempts")
                        return IntakeAction.FlagForClinic(
                            fieldId = fieldId,
                            reason = "Patient unable to provide clear answer after ${clarificationCounts[fieldId]} attempts",
                            questionText = questionText.ifBlank { "A staff member will help with this. Let's move on." }
                        )
                    }

                    when {
                        confidence >= CONFIDENCE_ACCEPT -> IntakeAction.SetField(
                            fieldId = fieldId,
                            value = value,
                            confidence = confidence,
                            questionText = questionText,
                            reasoning = reasoning
                        )
                        confidence >= CONFIDENCE_CONFIRM -> IntakeAction.AskClarification(
                            questionText = questionText.ifBlank { "You said \"$value\" — is that correct?" },
                            fieldId = fieldId,
                            reasoning = "Confidence $confidence — requesting confirmation"
                        )
                        else -> IntakeAction.AskClarification(
                            questionText = questionText.ifBlank { "I didn't quite catch that. Could you say it again?" },
                            fieldId = fieldId,
                            reasoning = "Confidence $confidence — requesting clarification"
                        )
                    }
                }

                "ask_clarification" -> IntakeAction.AskClarification(
                    questionText = questionText,
                    reasoning = reasoning
                )

                "skip_section" -> {
                    val skipIds = obj.optJSONArray("skip_field_ids")
                        ?.let { arr -> (0 until arr.length()).map { arr.getString(it) } }
                        ?: emptyList()
                    IntakeAction.SkipSection(
                        fieldIds = skipIds,
                        questionText = questionText,
                        reasoning = reasoning
                    )
                }

                "flag_for_clinic" -> IntakeAction.FlagForClinic(
                    fieldId = obj.optString("flag_field_id", ""),
                    reason = obj.optString("flag_reason", reasoning),
                    questionText = questionText
                )

                "transition_to_review" -> IntakeAction.TransitionToReview

                else -> {
                    Log.w(TAG, "Unknown action type: $action")
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse response JSON", e)
            null
        }
    }

    // ─── Staff Escalation ─────────────────────────────────────────────────────

    /**
     * Returns true if this field has been clarified enough times at low confidence
     * that it should be escalated to clinic staff rather than asking again.
     */
    private fun checkEscalation(
        fieldId: String,
        confidence: Float,
        clarificationCounts: Map<String, Int>
    ): Boolean {
        val count = clarificationCounts[fieldId] ?: 0
        return count >= ESCALATION_THRESHOLD && confidence < CONFIDENCE_CONFIRM
    }

    // ─── Field Validation ─────────────────────────────────────────────────────

    fun validateField(field: FormField, value: String): Boolean {
        if (field.required && value.isBlank()) return false
        return true  // Type validation added when schema has range/format constraints
    }

    // ─── Completion Check ─────────────────────────────────────────────────────

    fun allRequiredFilled(fields: List<FormField>): Boolean =
        fields.filter { it.required }.all { !it.value.isNullOrBlank() }

    // ─── Schema Loading ───────────────────────────────────────────────────────

    private fun loadSchema(): String {
        cachedSchema?.let { return it }
        return try {
            val json = context.assets.open(SCHEMA_FILE).bufferedReader().readText()
            cachedSchema = json
            json
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load schema from assets/$SCHEMA_FILE", e)
            "{\"error\": \"Schema not found — Phase 0 required\"}"
        }
    }

    // ─── Fallback ─────────────────────────────────────────────────────────────

    private fun fallbackAction(fields: List<FormField>, formId: String): IntakeAction {
        val nextUnfilled = fields.firstOrNull { it.required && it.value.isNullOrBlank() }
            ?: fields.firstOrNull { it.value.isNullOrBlank() }
        return IntakeAction.Fallback(
            questionText = nextUnfilled?.let {
                intakeRepository.generateFallbackQuestion(it)
            } ?: "Is there anything else you'd like to add?",
            fieldId = nextUnfilled?.id
        )
    }

    // ─── Audit Logging ────────────────────────────────────────────────────────

    private suspend fun logAiQuery(query: String) {
        logAudit(Constants.Audit.ACTION_AI_QUERY, "Intake query: ${query.take(100)}")
    }

    private suspend fun logAudit(action: String, description: String) {
        try {
            val userId = authRepository.getCurrentUserId() ?: "unknown"
            auditLogDao.insertLog(
                AuditLogEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    userId = userId,
                    action = action,
                    resourceType = "INTAKE_AI",
                    resourceId = null,
                    ipAddress = "local",
                    deviceId = "tablet",
                    description = description,
                    metadata = null
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Audit log failed", e)
        }
    }
}
