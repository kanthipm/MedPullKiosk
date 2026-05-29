package com.medpull.kiosk.ui.screens.export

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medpull.kiosk.data.models.FormStatus
import com.medpull.kiosk.R
import com.medpull.kiosk.data.repository.FormRepository
import com.medpull.kiosk.data.repository.StorageRepository
import com.medpull.kiosk.healthcare.repository.FhirRepository
import com.medpull.kiosk.utils.AppStrings
import com.medpull.kiosk.utils.PdfUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * ViewModel for export screen
 */
@HiltViewModel
class ExportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val formRepository: FormRepository,
    private val storageRepository: StorageRepository,
    private val fhirRepository: FhirRepository,
    private val pdfUtils: PdfUtils,
    private val appStrings: AppStrings,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "ExportViewModel"
    }

    private val formId: String = savedStateHandle.get<String>("formId") ?: ""

    private val _state = MutableStateFlow(ExportState())
    val state: StateFlow<ExportState> = _state.asStateFlow()

    init {
        loadForm()
    }

    /**
     * Load form details
     */
    private fun loadForm() {
        viewModelScope.launch {
            try {
                formRepository.getFormByIdFlow(formId)
                    .collect { form ->
                        if (form != null) {
                            val completionPercentage = formRepository.getFormCompletionPercentage(formId)
                            _state.update {
                                it.copy(
                                    form = form,
                                    completionPercentage = completionPercentage,
                                    isLoading = false,
                                    canExport = completionPercentage >= 100f
                                )
                            }
                        } else {
                            _state.update {
                                it.copy(
                                    error = appStrings.get(R.string.form_not_found),
                                    isLoading = false
                                )
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading form", e)
                _state.update {
                    it.copy(
                        error = appStrings.get(R.string.err_failed_load_form, e.message ?: ""),
                        isLoading = false
                    )
                }
            }
        }
    }

    /**
     * Export to S3
     */
    fun exportToS3() {
        viewModelScope.launch {
            try {
                _state.update { it.copy(isExporting = true, error = null) }

                val form = _state.value.form
                if (form == null) {
                    _state.update { it.copy(error = appStrings.get(R.string.err_no_form_export), isExporting = false) }
                    return@launch
                }

                // Generate filled PDF
                val filledPdfResult = pdfUtils.generateFilledPdf(
                    originalPdfPath = form.originalFileUri,
                    fields = form.fields,
                    outputDir = context.cacheDir
                )

                if (filledPdfResult == null) {
                    _state.update {
                        it.copy(
                            error = appStrings.get(R.string.err_failed_generate_pdf_short),
                            isExporting = false
                        )
                    }
                    return@launch
                }

                val filledPdf = filledPdfResult // Non-null at this point

                // Upload to S3
                val result = storageRepository.uploadFilledForm(filledPdf, form.userId, formId)

                if (result.isSuccess) {
                    // Update form status
                    formRepository.updateFormStatus(formId, FormStatus.EXPORTED)

                    _state.update {
                        it.copy(
                            isExporting = false,
                            exportSuccess = true,
                            exportMessage = appStrings.get(R.string.msg_exported_cloud)
                        )
                    }

                    Log.d(TAG, "Form exported to S3 successfully")
                } else {
                    _state.update {
                        it.copy(
                            error = appStrings.get(R.string.err_upload_cloud_failed, result.exceptionOrNull()?.message ?: ""),
                            isExporting = false
                        )
                    }
                }

                // Cleanup temp file
                filledPdf.delete()

            } catch (e: Exception) {
                Log.e(TAG, "Error exporting to S3", e)
                _state.update {
                    it.copy(
                        error = appStrings.get(R.string.err_export_failed, e.message ?: ""),
                        isExporting = false
                    )
                }
            }
        }
    }

    /**
     * Export to local storage
     */
    fun exportToLocal() {
        viewModelScope.launch {
            try {
                _state.update { it.copy(isExporting = true, error = null) }

                val form = _state.value.form
                if (form == null) {
                    _state.update { it.copy(error = appStrings.get(R.string.err_no_form_export), isExporting = false) }
                    return@launch
                }

                // Generate filled PDF
                val outputDir = context.getExternalFilesDir(null) ?: context.filesDir
                val filledPdfResult = pdfUtils.generateFilledPdf(
                    originalPdfPath = form.originalFileUri,
                    fields = form.fields,
                    outputDir = outputDir
                )

                if (filledPdfResult == null) {
                    _state.update {
                        it.copy(
                            error = appStrings.get(R.string.err_failed_generate_pdf_short),
                            isExporting = false
                        )
                    }
                    return@launch
                }

                val filledPdf = filledPdfResult // Non-null at this point

                // Update form status
                formRepository.updateFormStatus(formId, FormStatus.EXPORTED)

                _state.update {
                    it.copy(
                        isExporting = false,
                        exportSuccess = true,
                        exportMessage = appStrings.get(R.string.msg_form_saved_to, filledPdf.absolutePath),
                        localFilePath = filledPdf.absolutePath
                    )
                }

                Log.d(TAG, "Form exported locally: ${filledPdf.absolutePath}")

            } catch (e: Exception) {
                Log.e(TAG, "Error exporting locally", e)
                _state.update {
                    it.copy(
                        error = appStrings.get(R.string.err_export_failed, e.message ?: ""),
                        isExporting = false
                    )
                }
            }
        }
    }

    /**
     * Preview export
     */
    fun previewExport() {
        viewModelScope.launch {
            try {
                _state.update { it.copy(isGeneratingPreview = true, error = null) }

                val form = _state.value.form
                if (form == null) {
                    _state.update {
                        it.copy(
                            error = appStrings.get(R.string.err_no_form_preview),
                            isGeneratingPreview = false
                        )
                    }
                    return@launch
                }

                // Generate preview PDF
                val previewPdf = pdfUtils.generateFilledPdf(
                    originalPdfPath = form.originalFileUri,
                    fields = form.fields,
                    outputDir = context.cacheDir
                )

                if (previewPdf != null) {
                    _state.update {
                        it.copy(
                            previewFilePath = previewPdf.absolutePath,
                            isGeneratingPreview = false
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            error = appStrings.get(R.string.err_failed_generate_pdf_short),
                            isGeneratingPreview = false
                        )
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error generating preview", e)
                _state.update {
                    it.copy(
                        error = appStrings.get(R.string.err_preview_failed, e.message ?: ""),
                        isGeneratingPreview = false
                    )
                }
            }
        }
    }

    /**
     * Export to FHIR server as QuestionnaireResponse + DocumentReference
     */
    fun exportToFhir() {
        viewModelScope.launch {
            try {
                _state.update { it.copy(isExporting = true, error = null) }

                val form = _state.value.form
                if (form == null) {
                    _state.update { it.copy(error = appStrings.get(R.string.err_no_form_export), isExporting = false) }
                    return@launch
                }

                // Generate filled PDF for DocumentReference
                val filledPdf = pdfUtils.generateFilledPdf(
                    originalPdfPath = form.originalFileUri,
                    fields = form.fields,
                    outputDir = context.cacheDir
                )

                val pdfData = filledPdf?.readBytes()

                // Look up associated FHIR patient ID
                val patientFhirId = fhirRepository.getFhirId(form.id, "Patient")

                val result = fhirRepository.exportFormToFhir(
                    userId = form.userId,
                    form = form,
                    patientFhirId = patientFhirId,
                    pdfData = pdfData
                )

                // Cleanup temp file
                filledPdf?.delete()

                if (result.isSuccess) {
                    formRepository.updateFormStatus(formId, FormStatus.EXPORTED)
                    val exportResult = result.getOrThrow()
                    _state.update {
                        it.copy(
                            isExporting = false,
                            exportSuccess = true,
                            exportMessage = appStrings.get(R.string.msg_exported_fhir, exportResult.questionnaireResponseId)
                        )
                    }
                    Log.d(TAG, "Form exported to FHIR: ${exportResult.questionnaireResponseId}")
                } else {
                    _state.update {
                        it.copy(
                            error = appStrings.get(R.string.err_fhir_export_failed, result.exceptionOrNull()?.message ?: ""),
                            isExporting = false
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error exporting to FHIR", e)
                _state.update {
                    it.copy(
                        error = appStrings.get(R.string.err_fhir_export_failed, e.message ?: ""),
                        isExporting = false
                    )
                }
            }
        }
    }

    /**
     * Check if FHIR server is configured
     */
    fun isFhirConfigured(): Boolean {
        return fhirRepository.loadServerConfig().isConfigured
    }

    /**
     * Clear success state
     */
    fun clearSuccess() {
        _state.update {
            it.copy(
                exportSuccess = false,
                exportMessage = null
            )
        }
    }

    /**
     * Clear error
     */
    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    /**
     * Set navigation flag
     */
    fun navigateBack() {
        _state.update { it.copy(shouldNavigateBack = true) }
    }

    /**
     * Reset navigation flag
     */
    fun resetNavigation() {
        _state.update { it.copy(shouldNavigateBack = false) }
    }
}

/**
 * Export UI state
 */
data class ExportState(
    val form: com.medpull.kiosk.data.models.Form? = null,
    val completionPercentage: Float = 0f,
    val canExport: Boolean = false,
    val isLoading: Boolean = true,
    val isExporting: Boolean = false,
    val isGeneratingPreview: Boolean = false,
    val exportSuccess: Boolean = false,
    val exportMessage: String? = null,
    val previewFilePath: String? = null,
    val localFilePath: String? = null,
    val error: String? = null,
    val shouldNavigateBack: Boolean = false
)
