package com.medpull.kiosk.data.engine

import android.util.Log
import com.google.gson.Gson
import com.medpull.kiosk.data.local.dao.AuditLogDao
import com.medpull.kiosk.data.local.entities.AuditLogEntity
import com.medpull.kiosk.data.models.FieldParseResult
import com.medpull.kiosk.data.models.FieldType
import com.medpull.kiosk.data.models.FieldUpdate
import com.medpull.kiosk.data.models.FormField
import com.medpull.kiosk.data.remote.ai.AiResponse
import com.medpull.kiosk.data.remote.ai.GrokApiService
import com.medpull.kiosk.data.repository.AuthRepository
import com.medpull.kiosk.utils.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stateless intake conversation engine.
 *
 * The ViewModel owns all progression logic — which field to ask, when to advance,
 * which fields to skip. This engine has exactly two jobs:
 *
 *   generateQuestion(field, context)  — produce a warm, natural question for one field
 *   parseAnswer(field, answer, allFields) — extract the field value from free text
 *
 * Both methods are focused single-field calls with small token budgets. No free-form
 * multi-field steering, no "decide what to ask next" logic. That was the root cause
 * of the 29% completion problem.
 */
@Singleton
class IntakeConversationEngine @Inject constructor(
    private val apiService: GrokApiService,
    private val auditLogDao: AuditLogDao,
    private val authRepository: AuthRepository,
    private val gson: Gson,
    @ApplicationContext private val context: android.content.Context
) {

    companion object {
        private const val TAG = "IntakeEngine"
    }

    // ─── Question Generation ──────────────────────────────────────────────────

    /**
     * Generate a single warm question for the given field.
     * Falls back to a template question on API failure — callers always get a string.
     */
    suspend fun generateQuestion(
        field: FormField,
        filledFields: List<FormField>,
        language: String,
        guardianMode: Boolean
    ): String {
        val languageName = languageName(language)
        val filledSummary = filledFields
            .take(6)
            .joinToString(", ") { "${it.id}=${it.value}" }
            .ifBlank { "none yet" }

        val prompt = buildString {
            appendLine("Generate ONE warm, natural intake question in $languageName.")
            appendLine("Field: ${field.id} (${field.fieldType.name.lowercase()})")
            appendLine("Label: ${field.translatedText ?: field.fieldName}")
            if (!field.description.isNullOrBlank()) {
                appendLine("Schema instructions: ${field.description}")
            }
            if (field.options.isNotEmpty()) {
                appendLine("Note: answer options shown as buttons in UI — do NOT list them in the question.")
            }
            if (guardianMode) {
                appendLine("Use third-person framing — ask about 'the patient', not 'you'.")
            }
            appendLine("Already collected: $filledSummary")
            appendLine()
            append("Return JSON only: {\"question\": \"your question here\"}")
        }

        logAudit("AI_QUESTION_GEN", "Field: ${field.id}")

        return when (val resp = apiService.sendMessage(
            userMessage = prompt,
            conversationHistory = emptyList(),
            systemPrompt = "You generate intake form questions. Respond with valid JSON only.",
            model = Constants.AI.CONVERSATION_MODEL,
            maxTokens = 150
        )) {
            is AiResponse.Success -> parseQuestionJson(resp.message) ?: fallbackQuestion(field, guardianMode)
            is AiResponse.Error -> {
                Log.w(TAG, "Question gen failed for ${field.id}: ${resp.message}")
                fallbackQuestion(field, guardianMode)
            }
        }
    }

    private fun parseQuestionJson(raw: String): String? {
        return try {
            val cleaned = raw.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()
            JSONObject(cleaned).optString("question", "").ifBlank { null }
        } catch (e: Exception) {
            // If it's plain text (not JSON), use it directly if reasonable length
            raw.trim().takeIf { it.isNotBlank() && it.length < 400 && !it.startsWith("{") }
        }
    }

    private fun fallbackQuestion(field: FormField, guardianMode: Boolean = false): String {
        val label = field.translatedText ?: field.fieldName
        val ref = if (guardianMode) "the patient's" else "your"
        return when (field.fieldType) {
            FieldType.SIGNATURE -> "Please provide your signature below."
            FieldType.MULTI_SELECT -> "Which of the following apply to $ref $label? Select all that apply."
            FieldType.DATE -> "What is $ref $label?"
            else -> "What is $ref $label?"
        }
    }

    // ─── Answer Parsing ───────────────────────────────────────────────────────

    /**
     * Parse a patient's free-text answer for [field].
     *
     * For RADIO/DROPDOWN where the answer exactly matches an option, short-circuits
     * without an API call. For everything else, calls the model with a tightly scoped
     * prompt asking only for the target field value (plus any obvious bonus fields).
     */
    suspend fun parseAnswer(
        field: FormField,
        userAnswer: String,
        allFields: List<FormField>,
        language: String
    ): FieldParseResult {
        // Fast path: exact option match for radio/dropdown
        if (field.fieldType in listOf(FieldType.RADIO, FieldType.DROPDOWN)) {
            val match = field.options.find { it.equals(userAnswer.trim(), ignoreCase = true) }
            if (match != null) return FieldParseResult(value = match, confidence = 1.0f)
        }

        // Fast path: multi-select value comes in pre-formatted from chip UI
        if (field.fieldType == FieldType.MULTI_SELECT) {
            val trimmed = userAnswer.trim()
            if (trimmed.isNotBlank()) {
                return FieldParseResult(value = trimmed, confidence = 1.0f)
            }
        }

        // Other unfilled fields — offer them as bonus fill candidates (limit to 8 for token budget)
        val bonusCandidates = allFields
            .filter { f ->
                f.id != field.id &&
                f.value.isNullOrBlank() &&
                f.fieldType !in listOf(FieldType.STATIC_LABEL, FieldType.SIGNATURE, FieldType.MULTI_SELECT)
            }
            .take(8)
            .joinToString("\n") { "  ${it.id}: ${it.translatedText ?: it.fieldName}" }

        val prompt = buildString {
            appendLine("Extract the value for this intake field from the patient's answer.")
            appendLine()
            appendLine("Target field: ${field.id} (${field.fieldType.name.lowercase()})")
            appendLine("Label: ${field.translatedText ?: field.fieldName}")
            if (field.options.isNotEmpty()) appendLine("Valid options: ${field.options.joinToString(", ")}")
            if (!field.description.isNullOrBlank()) appendLine("Format hint: ${field.description}")
            appendLine()
            appendLine("Patient said: \"$userAnswer\"")
            if (bonusCandidates.isNotBlank()) {
                appendLine()
                appendLine("If the answer also clearly fills these other unfilled fields, include them in also_fills:")
                appendLine(bonusCandidates)
            }
            appendLine()
            appendLine("""Return JSON only:
{
  "value": "extracted value, or null if unclear",
  "confidence": 0.0-1.0,
  "also_fills": [{"field_id": "...", "value": "..."}],
  "needs_clarification": false,
  "clarification_question": "follow-up question if value is null"
}""")
        }

        logAudit("AI_PARSE_ANSWER", "Field: ${field.id}")

        return when (val resp = apiService.sendMessage(
            userMessage = prompt,
            conversationHistory = emptyList(),
            systemPrompt = "You extract field values from patient answers. Return valid JSON only.",
            model = Constants.AI.CONVERSATION_MODEL,
            maxTokens = 400
        )) {
            is AiResponse.Success -> parseFieldResult(resp.message)
            is AiResponse.Error -> {
                Log.e(TAG, "Parse answer failed for ${field.id}: ${resp.message}")
                FieldParseResult(
                    needsClarification = true,
                    clarificationQuestion = "I'm having trouble connecting. Could you try again?"
                )
            }
        }
    }

    private fun parseFieldResult(raw: String): FieldParseResult {
        return try {
            val cleaned = raw.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()
            val obj = JSONObject(cleaned)

            val rawValue = obj.optString("value", "null")
            val value = if (rawValue == "null" || rawValue.isBlank()) null else rawValue
            val confidence = obj.optDouble("confidence", 0.0).toFloat()
            val needsClarification = obj.optBoolean("needs_clarification", false) || value == null
            val clarificationQuestion = obj.optString("clarification_question", "")

            val alsoFills = mutableListOf<FieldUpdate>()
            val arr = obj.optJSONArray("also_fills")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val entry = arr.getJSONObject(i)
                    val fId = entry.optString("field_id", "")
                    val fVal = entry.optString("value", "")
                    if (fId.isNotBlank() && fVal.isNotBlank() && fVal != "null") {
                        alsoFills += FieldUpdate(fId, fVal, 0.9f)
                    }
                }
            }

            FieldParseResult(
                value = value,
                confidence = confidence,
                alsoFills = alsoFills,
                needsClarification = needsClarification,
                clarificationQuestion = clarificationQuestion.ifBlank { "Could you clarify that?" }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse FieldParseResult: ${e.message}")
            FieldParseResult(
                needsClarification = true,
                clarificationQuestion = "I didn't quite catch that. Could you try again?"
            )
        }
    }

    // ─── Utility ──────────────────────────────────────────────────────────────

    fun allRequiredFilled(fields: List<FormField>, skippedFieldIds: Set<String> = emptySet()): Boolean =
        fields.filter { it.required && it.id !in skippedFieldIds }.all { !it.value.isNullOrBlank() }

    private fun languageName(code: String) = when (code) {
        "es" -> "Spanish"; "zh" -> "Chinese"; "fr" -> "French"
        "hi" -> "Hindi"; "ar" -> "Arabic"; else -> "English"
    }

    // ─── Audit Logging ────────────────────────────────────────────────────────

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
