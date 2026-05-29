package com.medpull.kiosk.ui.screens.formselection

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medpull.kiosk.data.models.Form
import com.medpull.kiosk.data.models.FormStatus
import com.medpull.kiosk.data.repository.AuthRepository
import com.medpull.kiosk.data.repository.FormProcessResult
import com.medpull.kiosk.R
import com.medpull.kiosk.data.repository.FormRepository
import com.medpull.kiosk.utils.AppStrings
import com.medpull.kiosk.utils.Constants
import com.medpull.kiosk.utils.LocaleManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for form selection and upload screen
 */
@HiltViewModel
class FormSelectionViewModel @Inject constructor(
    private val formRepository: FormRepository,
    private val authRepository: AuthRepository,
    private val localeManager: LocaleManager,
    private val appStrings: AppStrings,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    companion object {
        private const val TAG = "FormSelectionViewModel"
    }

    private val _state = MutableStateFlow(FormSelectionState())
    val state: StateFlow<FormSelectionState> = _state.asStateFlow()

    init {
        loadForms()
    }

    /**
     * Load user's forms
     */
    private fun loadForms() {
        viewModelScope.launch {
            try {
                val userId = authRepository.getCurrentUserId()
                if (userId != null) {
                    formRepository.getFormsByUserIdFlow(userId)
                        .catch { e ->
                            Log.e(TAG, "Error loading forms", e)
                            _state.update { it.copy(error = appStrings.get(R.string.err_failed_load_forms, e.message ?: "")) }
                        }
                        .collect { forms ->
                            _state.update { it.copy(forms = forms, isLoading = false) }
                        }
                } else {
                    _state.update { it.copy(error = appStrings.get(R.string.err_no_user_logged_in), isLoading = false) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading forms", e)
                _state.update { it.copy(error = appStrings.get(R.string.err_failed_load_forms, e.message ?: ""), isLoading = false) }
            }
        }
    }

    /**
     * Upload and process a form file
     */
    fun uploadForm(file: File) {
        viewModelScope.launch {
            try {
                _state.update { it.copy(isUploading = true, uploadProgress = 0f, error = null) }

                // Validate file size
                val fileSizeMB = file.length() / (1024.0 * 1024.0)
                if (fileSizeMB > Constants.Pdf.MAX_FILE_SIZE_MB) {
                    _state.update {
                        it.copy(
                            isUploading = false,
                            error = appStrings.get(
                                R.string.err_file_too_large,
                                String.format("%.2f", fileSizeMB),
                                Constants.Pdf.MAX_FILE_SIZE_MB
                            )
                        )
                    }
                    return@launch
                }

                val userId = authRepository.getCurrentUserId()
                if (userId == null) {
                    _state.update { it.copy(isUploading = false, error = appStrings.get(R.string.err_no_user_logged_in)) }
                    return@launch
                }

                // Create form entity
                val formId = UUID.randomUUID().toString()
                val form = Form(
                    id = formId,
                    userId = userId,
                    fileName = file.name,
                    originalFileUri = file.absolutePath,
                    status = FormStatus.UPLOADING,
                    fields = emptyList(),
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                // Save form to database
                formRepository.saveForm(form)

                Log.d(TAG, "Starting form upload: ${file.name}")

                // Resolve language for parallel translation during processing
                val language = localeManager.getCurrentLanguage(appContext)

                _state.update { it.copy(uploadProgress = 0.05f) }

                // Upload and process form with real progress tracking
                val result = formRepository.uploadAndProcessForm(
                    file = file,
                    userId = userId,
                    formId = formId,
                    targetLanguage = language,
                    onProgress = { progress ->
                        _state.update { it.copy(uploadProgress = progress) }
                    }
                )

                when (result) {
                    is FormProcessResult.Success -> {
                        Log.d(TAG, "Form processed successfully: ${result.fields.size} fields")
                        _state.update {
                            it.copy(
                                isUploading = false,
                                uploadProgress = 1f,
                                successMessage = appStrings.get(R.string.msg_form_uploaded)
                            )
                        }
                        // Clear success message after 3 seconds
                        kotlinx.coroutines.delay(3000)
                        _state.update { it.copy(successMessage = null) }
                    }
                    is FormProcessResult.Processing -> {
                        _state.update {
                            it.copy(
                                isUploading = false,
                                successMessage = appStrings.get(R.string.msg_form_processing)
                            )
                        }
                    }
                    is FormProcessResult.Error -> {
                        Log.e(TAG, "Form processing error: ${result.message}")
                        val isSessionError = result.message.contains("sign out", ignoreCase = true) ||
                            result.message.contains("session expired", ignoreCase = true) ||
                            result.message.contains("credential error", ignoreCase = true)
                        _state.update {
                            it.copy(
                                isUploading = false,
                                error = if (!isSessionError) appStrings.get(R.string.err_failed_process_form, result.message) else null,
                                sessionExpired = isSessionError,
                                sessionExpiredMessage = if (isSessionError) result.message else null
                            )
                        }
                    }
                    is FormProcessResult.QueuedForSync -> {
                        _state.update {
                            it.copy(
                                isUploading = false,
                                successMessage = appStrings.get(R.string.msg_form_queued)
                            )
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error uploading form", e)
                _state.update {
                    it.copy(
                        isUploading = false,
                        error = appStrings.get(R.string.err_failed_upload_form, e.message ?: "")
                    )
                }
            }
        }
    }

    /**
     * Delete a form
     */
    fun deleteForm(formId: String) {
        viewModelScope.launch {
            try {
                formRepository.deleteForm(formId)
                Log.d(TAG, "Form deleted: $formId")
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting form", e)
                _state.update { it.copy(error = appStrings.get(R.string.err_failed_delete_form, e.message ?: "")) }
            }
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    /**
     * Clear success message
     */
    fun clearSuccessMessage() {
        _state.update { it.copy(successMessage = null) }
    }

    /**
     * Refresh forms list
     */
    fun refreshForms() {
        _state.update { it.copy(isLoading = true) }
        loadForms()
    }
}

/**
 * Form selection UI state
 */
data class FormSelectionState(
    val forms: List<Form> = emptyList(),
    val isLoading: Boolean = true,
    val isUploading: Boolean = false,
    val uploadProgress: Float = 0f,
    val error: String? = null,
    val successMessage: String? = null,
    val sessionExpired: Boolean = false,
    val sessionExpiredMessage: String? = null
)
