package com.medpull.kiosk.ui.screens.intake

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medpull.kiosk.data.models.DocumentType
import com.medpull.kiosk.data.models.FormField
import com.medpull.kiosk.data.models.FormStatus
import com.medpull.kiosk.data.models.UploadStatus
import com.medpull.kiosk.data.repository.AuthRepository
import com.medpull.kiosk.data.repository.DocumentRepository
import com.medpull.kiosk.data.repository.FormRepository
import com.medpull.kiosk.data.repository.GuidedIntakeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Key sliding-fee field IDs shown on the summary screen, in display order. */
val ELIGIBILITY_FIELD_ORDER = listOf(
    "full_name",
    "date_of_birth",
    "address_street",
    "address_city",
    "address_state",
    "address_zip",
    "household_size",
    "number_of_dependents",
    "income_sources",
    "monthly_income",
    "employment_status",
    "insurance_status"
)

data class EligibilitySummaryState(
    val isLoading: Boolean = true,
    val formId: String = "",
    val fields: List<FormField> = emptyList(),
    val skippedFieldIds: Set<String> = emptySet(),
    val documents: List<DocumentSlot> = emptyList(),
    val isSubmitted: Boolean = false,
    val error: String? = null
) {
    val missingRequiredFields: List<FormField>
        get() = fields.filter { f ->
            f.required && f.id !in skippedFieldIds && f.value.isNullOrBlank()
        }

    val missingRequiredDocs: List<DocumentType>
        get() = documents
            .filter { it.type.required && it.status == UploadStatus.MISSING }
            .map { it.type }

    val isReadyToSubmit: Boolean
        get() = missingRequiredFields.isEmpty() && missingRequiredDocs.isEmpty()
}

@HiltViewModel
class EligibilitySummaryViewModel @Inject constructor(
    private val formRepository: FormRepository,
    private val intakeRepository: GuidedIntakeRepository,
    private val documentRepository: DocumentRepository,
    private val authRepository: AuthRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val formId: String = savedStateHandle.get<String>("formId") ?: ""

    private val _state = MutableStateFlow(EligibilitySummaryState(formId = formId))
    val state: StateFlow<EligibilitySummaryState> = _state.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            try {
                combine(
                    formRepository.getFormByIdFlow(formId),
                    intakeRepository.watchFlow(formId)
                ) { form, flow -> Pair(form, flow) }
                    .collect { (form, flow) ->
                        if (form == null) {
                            _state.update { it.copy(isLoading = false, error = "Form not found") }
                            return@collect
                        }
                        val skipped = flow?.skippedFieldIds ?: emptySet()
                        // Keep only eligibility fields, in declared display order
                        val ordered = ELIGIBILITY_FIELD_ORDER
                            .mapNotNull { id -> form.fields.find { it.id == id } }
                        _state.update {
                            it.copy(
                                isLoading = false,
                                fields = ordered,
                                skippedFieldIds = skipped
                            )
                        }
                    }
            } catch (e: Exception) {
                Log.e("EligibilitySummaryVM", "Error loading data", e)
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }

        viewModelScope.launch {
            documentRepository.getDocumentsFlow(formId).collect { entities ->
                val slots = DocumentType.values().map { type ->
                    val entity = entities.find { it.documentType == type.name }
                    DocumentSlot(
                        type = type,
                        status = entity?.uploadStatus() ?: UploadStatus.MISSING,
                        filePath = entity?.filePath
                    )
                }
                _state.update { it.copy(documents = slots) }
            }
        }
    }

    fun submit() {
        viewModelScope.launch {
            try {
                formRepository.updateFormStatus(formId, FormStatus.COMPLETED)
                val userId = authRepository.getCurrentUserId()
                if (!userId.isNullOrBlank()) {
                    formRepository.savePatientCache(userId, _state.value.fields)
                }
                _state.update { it.copy(isSubmitted = true) }
            } catch (e: Exception) {
                Log.e("EligibilitySummaryVM", "Submit error", e)
                _state.update { it.copy(error = "Failed to submit: ${e.message}") }
            }
        }
    }
}
