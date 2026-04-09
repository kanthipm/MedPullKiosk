package com.medpull.kiosk.data.models

/**
 * Guided intake flow state — tracks progress through conversational form filling
 */
data class FormIntakeFlow(
    val id: String,
    val formId: String,
    val currentQuestionIndex: Int = 0,
    val answeredFieldIds: Set<String> = emptySet(),
    val confirmedFieldIds: Set<String> = emptySet(),  // User-approved inferred values
    val inferredFieldIds: Set<String> = emptySet(),   // AI extracted from responses
    val skippedFieldIds: Set<String> = emptySet(),    // Branch logic skipped these
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Branching rule — define conditional logic for field visibility
 *
 * Example: if insurance_status == "none", show income_level and household_size
 */
data class BranchingRule(
    val id: String,
    val formId: String,
    val sourceFieldId: String,           // e.g., "insurance_status"
    val triggerValue: String,            // e.g., "uninsured", "no"
    val actionType: BranchingActionType, // SKIP_FIELDS or SHOW_FIELDS
    val targetFieldIds: List<String>,    // Fields to skip/show
    val priority: Int = 0                // Order of evaluation (lower = first)
)

enum class BranchingActionType {
    SKIP_FIELDS,
    SHOW_FIELDS
}

/**
 * A single intake question derived from a form field
 */
data class IntakeQuestion(
    val fieldId: String,
    val question: String,                // "Do you have health insurance?" (AI-generated)
    val followUpPrompt: String? = null,  // "Ask for insurance company name if they do."
    val conversationalHints: String? = null, // Chatbot context/hints
    val expectedFieldType: FieldType
)

/**
 * Result of AI extracting a value from patient speech/text
 */
data class ExtractedValue(
    val fieldId: String,
    val value: String,
    val confidence: Float = 0.8f,
    val extractedFrom: String,  // The user response text
    val isConfirmed: Boolean = false
)
