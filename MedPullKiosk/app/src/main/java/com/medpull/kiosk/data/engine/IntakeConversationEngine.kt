package com.medpull.kiosk.data.engine

import android.util.Log
import com.google.gson.Gson
import com.medpull.kiosk.data.local.dao.AuditLogDao
import com.medpull.kiosk.data.local.entities.AuditLogEntity
import com.medpull.kiosk.data.models.Assessment
import com.medpull.kiosk.data.models.CopilotAction
import com.medpull.kiosk.data.models.CopilotDecision
import com.medpull.kiosk.data.models.CopilotOutcome
import com.medpull.kiosk.data.models.FieldParseResult
import com.medpull.kiosk.data.models.FieldType
import com.medpull.kiosk.data.models.FieldUpdate
import com.medpull.kiosk.data.models.FormField
import com.medpull.kiosk.data.models.Intervention
import com.medpull.kiosk.data.models.InterventionType
import com.medpull.kiosk.data.remote.ai.AiResponse
import com.medpull.kiosk.data.remote.ai.GrokApiService
import com.medpull.kiosk.data.remote.ai.OllamaApiService
import com.medpull.kiosk.data.remote.ai.OllamaResult
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
    private val ollama: OllamaApiService,
    private val auditLogDao: AuditLogDao,
    private val authRepository: AuthRepository,
    private val gson: Gson,
    @ApplicationContext private val context: android.content.Context
) {

    companion object {
        private const val TAG = "IntakeEngine"

        /** Shared value-normalization guidance appended to the co-pilot user prompt. */
        private val COPILOT_NORMALIZATION_RULES = """
            Normalize the extracted value to a standard format:
            - Date of birth / any date → MM/DD/YYYY (e.g. "01/15/1990")
            - Phone number → (XXX) XXX-XXXX
            - US State → 2-letter abbreviation (e.g. "TX", "CA")
            - ZIP code → 5 digits only
            - Full name / any name → Title Case
            - Dollar amount / income → digits only, no $ or commas (e.g. "2400")
            - Household size / number of dependents → digits only (e.g. "3")
            - Yes/No fields → "Yes" or "No" exactly
        """.trimIndent()

        val STATE_ABBREVIATIONS = mapOf(
            "ALABAMA" to "AL", "ALASKA" to "AK", "ARIZONA" to "AZ", "ARKANSAS" to "AR",
            "CALIFORNIA" to "CA", "COLORADO" to "CO", "CONNECTICUT" to "CT", "DELAWARE" to "DE",
            "FLORIDA" to "FL", "GEORGIA" to "GA", "HAWAII" to "HI", "IDAHO" to "ID",
            "ILLINOIS" to "IL", "INDIANA" to "IN", "IOWA" to "IA", "KANSAS" to "KS",
            "KENTUCKY" to "KY", "LOUISIANA" to "LA", "MAINE" to "ME", "MARYLAND" to "MD",
            "MASSACHUSETTS" to "MA", "MICHIGAN" to "MI", "MINNESOTA" to "MN",
            "MISSISSIPPI" to "MS", "MISSOURI" to "MO", "MONTANA" to "MT",
            "NEBRASKA" to "NE", "NEVADA" to "NV", "NEW HAMPSHIRE" to "NH",
            "NEW JERSEY" to "NJ", "NEW MEXICO" to "NM", "NEW YORK" to "NY",
            "NORTH CAROLINA" to "NC", "NORTH DAKOTA" to "ND", "OHIO" to "OH",
            "OKLAHOMA" to "OK", "OREGON" to "OR", "PENNSYLVANIA" to "PA",
            "RHODE ISLAND" to "RI", "SOUTH CAROLINA" to "SC", "SOUTH DAKOTA" to "SD",
            "TENNESSEE" to "TN", "TEXAS" to "TX", "UTAH" to "UT", "VERMONT" to "VT",
            "VIRGINIA" to "VA", "WASHINGTON" to "WA", "WEST VIRGINIA" to "WV",
            "WISCONSIN" to "WI", "WYOMING" to "WY", "DISTRICT OF COLUMBIA" to "DC"
        )
    }

    // ─── Question Generation ──────────────────────────────────────────────────

    /**
     * Return a single warm question for the given field.
     *
     * Questions are pre-translated and baked into the schema (see
     * scripts/translate_forms.py), so this is fully deterministic — no LLM call,
     * no network, no xAI credits. The patient-facing text is already in the
     * active language. Guardian mode and schema-less (uploaded) forms fall back
     * to a template built from the field label.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun generateQuestion(
        field: FormField,
        filledFields: List<FormField>,
        language: String,
        guardianMode: Boolean
    ): String {
        logAudit("AI_QUESTION_GEN", "Field: ${field.id}")

        // Guardian mode reframes to third person, which the baked first-person
        // question can't express — derive from the (localized) label instead.
        if (!guardianMode) {
            val baked = field.translatedQuestion?.takeIf { it.isNotBlank() }
                ?: field.question?.takeIf { it.isNotBlank() }
            if (baked != null) return baked
        }
        return fallbackQuestion(field, guardianMode)
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
        allFields: List<FormField>
    ): FieldParseResult {
        // Fast path: exact option match for radio/dropdown — no LLM needed.
        // Match against canonical English options AND the localized display
        // labels, always returning the canonical English value (skip rules,
        // consent, and export all key off canonical values).
        if (field.fieldType in listOf(FieldType.RADIO, FieldType.DROPDOWN)) {
            val answer = userAnswer.trim()
            val canonical = field.options.find { it.equals(answer, ignoreCase = true) }
            if (canonical != null) return FieldParseResult(value = canonical, confidence = 1.0f)
            val localizedIdx = field.optionLabels.indexOfFirst { it.equals(answer, ignoreCase = true) }
            if (localizedIdx in field.options.indices) {
                return FieldParseResult(value = field.options[localizedIdx], confidence = 1.0f)
            }
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

        val isStreetField = field.id.endsWith("_street") ||
            field.fieldName.lowercase().let { it.contains("street") || it.contains("address line") }

        val prompt = buildString {
            appendLine("Extract the value for this intake field from the patient's answer.")
            appendLine()
            appendLine("Target field: ${field.id} (${field.fieldType.name.lowercase()})")
            appendLine("Label: ${field.translatedText ?: field.fieldName}")
            if (field.options.isNotEmpty()) appendLine("Valid options: ${field.options.joinToString(", ")}")
            if (!field.description.isNullOrBlank()) appendLine("Format hint: ${field.description}")
            if (isStreetField) {
                appendLine()
                appendLine("IMPORTANT: If the patient typed a full address (e.g. '123 Main St, Houston, TX 77001'), extract ONLY the street number and name as the value for this field, and put city, state, and ZIP into also_fills using the exact field IDs listed below.")
            }
            appendLine()
            appendLine("Patient said: \"$userAnswer\"")
            if (bonusCandidates.isNotBlank()) {
                appendLine()
                appendLine("If the answer also clearly fills these other unfilled fields, include them in also_fills:")
                appendLine(bonusCandidates)
            }
            appendLine()
            appendLine()
            appendLine("""Normalize the extracted value to a standard format:
- Date of birth / any date → MM/DD/YYYY (e.g. "01/15/1990")
- Phone number → (XXX) XXX-XXXX
- US State → 2-letter abbreviation (e.g. "TX", "CA")
- ZIP code → 5 digits only
- Full name / any name → Title Case
- Dollar amount / income → digits only, no $ or commas (e.g. "2400")
- Household size / number of dependents → digits only (e.g. "3")
- Yes/No fields → "Yes" or "No" exactly""")
            appendLine()
            appendLine("""Return JSON only:
{
  "value": "normalized extracted value, or null if unclear",
  "confidence": 0.0-1.0,
  "also_fills": [{"field_id": "...", "value": "normalized value"}],
  "needs_clarification": false,
  "clarification_question": "follow-up question if value is null"
}""")
        }

        logAudit("AI_PARSE_ANSWER", "Field: ${field.id}")

        return when (val resp = apiService.sendMessage(
            userMessage = prompt,
            conversationHistory = emptyList(),
            systemPrompt = "You extract and normalize field values from patient answers. Return valid JSON only.",
            model = Constants.AI.CONVERSATION_MODEL,
            maxTokens = 400,
            preferGroq = true
        )) {
            is AiResponse.Success -> parseFieldResult(resp.message).normalizeAll(field, allFields)
            is AiResponse.Error -> {
                Log.w(TAG, "Parse answer offline fallback for ${field.id}: ${resp.message}")
                offlineParse(field, userAnswer, allFields).normalizeAll(field, allFields)
            }
        }
    }

    /** Apply deterministic normalization to primary value and all alsoFills. */
    private fun FieldParseResult.normalizeAll(field: FormField, allFields: List<FormField>): FieldParseResult {
        val normalizedValue = value?.let { normalizeValue(field, it) }
        val normalizedFills = alsoFills.map { update ->
            val relatedField = allFields.find { it.id == update.fieldId }
            if (relatedField != null)
                update.copy(value = normalizeValue(relatedField, update.value))
            else
                update
        }
        return copy(value = normalizedValue, alsoFills = normalizedFills)
    }

    /**
     * Normalize a field's raw parsed value to a standard display format.
     * Applied to both AI-parsed and offline-parsed values so the final form
     * is always consistent regardless of how the patient typed their answer.
     */
    private fun normalizeValue(field: FormField, raw: String): String {
        val t = raw.trim()
        if (t.isBlank()) return t
        return when {
            field.fieldType == FieldType.DATE -> normalizeDate(t) ?: t
            field.id.contains("phone") -> normalizePhone(t) ?: t
            field.id.endsWith("_zip") || field.id == "zip" || field.id.contains("zip_code") ->
                normalizeZip(t) ?: t
            field.id.endsWith("_state") || field.id == "state" ->
                normalizeState(t) ?: t
            field.id.contains("income") || field.id.contains("monthly_income") ->
                normalizeMoney(t) ?: t
            field.fieldType == FieldType.NUMBER ->
                t.filter { it.isDigit() || it == '.' }.trimStart('0').ifBlank { "0" }
            field.id.contains("name") && field.fieldType == FieldType.TEXT ->
                toTitleCase(t)
            field.id.endsWith("_city") || field.id == "city" ->
                toTitleCase(t)
            field.id.endsWith("_street") || field.id == "street" ->
                toTitleCase(t)
            else -> t
        }
    }

    private fun normalizeDate(raw: String): String? {
        val output = java.text.SimpleDateFormat("MM/dd/yyyy", java.util.Locale.US)
        // Remove ordinal suffixes: "15th" → "15"
        val cleaned = raw.replace(Regex("""(\d+)(st|nd|rd|th)"""), "$1")
            .replace(",", " ").replace(Regex(" {2,}"), " ").trim()
        val patterns = listOf(
            "MM/dd/yyyy", "M/d/yyyy", "MM-dd-yyyy", "M-d-yyyy",
            "MM/dd/yy", "M/d/yy",
            "yyyy-MM-dd",
            "MMMM d yyyy", "MMM d yyyy",
            "d MMMM yyyy", "d MMM yyyy",
            "MMMM dd yyyy", "MMM dd yyyy"
        )
        for (fmt in patterns) {
            try {
                val df = java.text.SimpleDateFormat(fmt, java.util.Locale.US)
                df.isLenient = false
                val date = df.parse(cleaned) ?: continue
                // Sanity-check: year must be reasonable
                val cal = java.util.Calendar.getInstance().also { it.time = date }
                val year = cal.get(java.util.Calendar.YEAR)
                if (year < 1900 || year > 2100) continue
                return output.format(date)
            } catch (_: Exception) {}
        }
        return null
    }

    private fun normalizePhone(raw: String): String? {
        val digits = raw.filter { it.isDigit() }
        return when {
            digits.length == 10 ->
                "(${digits.substring(0,3)}) ${digits.substring(3,6)}-${digits.substring(6)}"
            digits.length == 11 && digits[0] == '1' ->
                "(${digits.substring(1,4)}) ${digits.substring(4,7)}-${digits.substring(7)}"
            else -> null
        }
    }

    private fun normalizeZip(raw: String): String? {
        val digits = raw.filter { it.isDigit() }
        return if (digits.length >= 5) digits.take(5) else null
    }

    private fun normalizeState(raw: String): String? {
        val upper = raw.trim().uppercase()
        if (upper.length == 2 && upper.all { it.isLetter() }) return upper
        return STATE_ABBREVIATIONS[upper]
    }

    private fun normalizeMoney(raw: String): String? {
        val digits = raw.replace(Regex("[^0-9.]"), "")
        val amount = digits.toDoubleOrNull() ?: return null
        return amount.toLong().toString()
    }

    private fun toTitleCase(s: String): String = s.split(" ").joinToString(" ") { word ->
        if (word.length <= 1) word.uppercase()
        else word[0].uppercase() + word.substring(1).lowercase()
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

        // Derive prefix parts from the street field ID (drop "street" suffix).
        // e.g. "mailing_address_street" → ["mailing","address"]
        //      "address_street"         → ["address"]
        //      "physical_address_street"→ ["physical","address"]
        val idParts = streetField.id.split("_").dropLast(1)

        // Find a related blank address field by trying progressively shorter common prefixes.
        // This handles all naming conventions without hardcoding.
        fun findRelated(keyword: String): FormField? {
            for (len in idParts.size downTo 0) {
                val prefix = if (len == 0) "" else idParts.take(len).joinToString("_") + "_"
                val match = allFields.find { f ->
                    f.value.isNullOrBlank() &&
                    (if (prefix.isEmpty()) f.id == keyword else f.id.startsWith(prefix) && f.id.endsWith(keyword))
                }
                if (match != null) return match
            }
            return null
        }

        val cityField = findRelated("city")
        val stateField = findRelated("state")
        val zipField = findRelated("zip")
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
        if (city != null && cityField != null) alsoFills += FieldUpdate(cityField.id, city, 0.9f)
        if (state != null && stateField != null) alsoFills += FieldUpdate(stateField.id, state, 0.9f)
        if (zip != null && zipField != null) alsoFills += FieldUpdate(zipField.id, zip, 0.9f)
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

    // ─── AI Co-Pilot Decision (local Ollama) ─────────────────────────────────

    /**
     * Assess a patient's answer for [field] with the local Ollama co-pilot and return
     * a structured [CopilotOutcome]. This is the richer evolution of [parseAnswer]:
     * one call returns assess + normalize + accept/interrupt + intervention.
     *
     * Returns a TYPED outcome so the ViewModel can treat the two failure modes
     * differently (plan §3): [CopilotOutcome.Unreachable] (no AI → deterministic
     * fallback OK) vs [CopilotOutcome.ModelUnavailable] (model up but slow/bad →
     * re-ask, never silently advance). The app stays the authority on answers; this
     * is only a per-call opinion.
     */
    suspend fun assessAnswer(
        field: FormField,
        userAnswer: String,
        allFields: List<FormField>,
        language: String
    ): CopilotOutcome {
        val started = System.currentTimeMillis()

        val priorAnswers = allFields
            .filter { !it.value.isNullOrBlank() && it.id != field.id &&
                it.fieldType != FieldType.STATIC_LABEL && it.value != "delivered" }
            .take(12)
            .joinToString("\n") { "  ${it.id} (${it.translatedText ?: it.fieldName}): ${it.value}" }

        val bonusCandidates = allFields
            .filter { f ->
                f.id != field.id && f.value.isNullOrBlank() &&
                f.fieldType !in listOf(FieldType.STATIC_LABEL, FieldType.SIGNATURE, FieldType.MULTI_SELECT)
            }
            .take(8)
            .joinToString("\n") { "  ${it.id}: ${it.translatedText ?: it.fieldName}" }

        val systemPrompt = buildCopilotSystemPrompt(language)
        val userPrompt = buildString {
            appendLine("Current field id: ${field.id} (${field.fieldType.name.lowercase()})")
            appendLine("Question: ${field.translatedQuestion ?: field.question ?: field.translatedText ?: field.fieldName}")
            if (field.options.isNotEmpty()) appendLine("Valid options: ${field.options.joinToString(", ")}")
            if (!field.description.isNullOrBlank()) appendLine("Field note: ${field.description}")
            appendLine("Required: ${field.required}")
            appendLine()
            appendLine("Patient said: \"$userAnswer\"")
            if (priorAnswers.isNotBlank()) {
                appendLine(); appendLine("Prior answers (context — do not re-ask these):"); appendLine(priorAnswers)
            }
            if (bonusCandidates.isNotBlank()) {
                appendLine()
                appendLine("Other empty fields the answer MIGHT also fill (only if the patient explicitly gave them):")
                appendLine(bonusCandidates)
            }
            appendLine()
            append(COPILOT_NORMALIZATION_RULES)
        }

        logAudit("AI_COPILOT_ASSESS", "Field: ${field.id}")

        return when (val resp = ollama.chat(systemPrompt, userPrompt)) {
            is OllamaResult.Success -> {
                val decision = parseCopilotDecision(resp.content, field, allFields)
                val latency = System.currentTimeMillis() - started
                if (decision != null) CopilotOutcome.Decided(decision, latency)
                else CopilotOutcome.ModelUnavailable("unparseable: ${resp.content.take(160)}", latency)
            }
            is OllamaResult.Unreachable ->
                CopilotOutcome.Unreachable(resp.message, System.currentTimeMillis() - started)
            is OllamaResult.Timeout ->
                CopilotOutcome.ModelUnavailable("timeout: ${resp.message}", System.currentTimeMillis() - started)
            is OllamaResult.HttpError ->
                CopilotOutcome.ModelUnavailable("http ${resp.code}: ${resp.message}", System.currentTimeMillis() - started)
        }
    }

    private fun buildCopilotSystemPrompt(language: String): String {
        val lang = languageName(language)
        // NOTE: the target Ollama box does NOT enforce JSON-schema `format` (only
        // `format:"json"`), so THIS PROMPT is the contract — it must spell out the
        // exact keys. Verified end-to-end that the model returns this shape.
        return """
            You are the MedPull Assistant, helping a patient complete a medical intake FORM on a kiosk. You ONLY help complete the form. You MUST NOT give medical advice, diagnoses, treatment, or clinical opinions of any kind. Ground every message strictly in the form's own field label and options — never invent medical information.

            Decide how to handle the patient's answer for the current field:
            - action="accept" with a normalized value when the answer clearly fits the field (assessment="answered").
            - action="interrupt" when the patient is confused, asked a question, gave something that doesn't fit, or went off-topic. Provide an intervention: type "clarify"/"rephrase" to restate the SAME question simply; "assist" to explain what the field asks for (still no medical advice); "branch" ONLY when a DIFFERENT existing field should be asked next (set target_question_id and write message AS the next question).

            Return ONLY a JSON object with EXACTLY these keys:
            - "assessment": one of "answered","invalid","confused","needs_help","off_topic"
            - "confidence": number 0.0-1.0 (your certainty)
            - "normalized_answer": the cleaned value as a string ("" if none)
            - "also_fills": array of {"field_id","value"} for other fields the answer explicitly fills (usually empty [])
            - "action": "accept" or "interrupt"
            - "intervention": when action is "interrupt", an object {"type","message","target_question_id"} with message plain, kind, at most 2-3 sentences and written in $lang; otherwise null

            Even inside an intervention, never give medical guidance — for medical questions, gently say staff can help. Output JSON only, no prose.
        """.trimIndent()
    }

    private fun parseCopilotDecision(
        raw: String,
        field: FormField,
        allFields: List<FormField>
    ): CopilotDecision? {
        return try {
            val cleaned = raw.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()
            if (cleaned.isBlank()) return null
            val obj = JSONObject(cleaned)

            val assessment = Assessment.from(obj.optString("assessment"))
            val confidence = obj.optDouble("confidence", 0.0).toFloat()
            // Primary key is normalized_answer; tolerate a stray "value" key if the
            // model drifts (observed when schema enforcement is off).
            val rawValue = obj.optString("normalized_answer", "")
                .ifBlank { obj.optString("value", "") }
            val normalized = rawValue
                .takeIf { it.isNotBlank() && it != "null" }
                ?.let { normalizeValue(field, it) }
            val action = CopilotAction.from(obj.optString("action"))

            val alsoFills = mutableListOf<FieldUpdate>()
            obj.optJSONArray("also_fills")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val e = arr.optJSONObject(i) ?: continue
                    val fId = e.optString("field_id", "")
                    val fVal = e.optString("value", "")
                    if (fId.isNotBlank() && fVal.isNotBlank() && fVal != "null") {
                        val related = allFields.find { it.id == fId }
                        val value = if (related != null) normalizeValue(related, fVal) else fVal
                        alsoFills += FieldUpdate(fId, value, confidence)
                    }
                }
            }

            // intervention is normally an object; tolerate a bare string (model drift)
            // by treating it as a clarify message.
            val interventionObj = obj.optJSONObject("intervention")
            val intervention = when {
                interventionObj != null -> {
                    val msg = interventionObj.optString("message", "")
                    if (msg.isBlank()) null
                    else Intervention(
                        type = InterventionType.from(interventionObj.optString("type")),
                        message = msg,
                        targetQuestionId = interventionObj.optString("target_question_id", "").ifBlank { null }
                    )
                }
                else -> obj.optString("intervention", "")
                    .takeIf { it.isNotBlank() && it != "null" }
                    ?.let { Intervention(InterventionType.CLARIFY, it, null) }
            }

            CopilotDecision(assessment, confidence, normalized, alsoFills, action, intervention)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse CopilotDecision: ${e.message}")
            null
        }
    }

    /**
     * Soft, secondary guardrail: catch intervention text that reads like medical
     * advice so the ViewModel can downgrade it to a safe re-ask. The SYSTEM PROMPT
     * is the primary defense — this keyword screen is a backstop only (see
     * KNOWN_ISSUES.md), and is intentionally conservative to avoid false positives
     * on benign form help.
     */
    fun looksLikeMedicalAdvice(text: String): Boolean {
        val t = text.lowercase()
        val redFlags = listOf(
            "you should take", "i recommend taking", "you may have", "you might have",
            "you probably have", "diagnos", "prescri", "dosage", "you should stop taking",
            "increase your dose", "decrease your dose", "this medication will", "treat your"
        )
        return redFlags.any { it in t }
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
            maxTokens = 200,
            preferGroq = true
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
            is AiResponse.Error -> buildOfflineClarification(field)
        }
    }

    /**
     * Offline clarification: builds a useful, contextual answer from field metadata
     * so patients aren't left with a generic "please fill it in" message when the
     * AI API is unavailable.
     */
    private fun buildOfflineClarification(field: FormField): String {
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
        "ja" -> "Japanese"; "pt" -> "Portuguese"
        "ar" -> "Arabic"; "ru" -> "Russian"; else -> "English"
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
