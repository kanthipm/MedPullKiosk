package com.medpull.kiosk.ui.screens.intake

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medpull.kiosk.data.models.FormField
import com.medpull.kiosk.data.models.FormStatus
import com.medpull.kiosk.data.repository.AuthRepository
import com.medpull.kiosk.data.repository.FormRepository
import com.medpull.kiosk.data.repository.GuidedIntakeRepository
import com.medpull.kiosk.utils.PdfUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class IntakeReviewViewModel @Inject constructor(
    private val formRepository: FormRepository,
    private val intakeRepository: GuidedIntakeRepository,
    private val authRepository: AuthRepository,
    private val pdfUtils: PdfUtils,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "IntakeReviewViewModel"
    }

    private val formId: String = savedStateHandle.get<String>("formId") ?: ""

    private val _state = MutableStateFlow(IntakeReviewState())
    val state: StateFlow<IntakeReviewState> = _state.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            try {
                _state.update { it.copy(isLoading = true) }

                combine(
                    formRepository.getFormByIdFlow(formId),
                    intakeRepository.watchFlow(formId)
                ) { form, flow ->
                    Pair(form, flow)
                }.collect { (form, flow) ->
                    if (form == null) {
                        _state.update { it.copy(isLoading = false, error = "Form not found") }
                        return@collect
                    }
                    val skipped = flow?.skippedFieldIds ?: emptySet()
                    _state.update {
                        it.copy(
                            isLoading = false,
                            formName = form.fileName,
                            fields = form.fields,
                            skippedFieldIds = skipped
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading review data", e)
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun updateField(fieldId: String, value: String) {
        viewModelScope.launch {
            try {
                formRepository.updateFieldValue(fieldId, value)
                _state.update {
                    it.copy(fields = it.fields.map { f ->
                        if (f.id == fieldId) f.copy(value = value) else f
                    })
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating field $fieldId", e)
            }
        }
    }

    fun submit() {
        viewModelScope.launch {
            try {
                formRepository.updateFormStatus(formId, FormStatus.COMPLETED)
                // Write demographic fields to patient cache for next intake prefill
                val userId = authRepository.getCurrentUserId()
                if (!userId.isNullOrBlank()) {
                    formRepository.savePatientCache(userId, _state.value.fields)
                }
                _state.update { it.copy(isSubmitted = true) }
                Log.d(TAG, "Intake review submitted")
            } catch (e: Exception) {
                Log.e(TAG, "Error submitting", e)
                _state.update { it.copy(error = "Failed to submit: ${e.message}") }
            }
        }
    }

    fun generatePdf(outputDir: File) {
        viewModelScope.launch {
            try {
                _state.update { it.copy(isGeneratingPdf = true, pdfFile = null) }
                val file = withContext(Dispatchers.IO) {
                    outputDir.mkdirs()
                    pdfUtils.createSimpleFilledPdf(
                        fields = _state.value.fields,
                        outputDir = outputDir,
                        formName = _state.value.formName
                    )
                }
                _state.update { it.copy(isGeneratingPdf = false, pdfFile = file) }
                if (file == null) Log.e(TAG, "PDF generation returned null")
            } catch (e: Exception) {
                Log.e(TAG, "Error generating PDF", e)
                _state.update { it.copy(isGeneratingPdf = false, error = "Failed to generate PDF: ${e.message}") }
            }
        }
    }

    fun clearPdfFile() {
        _state.update { it.copy(pdfFile = null) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}

data class IntakeReviewState(
    val isLoading: Boolean = true,
    val formName: String = "",
    val fields: List<FormField> = emptyList(),
    val skippedFieldIds: Set<String> = emptySet(),
    val error: String? = null,
    val isSubmitted: Boolean = false,
    val isGeneratingPdf: Boolean = false,
    val pdfFile: File? = null
)
