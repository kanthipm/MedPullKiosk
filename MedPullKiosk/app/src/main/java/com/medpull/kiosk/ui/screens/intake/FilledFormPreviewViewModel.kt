package com.medpull.kiosk.ui.screens.intake

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medpull.kiosk.data.models.FormField
import android.content.Context
import com.medpull.kiosk.data.repository.AuthRepository
import com.medpull.kiosk.data.repository.FormRepository
import com.medpull.kiosk.utils.PdfFormFiller
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class FilledFormPreviewViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val formRepository: FormRepository,
    private val authRepository: AuthRepository,
    private val pdfFormFiller: PdfFormFiller,
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

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}

data class FilledFormPreviewState(
    val isLoading: Boolean = true,
    val formName: String = "",
    val pdfFile: File? = null,
    val error: String? = null
)
