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
        // Strip trailing punctuation so we can append our own cleanly
        val label = (field.translatedText ?: field.fieldName).trimEnd('?', '.', ' ')
        val ref = if (guardianMode) "the patient's" else "your"
        return when (field.fieldType) {
            FieldType.SIGNATURE -> "Please sign below."
            FieldType.MULTI_SELECT -> "Which of the following apply to $ref $label? Select all that apply."
            else -> {
                // Labels that already start with a question word should be used as-is
                val lowerLabel = label.lowercase()
                val startsAsQuestion = listOf(
                    "has ", "have ", "is ", "are ", "do ", "does ", "did ",
                    "can ", "will ", "was ", "were ", "should "
                ).any { lowerLabel.startsWith(it) }
                if (startsAsQuestion) "$label?" else "What is $ref $label?"
            }
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
                Log.w(TAG, "Parse answer offline fallback for ${field.id}: ${resp.message}")
                offlineParse(field, userAnswer, allFields)
            }
        }
    }

    /**
     * Offline fallback: accept the answer as typed when the AI API is unavailable.
     *
     * Special handling:
     *  - Blank → ask for clarification
     *  - Address street field + full address typed → split city/state/zip into alsoFills
     *  - Everything else → pass value through at 0.9 confidence
     */
    private fun offlineParse(
        field: FormField,
        answer: String,
        allFields: List<FormField> = emptyList()
    ): FieldParseResult {
        val trimmed = answer.trim()
        if (trimmed.isBlank()) {
            return FieldParseResult(
                needsClarification = true,
                clarificationQuestion = "Could you provide your ${field.fieldName.lowercase()}?"
            )
        }

        // Address street field: try to extract city/state/zip from a full address answer
        val isStreetField = field.id.endsWith("_street") ||
            field.fieldName.lowercase().let { it.contains("street") || it.contains("address line") }
        if (isStreetField && trimmed.contains(",")) {
            val extracted = tryExtractAddressParts(trimmed, field, allFields)
            if (extracted != null) return extracted
        }

        return FieldParseResult(value = trimmed, confidence = 0.9f)
    }

    /**
     * If the patient typed a full address (e.g. "123 Main St, Houston, TX 77001") into a street
     * field, parse out the components and return them as alsoFills so we can skip the follow-up
     * city/state/zip questions automatically.
     */
    private fun tryExtractAddressParts(
        address: String,
        streetField: FormField,
        allFields: List<FormField>
    ): FieldParseResult? {
        val parts = address.split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (parts.size < 2) return null

        val street = parts[0]

        // Determine the field-id prefix (mailing_, physical_, or empty)
        val prefix = when {
            streetField.id.startsWith("mailing_") -> "mailing_"
            streetField.id.startsWith("physical_") -> "physical_"
            else -> ""
        }
        val cityId = "${prefix}city"
        val stateId = "${prefix}state"
        val zipId = "${prefix}zip"

        // Only populate a field if it exists in this form and is currently blank
        fun candidate(id: String) = allFields.find { it.id == id && it.value.isNullOrBlank() }
        val cityField = candidate(cityId)
        val stateField = candidate(stateId)
        val zipField = candidate(zipId)
        if (cityField == null && stateField == null && zipField == null) return null

        // "TX 77001" or "TX" or "77001"
        val stateZipRx = Regex("""^([A-Za-z]{2})\s+(\d{5}(?:-\d{4})?)$""")
        val stateOnlyRx = Regex("""^([A-Za-z]{2})$""")
        val zipOnlyRx = Regex("""^\d{5}(?:-\d{4})?$""")

        var city: String? = null
        var state: String? = null
        var zip: String? = null

        when {
            parts.size >= 3 -> {
                // "123 Main St, Houston, TX 77001"
                city = parts[1]
                val last = parts.last()
                val m = stateZipRx.find(last)
                if (m != null) {
                    state = m.groupValues[1].uppercase()
                    zip = m.groupValues[2]
                } else if (stateOnlyRx.matches(last)) {
                    state = last.uppercase()
                } else if (zipOnlyRx.matches(last)) {
                    zip = last
                    if (parts.size >= 4) state = parts[parts.size - 2].uppercase()
                } else {
                    state = last
                }
            }
            parts.size == 2 -> {
                // "123 Main St, Houston TX 77001"  or  "123 Main St, Houston"
                val last = parts[1]
                val cityStateZipRx = Regex("""^(.*?)\s+([A-Za-z]{2})\s+(\d{5}(?:-\d{4})?)$""")
                val m = cityStateZipRx.find(last)
                if (m != null) {
                    city = m.groupValues[1]
                    state = m.groupValues[2].uppercase()
                    zip = m.groupValues[3]
                } else {
                    val mStateZip = stateZipRx.find(last)
                    if (mStateZip != null) {
                        state = mStateZip.groupValues[1].uppercase()
                        zip = mStateZip.groupValues[2]
                    } else {
                        city = last   // just a city was appended
                    }
                }
            }
        }

        val alsoFills = mutableListOf<FieldUpdate>()
        if (city != null && cityField != null) alsoFills += FieldUpdate(cityId, city, 0.9f)
        if (state != null && stateField != null) alsoFills += FieldUpdate(stateId, state, 0.9f)
        if (zip != null && zipField != null) alsoFills += FieldUpdate(zipId, zip, 0.9f)
        if (alsoFills.isEmpty()) return null

        return FieldParseResult(value = street, confidence = 0.9f, alsoFills = alsoFills)
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

    // ─── Clarification Q&A ───────────────────────────────────────────────────

    /**
     * Answer a patient's clarifying question about the current field.
     * Called when the patient asks something rather than providing an answer.
     */
    suspend fun answerClarification(
        question: String,
        field: FormField,
        language: String
    ): String {
        val languageName = languageName(language)
        val prompt = buildString {
            appendLine("A patient at a medical kiosk is asking a clarifying question about an intake form field.")
            appendLine("Answer their question clearly and reassuringly in $languageName.")
            appendLine("Keep the answer brief (2-3 sentences max). Use plain language, no jargon.")
            appendLine()
            appendLine("Current form field: ${field.fieldName}")
            if (!field.description.isNullOrBlank()) appendLine("Field context: ${field.description}")
            appendLine()
            appendLine("Patient's question: \"$question\"")
            appendLine()
            append("Return JSON only: {\"answer\": \"your brief answer here\"}")
        }

        logAudit("AI_CLARIFICATION", "Field: ${field.id}, Q: $question")

        return when (val resp = apiService.sendMessage(
            userMessage = prompt,
            conversationHistory = emptyList(),
            systemPrompt = "You are a helpful medical assistant. Answer patient questions briefly and kindly. Return valid JSON only.",
            model = Constants.AI.CONVERSATION_MODEL,
            maxTokens = 200
        )) {
            is AiResponse.Success -> {
                try {
                    val cleaned = resp.message.trim()
                        .removePrefix("```json").removePrefix("```")
                        .removeSuffix("```").trim()
                    JSONObject(cleaned).optString("answer", "").ifBlank { resp.message.trim() }
                } catch (e: Exception) {
                    resp.message.trim().takeIf { it.isNotBlank() }
                        ?: "Happy to help! This field just needs your ${field.fieldName.lowercase()}."
                }
            }
            is AiResponse.Error -> buildOfflineClarification(question, field)
        }
    }

    /**
     * Offline clarification: builds a useful, contextual answer from field metadata
     * so patients aren't left with a generic "please fill it in" message when the
     * AI API is unavailable.
     */
    private fun buildOfflineClarification(question: String, field: FormField): String {
        val label = field.fieldName.trimEnd('?', ':').trim()
        val desc = field.description?.takeIf { it.isNotBlank() && !it.startsWith("ai_note") }

        // If the schema description is informative, lead with it
        val descLine = if (desc != null) "$desc " else ""

        return when {
            // Insurance fields
            field.id.contains("insurance") && field.id.contains("id") ->
                "${descLine}This is your member ID or subscriber ID — you'll find it on your insurance card, usually labeled \"ID\" or \"Member ID\"."
            field.id.contains("insurance") && field.id.contains("group") ->
                "${descLine}The group number is on your insurance card, often labeled \"Group\" or \"Grp #\". If you don't have it handy you can leave it blank and we can look it up."
            field.id.contains("insurance_provider") || field.id.contains("insurance") && field.id.contains("provider") ->
                "${descLine}Write the name of your insurance company — for example, Blue Cross Blue Shield, Aetna, UnitedHealthcare, Medicaid, or Medicare."
            field.id == "policyholder_is_self" ->
                "This asks whether the person whose name is on the insurance policy is you, or someone else (like a parent or spouse). If the card has your name, select Yes."
            field.id.contains("policyholder") ->
                "${descLine}The policyholder is the person whose name is listed as the primary member on the insurance card — sometimes that's a parent or spouse rather than the patient."
            field.id.contains("has_secondary_insurance") ->
                "Secondary insurance is a second health plan that can cover costs the primary insurance doesn't. If you only have one insurance plan, select No."

            // Address fields
            field.id.contains("mailing") && field.id.contains("address") ->
                "This is the address where you receive mail — it's okay if it's a P.O. box. Just type your street number and name, like \"123 Main St\"."
            field.id.contains("physical") && field.id.contains("address") ->
                "This is where you actually live or stay, even if you get mail somewhere else. If your home address is the same as your mailing address, you can skip this."
            field.id.contains("zip") ->
                "Your ZIP code is the 5-digit number at the end of your address, like 77001."
            field.id.contains("state") && field.fieldType == FieldType.RADIO ->
                "Select the U.S. state you live in. The options are shown as buttons below the question."

            // Contact fields
            field.id.contains("emergency_contact") ->
                "${descLine}This is someone we should call if we can't reach you or if there's a medical emergency — a family member or close friend."
            field.id == "phone_primary" ->
                "Please enter your best phone number including area code, like (713) 555-1234 or 7135551234."

            // Date fields
            field.fieldType == FieldType.DATE ->
                "${descLine}Please enter the date in MM/DD/YYYY format — for example, 01/15/1990."

            // Yes/No fields
            field.fieldType == FieldType.RADIO && field.options.map { it.lowercase() }.containsAll(listOf("yes", "no")) ->
                "${descLine}Just say Yes or No, or tap the button. ${if (field.options.isNotEmpty()) "Your choices are: ${field.options.joinToString(", ")}." else ""}"

            // Medical history
            field.id.contains("medications") ->
                "List any prescription or over-the-counter medicines you take regularly, including vitamins and supplements. If you take none, just say No."
            field.id.contains("allergies") ->
                "Tell us about any allergies to medicines, foods, or other substances. If you have no known allergies, say No."
            field.id.contains("medical_conditions") ->
                "List any ongoing health conditions or diagnoses you've been given by a doctor — like diabetes, asthma, high blood pressure, etc. If none, leave it blank."
            field.id.contains("family_history") ->
                "We're asking about health conditions that run in your family — things like heart disease, cancer, or diabetes in parents, siblings, or grandparents."
            field.id.contains("surgeries") ->
                "List any operations or surgical procedures you've had, including the approximate year if you remember. If you've had none, say No."

            // Consent fields
            field.id.contains("hipaa") ->
                "HIPAA is a federal law that protects your health information. This acknowledges that you've received our privacy notice explaining your rights and how we use your data."
            field.id.contains("sliding_fee") ->
                "Our sliding fee scale means the cost of your care may be adjusted based on your household income so it's affordable. This just asks you to acknowledge that option exists."
            field.id.contains("photo_consent") ->
                "This asks if you give us permission to take or use photos for your care — for example, photographing a skin condition for your medical record."

            // Signature
            field.fieldType == FieldType.SIGNATURE ->
                "Please sign your name in the box using your finger. This confirms that the information you've provided is accurate to the best of your knowledge."

            // Representative / guardian
            field.id.contains("representative") ->
                "${descLine}If you're filling this out for someone else (a child, parent, or patient you're caring for), enter your name and relationship here."
            field.id == "filling_for_self" ->
                "Select 'Myself' if you are the patient filling this out for yourself. Select 'Someone else' if you're a guardian or caregiver completing it on behalf of another person."

            // Generic with description
            desc != null -> desc

            // Last resort
            else -> "This field is asking for your $label. ${if (field.options.isNotEmpty()) "You can choose from: ${field.options.joinToString(", ")}." else "Type your answer in the box below and tap OK."}"
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
