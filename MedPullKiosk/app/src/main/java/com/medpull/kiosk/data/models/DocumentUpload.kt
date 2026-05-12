package com.medpull.kiosk.data.models

enum class DocumentType(
    val displayName: String,
    val reason: String,
    val examples: List<String>,
    val required: Boolean
) {
    GOVERNMENT_ID(
        displayName = "Government ID",
        reason = "Required to verify your identity.",
        examples = listOf("Driver's license", "State ID", "Passport"),
        required = true
    ),
    PROOF_OF_INCOME(
        displayName = "Proof of Income",
        reason = "Used to calculate your sliding fee discount.",
        examples = listOf("Pay stub", "W2", "Employer letter"),
        required = true
    ),
    PROOF_OF_DEPENDENTS(
        displayName = "Proof of Dependents",
        reason = "Only needed if you listed dependents in your household.",
        examples = listOf("Birth certificate", "School record", "Insurance document"),
        required = false
    )
}

enum class UploadStatus {
    UPLOADED,
    MISSING,
    SKIPPED
}
