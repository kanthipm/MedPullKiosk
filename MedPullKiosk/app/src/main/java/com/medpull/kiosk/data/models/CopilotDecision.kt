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
    val intervention: Intervention? = null,
    /** Model couldn't answer the patient's question from its own knowledge — the app
     *  should run a web lookup for [searchQuery] and answer from the results. */
    val needsSearch: Boolean = false,
    /** Short, PHI-free web query the app should run when [needsSearch] is true. */
    val searchQuery: String? = null
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

/*
 * Decision JSON contract (the model is asked for these EXACT keys in the system
 * prompt — the target Ollama box does not enforce JSON-schema `format`, only
 * `format:"json"`, so the prompt is the contract and the parser is defensive):
 *
 *   assessment        : "answered" | "invalid" | "confused" | "needs_help" | "off_topic"
 *   confidence        : number 0.0-1.0
 *   normalized_answer : string ("" if none)
 *   also_fills        : [ { field_id, value } ]   (usually empty; gated OFF in app)
 *   action            : "accept" | "interrupt"
 *   intervention      : { type: clarify|rephrase|assist|branch, message, target_question_id } | null
 *   needs_search      : boolean (true only when the model can't answer a factual
 *                       question from its own knowledge and wants a web lookup)
 *   search_query      : string ("" unless needs_search; must contain no patient details)
 */
