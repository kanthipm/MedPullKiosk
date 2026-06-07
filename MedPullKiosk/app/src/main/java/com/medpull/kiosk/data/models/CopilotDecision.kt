package com.medpull.kiosk.data.models

/**
 * Structured decision returned by the local Ollama co-pilot for a SINGLE intake
 * field. The model is constrained (Ollama `format`) to emit JSON matching
 * [COPILOT_DECISION_SCHEMA]; [com.medpull.kiosk.data.engine.IntakeConversationEngine]
 * parses that JSON into a [CopilotDecision].
 *
 * The app — not the model — stays the authority on what has been answered. This is
 * a per-call opinion about ONE answer, never a source of truth for the session.
 */
enum class Assessment {
    ANSWERED, INVALID, CONFUSED, NEEDS_HELP, OFF_TOPIC, UNKNOWN;

    companion object {
        fun from(raw: String?): Assessment = when (raw?.trim()?.lowercase()) {
            "answered" -> ANSWERED
            "invalid" -> INVALID
            "confused" -> CONFUSED
            "needs_help" -> NEEDS_HELP
            "off_topic" -> OFF_TOPIC
            else -> UNKNOWN
        }
    }
}

enum class CopilotAction {
    ACCEPT, INTERRUPT;

    companion object {
        /** Default to INTERRUPT on anything unrecognized — fail safe toward asking, not silently accepting. */
        fun from(raw: String?): CopilotAction =
            if (raw?.trim()?.lowercase() == "accept") ACCEPT else INTERRUPT
    }
}

enum class InterventionType {
    CLARIFY, REPHRASE, ASSIST, BRANCH;

    companion object {
        fun from(raw: String?): InterventionType = when (raw?.trim()?.lowercase()) {
            "rephrase" -> REPHRASE
            "assist" -> ASSIST
            "branch" -> BRANCH
            else -> CLARIFY
        }
    }
}

/** A patient-facing intervention when the co-pilot steps in. */
data class Intervention(
    val type: InterventionType,
    val message: String,
    val targetQuestionId: String? = null
)

data class CopilotDecision(
    val assessment: Assessment,
    val confidence: Float,
    /** Normalized value for the field; null/blank = no usable value extracted. */
    val normalizedAnswer: String?,
    /** Other fields the answer also fills — GATED + OFF by default at the app layer. */
    val alsoFills: List<FieldUpdate> = emptyList(),
    val action: CopilotAction,
    val intervention: Intervention? = null
)

/**
 * Outcome of an `assessAnswer()` call. Distinguishes a real decision from the two
 * failure modes the ViewModel must handle DIFFERENTLY (plan §3):
 *
 *  - [Decided]          → use the decision.
 *  - [Unreachable]      → no AI at all (connection error / host down). Deterministic
 *                         accept-and-advance is acceptable — there is genuinely no model.
 *  - [ModelUnavailable] → model reachable but slow/bad (read timeout, HTTP error, or
 *                         unparseable JSON). Do NOT silently accept-and-advance: re-ask,
 *                         and escalate to staff after repeated failures.
 */
sealed class CopilotOutcome {
    abstract val latencyMs: Long

    data class Decided(val decision: CopilotDecision, override val latencyMs: Long) : CopilotOutcome()
    data class Unreachable(val reason: String, override val latencyMs: Long) : CopilotOutcome()
    data class ModelUnavailable(val reason: String, override val latencyMs: Long) : CopilotOutcome()
}

/**
 * JSON Schema handed to Ollama's `format` so the model is grammar-constrained to
 * emit a decision in this exact shape. Parsed into a JsonElement before sending
 * (Ollama expects `format` to be a JSON object, not a stringified schema).
 */
const val COPILOT_DECISION_SCHEMA = """
{
  "type": "object",
  "properties": {
    "assessment": {"type": "string", "enum": ["answered", "invalid", "confused", "needs_help", "off_topic"]},
    "confidence": {"type": "number"},
    "normalized_answer": {"type": "string"},
    "also_fills": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {"field_id": {"type": "string"}, "value": {"type": "string"}},
        "required": ["field_id", "value"]
      }
    },
    "action": {"type": "string", "enum": ["accept", "interrupt"]},
    "intervention": {
      "type": "object",
      "properties": {
        "type": {"type": "string", "enum": ["clarify", "rephrase", "assist", "branch"]},
        "message": {"type": "string"},
        "target_question_id": {"type": "string"}
      },
      "required": ["type", "message"]
    }
  },
  "required": ["assessment", "confidence", "action", "normalized_answer"]
}
"""
