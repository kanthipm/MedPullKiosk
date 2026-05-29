package com.medpull.kiosk.ui.screens.formfill

import android.app.Application
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medpull.kiosk.data.models.FieldType
import com.medpull.kiosk.data.models.FormField
import com.medpull.kiosk.data.repository.AuthRepository
import com.medpull.kiosk.data.repository.FormRepository
import com.medpull.kiosk.data.repository.StorageRepository
import com.medpull.kiosk.R
import com.medpull.kiosk.data.repository.TranslationRepository
import com.medpull.kiosk.utils.AppStrings
import com.medpull.kiosk.utils.LocaleManager
import com.medpull.kiosk.utils.PdfUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * ViewModel for form filling screen
 */
@HiltViewModel
class FormFillViewModel @Inject constructor(
    private val formRepository: FormRepository,
    private val translationRepository: TranslationRepository,
    private val storageRepository: StorageRepository,
    private val authRepository: AuthRepository,
    private val localeManager: LocaleManager,
    private val pdfUtils: PdfUtils,
    private val appStrings: AppStrings,
    @ApplicationContext private val appContext: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "FormFillViewModel"
    }

    private val formId: String = savedStateHandle.get<String>("formId") ?: ""

    private val _state = MutableStateFlow(FormFillState())
    val state: StateFlow<FormFillState> = _state.asStateFlow()

    init {
        loadForm()
    }

    /**
     * Load form and fields
     */
    private fun loadForm() {
        viewModelScope.launch {
            // Resolve user language once
            val language = localeManager.getCurrentLanguage(appContext)
            _state.update { it.copy(userLanguage = language) }

            var hasTranslated = false

            try {
                formRepository.getFormByIdFlow(formId)
                    .catch { e ->
                        Log.e(TAG, "Error loading form", e)
                        _state.update { it.copy(error = appStrings.get(R.string.err_failed_load_form, e.message ?: "")) }
                    }
                    .collect { form ->
                        if (form != null) {
                            _state.update {
                                it.copy(
                                    form = form,
                                    fields = form.fields,
                                    isLoading = false,
                                    completionPercentage = calculateCompletionPercentage(form.fields)
                                )
                            }

                            // Auto-translate once if non-English and fields lack translations.
                            // Guard prevents re-triggering on DB-write Flow re-emissions.
                            if (!hasTranslated && language != "en" && form.fields.any { it.translatedText == null }) {
                                hasTranslated = true
                                translateAllFields(language)
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
     * Translate all fields to the target language
     */
    private suspend fun translateAllFields(targetLanguage: String) {
        try {
            val currentFields = _state.value.fields
            val translationMap = translationRepository.translateFormFields(currentFields, targetLanguage)

            val updatedFields = currentFields.map { field ->
                val translated = translationMap[field.id]
                if (translated != null) field.copy(translatedText = translated) else field
            }

            _state.update { it.copy(fields = updatedFields) }
            Log.d(TAG, "Auto-translated ${translationMap.size} fields to $targetLanguage")
        } catch (e: Exception) {
            Log.e(TAG, "Auto-translation failed", e)
            // Non-fatal — fields still display in English
        }
    }

    /**
     * Select a field for editing
     */
    fun selectField(field: FormField) {
        _state.update { it.copy(selectedField = field) }
    }

    /**
     * Clear field selection
     */
    fun clearFieldSelection() {
        _state.update { it.copy(selectedField = null) }
    }

    /**
     * Update field value
     */
    fun updateFieldValue(fieldId: String, value: String) {
        viewModelScope.launch {
            try {
                formRepository.updateFieldValue(fieldId, value)

                // Update local state
                val updatedFields = _state.value.fields.map { field ->
                    if (field.id == fieldId) {
                        field.copy(value = value)
                    } else {
                        field
                    }
                }

                _state.update {
                    it.copy(
                        fields = updatedFields,
                        selectedField = null,
                        completionPercentage = calculateCompletionPercentage(updatedFields)
                    )
                }

                Log.d(TAG, "Field value updated: $fieldId")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating field value", e)
                _state.update { it.copy(error = appStrings.get(R.string.err_failed_update_field, e.message ?: "")) }
            }
        }
    }

    /**
     * Translate field text
     */
    fun translateField(field: FormField, targetLanguage: String) {
        viewModelScope.launch {
            try {
                val originalText = field.fieldName
                val translatedText = translationRepository.translateText(
                    text = originalText,
                    targetLanguage = targetLanguage
                )

                // Update field in database and local state
                val updatedFields = _state.value.fields.map { f ->
                    if (f.id == field.id) {
                        f.copy(translatedText = translatedText)
                    } else {
                        f
                    }
                }

                _state.update { it.copy(fields = updatedFields) }

                Log.d(TAG, "Field translated: ${field.fieldName} -> $translatedText")
            } catch (e: Exception) {
                Log.e(TAG, "Error translating field", e)
                _state.update { it.copy(error = appStrings.get(R.string.err_translation_failed, e.message ?: "")) }
            }
        }
    }

    /**
     * Calculate form completion percentage
     */
    private fun calculateCompletionPercentage(fields: List<FormField>): Float {
        val fillableFields = fields.filter { it.fieldType != FieldType.STATIC_LABEL }
        if (fillableFields.isEmpty()) return 0f
        val filledCount = fillableFields.count { !it.value.isNullOrBlank() }
        return (filledCount.toFloat() / fillableFields.size) * 100f
    }

    /**
     * Save and exit
     */
    fun saveAndExit() {
        viewModelScope.launch {
            try {
                // All field updates are already saved in real-time
                // Just mark form as ready to export if all required fields filled
                val allRequiredFilled = formRepository.areAllRequiredFieldsFilled(formId)

                if (allRequiredFilled) {
                    formRepository.updateFormStatus(
                        formId,
                        com.medpull.kiosk.data.models.FormStatus.COMPLETED
                    )
                }

                _state.update { it.copy(shouldNavigateBack = true) }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving form", e)
                _state.update { it.copy(error = appStrings.get(R.string.err_failed_save, e.message ?: "")) }
            }
        }
    }

    /**
     * Generate a new filled PDF with field values translated to English
     */
    fun generateNewForm() {
        viewModelScope.launch {
            _state.update { it.copy(isGeneratingForm = true, generatedFormPath = null, generatedFormError = null) }

            try {
                val currentForm = _state.value.form
                    ?: throw IllegalStateException("No form loaded")
                val currentFields = _state.value.fields

                // Collect filled field values (skip blank ones)
                val filledValues = currentFields
                    .filter { !it.value.isNullOrBlank() }
                    .associate { it.id to it.value!! }

                if (filledValues.isEmpty()) {
                    _state.update { it.copy(
                        isGeneratingForm = false,
                        generatedFormError = appStrings.get(R.string.err_no_fields_filled)
                    ) }
                    return@launch
                }

                // Auto-detect language of each field and translate to English if needed
                Log.d(TAG, "Translating ${filledValues.size} field values to English...")
                filledValues.forEach { (id, value) ->
                    Log.d(TAG, "  Before translation: field=$id value='$value'")
                }
                val englishValues = translationRepository.translateValuesToEnglishAutoDetect(filledValues)
                englishValues.forEach { (id, value) ->
                    Log.d(TAG, "  After translation: field=$id value='$value'")
                }

                // Build fields with translated English values for PDF generation
                val fieldsForPdf = currentFields.map { field ->
                    val englishValue = englishValues[field.id]
                    if (englishValue != null) field.copy(value = englishValue) else field
                }

                // Generate filled PDF on IO dispatcher
                val outputDir = File(appContext.filesDir, "generated_forms").also { it.mkdirs() }
                val outputFile = withContext(Dispatchers.IO) {
                    pdfUtils.generateFilledPdf(
                        originalPdfPath = currentForm.originalFileUri,
                        fields = fieldsForPdf,
                        outputDir = outputDir
                    )
                }

                if (outputFile != null) {
                    _state.update { it.copy(
                        isGeneratingForm = false,
                        generatedFormPath = outputFile.absolutePath
                    ) }
                    Log.d(TAG, "Generated form at: ${outputFile.absolutePath}")
                } else {
                    _state.update { it.copy(
                        isGeneratingForm = false,
                        generatedFormError = appStrings.get(R.string.err_failed_generate_pdf_short)
                    ) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error generating new form", e)
                _state.update { it.copy(
                    isGeneratingForm = false,
                    generatedFormError = e.message ?: appStrings.get(R.string.err_failed_generate_pdf_short)
                ) }
            }
        }
    }

    /**
     * Reset generated form dialog state
     */
    fun clearGeneratedFormState() {
        _state.update { it.copy(
            generatedFormPath = null,
            generatedFormError = null,
            generatedFormExportSuccess = null
        ) }
    }

    /**
     * Export the generated PDF to S3 via StorageRepository
     */
    fun exportGeneratedFormToS3() {
        val path = _state.value.generatedFormPath ?: return
        viewModelScope.launch {
            _state.update { it.copy(isExportingGeneratedForm = true, generatedFormExportSuccess = null) }
            try {
                val userId = authRepository.getCurrentUserId()
                    ?: throw IllegalStateException(appStrings.get(R.string.err_no_user_logged_in))
                val file = File(path)
                val result = storageRepository.uploadFilledForm(file, userId, formId)
                result.fold(
                    onSuccess = { message ->
                        _state.update { it.copy(
                            isExportingGeneratedForm = false,
                            generatedFormExportSuccess = message
                        ) }
                        Log.d(TAG, "Generated form uploaded to S3: $message")
                    },
                    onFailure = { e ->
                        _state.update { it.copy(
                            isExportingGeneratedForm = false,
                            generatedFormError = appStrings.get(R.string.err_upload_failed, e.message ?: "")
                        ) }
                        Log.e(TAG, "Failed to upload generated form", e)
                    }
                )
            } catch (e: Exception) {
                _state.update { it.copy(
                    isExportingGeneratedForm = false,
                    generatedFormError = appStrings.get(R.string.err_upload_failed, e.message ?: "")
                ) }
                Log.e(TAG, "Failed to upload generated form", e)
            }
        }
    }

    /**
     * Save the generated PDF to the device's Documents folder
     */
    fun exportGeneratedFormToLocal() {
        val path = _state.value.generatedFormPath ?: return
        viewModelScope.launch {
            _state.update { it.copy(isExportingGeneratedForm = true, generatedFormExportSuccess = null) }
            try {
                val sourceFile = File(path)
                val docsDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOCUMENTS
                )
                val destFile = File(docsDir, sourceFile.name)
                withContext(Dispatchers.IO) {
                    docsDir.mkdirs()
                    sourceFile.copyTo(destFile, overwrite = true)
                }
                _state.update { it.copy(
                    isExportingGeneratedForm = false,
                    generatedFormExportSuccess = appStrings.get(R.string.msg_saved_to, destFile.absolutePath)
                ) }
                Log.d(TAG, "Generated form saved locally: ${destFile.absolutePath}")
            } catch (e: Exception) {
                _state.update { it.copy(
                    isExportingGeneratedForm = false,
                    generatedFormError = appStrings.get(R.string.err_save_failed, e.message ?: "")
                ) }
                Log.e(TAG, "Failed to save generated form locally", e)
            }
        }
    }

    /**
     * Clear error
     */
    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    /**
     * Reset navigation flag
     */
    fun resetNavigation() {
        _state.update { it.copy(shouldNavigateBack = false) }
    }

    /**
     * Set total page count (called when PDF is loaded)
     */
    fun setTotalPages(count: Int) {
        _state.update { it.copy(totalPages = count.coerceAtLeast(1)) }
    }

    /**
     * Set current page (bounds-checked against totalPages)
     */
    fun setCurrentPage(page: Int) {
        _state.update {
            it.copy(currentPage = page.coerceIn(0, it.totalPages - 1))
        }
    }

    /**
     * Toggle field overlay visibility
     */
    fun toggleFieldOverlays() {
        _state.update { it.copy(showFieldOverlays = !it.showFieldOverlays) }
    }
}

/**
 * Form fill UI state
 */
data class FormFillState(
    val form: com.medpull.kiosk.data.models.Form? = null,
    val fields: List<FormField> = emptyList(),
    val selectedField: FormField? = null,
    val currentPage: Int = 0,
    val totalPages: Int = 1,
    val showFieldOverlays: Boolean = true,
    val completionPercentage: Float = 0f,
    val isLoading: Boolean = true,
    val error: String? = null,
    val shouldNavigateBack: Boolean = false,
    val userLanguage: String = "en",
    val isGeneratingForm: Boolean = false,
    val generatedFormPath: String? = null,
    val generatedFormError: String? = null,
    val isExportingGeneratedForm: Boolean = false,
    val generatedFormExportSuccess: String? = null
)
