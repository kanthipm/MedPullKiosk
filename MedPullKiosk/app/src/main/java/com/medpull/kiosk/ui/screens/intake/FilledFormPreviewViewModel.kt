package com.medpull.kiosk.ui.screens.intake

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medpull.kiosk.data.models.DocumentType
import com.medpull.kiosk.data.models.UploadStatus
import com.medpull.kiosk.data.repository.AuthRepository
import com.medpull.kiosk.data.repository.DocumentRepository
import com.medpull.kiosk.data.repository.FormRepository
import com.medpull.kiosk.utils.ClinicSubmission
import com.medpull.kiosk.utils.PdfFormFiller
import com.medpull.kiosk.utils.SubmissionDocument
import com.medpull.kiosk.utils.SubmissionFinancial
import com.medpull.kiosk.utils.SubmissionHousehold
import com.medpull.kiosk.utils.SubmissionPersonal
import com.medpull.kiosk.utils.SubmissionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class FilledFormPreviewViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val formRepository: FormRepository,
    private val documentRepository: DocumentRepository,
    private val authRepository: AuthRepository,
    private val pdfFormFiller: PdfFormFiller,
    private val submissionStore: SubmissionStore,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "FilledFormPreviewVM"
    }

    private val formId: String = savedStateHandle.get<String>("formId") ?: ""

    private val _state = MutableStateFlow(FilledFormPreviewState())
    val state: StateFlow<FilledFormPreviewState> = _state.asStateFlow()

    init {
        generateFilledPdf()
    }

    private fun generateFilledPdf() {
        viewModelScope.launch {
            try {
                _state.update { it.copy(isLoading = true) }

                val form = withContext(Dispatchers.IO) {
                    formRepository.getFormById(formId)
                }

                if (form == null) {
                    _state.update { it.copy(isLoading = false, error = "Form not found") }
                    return@launch
                }

                _state.update { it.copy(formName = form.fileName) }

                val outputDir = withContext(Dispatchers.IO) {
                    File(context.filesDir, "pdf_exports")
                }

                val filledPdf = withContext(Dispatchers.IO) {
                    when (formId) {
                        "coastal_gateway_intake" ->
                            pdfFormFiller.fillCoastalGatewayForm(form.fields, outputDir)
                        else ->
                            pdfFormFiller.fillForm(form.fields, "forms/${formId}.pdf", form.fileName, outputDir)
                    }
                }

                if (filledPdf != null) {
                    _state.update { it.copy(isLoading = false, pdfFile = filledPdf) }
                } else {
                    _state.update { it.copy(isLoading = false, error = "Could not generate filled PDF") }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error generating filled PDF", e)
                _state.update { it.copy(isLoading = false, error = e.message ?: "Unknown error") }
            }
        }
    }

    fun sendToClinic() {
        if (_state.value.isSending || _state.value.isSent) return
        viewModelScope.launch {
            _state.update { it.copy(isSending = true) }
            try {
                val submission = withContext(Dispatchers.IO) { buildSubmission() }
                withContext(Dispatchers.IO) { submissionStore.save(submission) }
                _state.update { it.copy(isSending = false, isSent = true) }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending to clinic", e)
                _state.update { it.copy(isSending = false, error = "Failed to send: ${e.message}") }
            }
        }
    }

    private suspend fun buildSubmission(): ClinicSubmission {
        val form = formRepository.getFormById(formId)
        val fields = form?.fields?.associateBy { it.id } ?: emptyMap()
        val docs = documentRepository.getDocumentsOnce(formId)

        fun field(id: String) = fields[id]?.value?.trim().orEmpty()

        val addressParts = listOfNotNull(
            field("address_street").ifBlank { null },
            field("address_city").ifBlank { null },
            field("address_state").ifBlank { null },
            field("address_zip").ifBlank { null }
        )
        val address = addressParts.joinToString(", ")

        val householdSize = field("household_size").toIntOrNull() ?: 0
        val dependents = field("number_of_dependents").toIntOrNull() ?: 0
        val monthlyIncome = field("monthly_income")
            .replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
        val incomeSources = field("income_sources")
            .split(",", ";").map { it.trim() }.filter { it.isNotBlank() }
            .ifEmpty { listOf("Not specified") }

        val submissionDocs = docs.map { doc ->
            val status = doc.uploadStatus
            SubmissionDocument(
                id = doc.id,
                type = doc.documentType.replace("_", " ").lowercase()
                    .split(" ").joinToString(" ") { it.replaceFirstChar(Char::uppercase) },
                fileName = doc.filePath?.substringAfterLast("/") ?: "",
                uploadStatus = when (status) {
                    UploadStatus.UPLOADED.name -> "uploaded"
                    UploadStatus.SKIPPED.name -> "skipped"
                    else -> "missing"
                },
                uploadedAt = if (status == UploadStatus.UPLOADED.name)
                    DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(doc.timestamp))
                else null
            )
        }

        // Required docs that are missing OR skipped both make the application incomplete
        val incompleteDocCount = docs.count { doc ->
            val type = DocumentType.values().find { it.name == doc.documentType }
            type?.required == true && doc.uploadStatus != UploadStatus.UPLOADED.name
        }
        val appStatus = if (incompleteDocCount > 0) "INCOMPLETE_APPLICATION" else "READY_FOR_REVIEW"

        return ClinicSubmission(
            id = formId,
            submittedAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
            status = appStatus,
            personal = SubmissionPersonal(
                fullName = field("full_name").ifBlank { "Unknown Patient" },
                dob = field("date_of_birth"),
                address = address,
                phone = field("phone_number").ifBlank { null },
                email = field("email").ifBlank { null }
            ),
            household = SubmissionHousehold(
                householdSize = householdSize,
                dependents = dependents
            ),
            financial = SubmissionFinancial(
                incomeSources = incomeSources,
                monthlyIncome = monthlyIncome,
                employmentStatus = field("employment_status").ifBlank { "Not specified" },
                insuranceStatus = field("insurance_status").ifBlank { "Not specified" }
            ),
            documents = submissionDocs,
            missingDocumentsCount = incompleteDocCount,
            missingRequiredFields = emptyList()
        )
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}

data class FilledFormPreviewState(
    val isLoading: Boolean = true,
    val formName: String = "",
    val pdfFile: File? = null,
    val isSending: Boolean = false,
    val isSent: Boolean = false,
    val error: String? = null
)
