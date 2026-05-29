package com.medpull.kiosk.ui.screens.intake

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.medpull.kiosk.R
import com.medpull.kiosk.data.models.DocumentType

@Composable
fun DocumentType.localizedDisplayName(): String = when (this) {
    DocumentType.GOVERNMENT_ID -> stringResource(R.string.doc_gov_id)
    DocumentType.PROOF_OF_INCOME -> stringResource(R.string.doc_proof_income)
    DocumentType.PROOF_OF_DEPENDENTS -> stringResource(R.string.doc_proof_dependents)
}

@Composable
fun DocumentType.localizedReason(): String = when (this) {
    DocumentType.GOVERNMENT_ID -> stringResource(R.string.doc_gov_id_reason)
    DocumentType.PROOF_OF_INCOME -> stringResource(R.string.doc_proof_income_reason)
    DocumentType.PROOF_OF_DEPENDENTS -> stringResource(R.string.doc_proof_dependents_reason)
}

@Composable
fun DocumentType.localizedExamples(): List<String> = when (this) {
    DocumentType.GOVERNMENT_ID -> listOf(
        stringResource(R.string.doc_gov_id_example_license),
        stringResource(R.string.doc_gov_id_example_state_id),
        stringResource(R.string.doc_gov_id_example_passport)
    )
    DocumentType.PROOF_OF_INCOME -> listOf(
        stringResource(R.string.doc_proof_income_example_paystub),
        stringResource(R.string.doc_proof_income_example_w2),
        stringResource(R.string.doc_proof_income_example_letter)
    )
    DocumentType.PROOF_OF_DEPENDENTS -> listOf(
        stringResource(R.string.doc_proof_dependents_example_birth),
        stringResource(R.string.doc_proof_dependents_example_school),
        stringResource(R.string.doc_proof_dependents_example_insurance)
    )
}
