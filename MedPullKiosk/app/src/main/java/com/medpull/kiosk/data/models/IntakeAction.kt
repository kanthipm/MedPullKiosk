package com.medpull.kiosk.data.models

/**
 * Typed actions returned by IntakeConversationEngine after parsing the AI's JSON response.
 *
 * The AI is instructed to respond with:
 * {
 *   "action": "set_field" | "skip_section" | "flag_for_clinic" | "ask_clarification" | "transition_to_review",
 *   "question_text": "...",
 *   "field_updates": [{"field_id": "...", "value": "...", "confidence": 0.0-1.0}],
 *   "reasoning": "..."
 * }
 *
 * The engine parses that JSON and returns one of these sealed subclasses.
 * All downstream code (ViewModel) works with typed actions, not raw JSON strings.
 */
/** A single field value extracted by the AI — used in SetMultipleFields and FieldParseResult. */
data class FieldUpdate(
    val fieldId: String,
    val value: String,
    val confidence: Float
)

/**
 * Result of parsing a patient's answer for a specific field.
 * Replaces the old free-form IntakeAction — the ViewModel now drives field progression,
 * the engine only extracts values.
 */
data class FieldParseResult(
    val value: String? = null,          // Extracted value, null = couldn't parse
    val confidence: Float = 0f,
    val alsoFills: List<FieldUpdate> = emptyList(), // Bonus fields the answer also filled
    val needsClarification: Boolean = false,
    val clarificationQuestion: String = ""
)

sealed class IntakeAction {

    /**
     * The AI extracted values for multiple fields from a single patient reply.
     * All updates have confidence >= CONFIDENCE_ACCEPT.
     * The ViewModel writes all of them in one state update.
     */
    data class SetMultipleFields(
        val updates: List<FieldUpdate>,
        val questionText: String,
        val reasoning: String = ""
    ) : IntakeAction()

    /**
     * AI extracted a field value with sufficient confidence (>= 0.8).
     * The ViewModel writes this to Room DB and advances to the next question.
     */
    data class SetField(
        val fieldId: String,
        val value: String,
        val confidence: Float,
        val questionText: String,
        val reasoning: String = ""
    ) : IntakeAction()

    /**
     * AI needs the patient to confirm a value (confidence 0.5–0.79).
     * Also used when the patient's answer was unclear (confidence < 0.5).
     * The ViewModel does NOT write a field value — waits for patient confirmation.
     */
    data class AskClarification(
        val questionText: String,
        val fieldId: String? = null,
        val reasoning: String = ""
    ) : IntakeAction()

    /**
     * AI determined a set of fields don't apply based on prior answers.
     * The ViewModel marks these fields as skipped in the intake flow.
     */
    data class SkipSection(
        val fieldIds: List<String>,
        val questionText: String,
        val reasoning: String = ""
    ) : IntakeAction()

    /**
     * AI flagged a field for clinic staff review.
     * Triggered automatically when clarification count >= 2 and confidence < 0.5.
     * The ViewModel marks the field as flagged and advances to the next question.
     */
    data class FlagForClinic(
        val fieldId: String,
        val reason: String,
        val questionText: String
    ) : IntakeAction()

    /**
     * All required fields are filled. Time to show the review screen.
     */
    object TransitionToReview : IntakeAction()

    /**
     * AI returned malformed JSON after retries, or the API failed.
     * The engine fell back to a template question via generateQuestionText().
     * The ViewModel displays the question without updating any field state.
     */
    data class Fallback(
        val questionText: String,
        val fieldId: String? = null
    ) : IntakeAction()
}
