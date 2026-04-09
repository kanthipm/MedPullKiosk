package com.medpull.kiosk.ui.screens.intake

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medpull.kiosk.data.engine.IntakeConversationEngine
import com.medpull.kiosk.data.models.FieldType
import com.medpull.kiosk.data.models.Form
import com.medpull.kiosk.data.models.FormField
import com.medpull.kiosk.data.models.FormIntakeFlow
import com.medpull.kiosk.data.models.FormStatus
import com.medpull.kiosk.data.models.IntakeAction
import com.medpull.kiosk.data.models.IntakeQuestion
import com.medpull.kiosk.data.repository.FormRepository
import com.medpull.kiosk.data.repository.GuidedIntakeRepository
import com.medpull.kiosk.ui.screens.ai.ChatMessage
import com.medpull.kiosk.utils.LocaleManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class GuidedIntakeViewModel @Inject constructor(
    private val formRepository: FormRepository,
    private val intakeRepository: GuidedIntakeRepository,
    private val engine: IntakeConversationEngine,
    private val localeManager: LocaleManager,
    @ApplicationContext private val appContext: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "GuidedIntakeViewModel"
        private const val COASTAL_GATEWAY_ID = "coastal_gateway_intake"
        private const val SCHEMA_FILE = "schemas/coastal_gateway_intake.json"

        /** Parse the Coastal Gateway JSON schema into a synthetic Form + fields. */
        fun loadCoastalGatewayForm(context: Context): Form {
            val json = context.assets.open(SCHEMA_FILE).bufferedReader().readText()
            val root = JSONObject(json)
            val formName = root.optString("form_name", "Coastal Gateway Intake")
            val sections = root.optJSONArray("sections") ?: return emptyForm(formName)

            val fields = mutableListOf<FormField>()
            for (s in 0 until sections.length()) {
                val section = sections.getJSONObject(s)
                val sectionFields = section.optJSONArray("fields") ?: continue
                for (f in 0 until sectionFields.length()) {
                    val field = sectionFields.getJSONObject(f)
                    fields += FormField(
                        id = field.optString("id"),
                        formId = COASTAL_GATEWAY_ID,
                        fieldName = field.optString("label"),
                        originalText = field.optString("label"),
                        translatedText = field.optString("label"),
                        fieldType = when (field.optString("type")) {
                            "date" -> FieldType.DATE
                            "checkbox" -> FieldType.CHECKBOX
                            "radio" -> FieldType.RADIO
                            "dropdown" -> FieldType.DROPDOWN
                            "number", "phone", "zip" -> FieldType.NUMBER
                            else -> FieldType.TEXT
                        },
                        required = field.optBoolean("required", false)
                    )
                }
            }

            return Form(
                id = COASTAL_GATEWAY_ID,
                userId = "builtin",
                fileName = formName,
                originalFileUri = "",
                status = FormStatus.READY,
                fields = fields
            )
        }

        private fun emptyForm(name: String) = Form(
            id = COASTAL_GATEWAY_ID, userId = "builtin",
            fileName = name, originalFileUri = "",
            status = FormStatus.READY, fields = emptyList()
        )
    }

    private val formId: String = savedStateHandle.get<String>("formId") ?: ""

    private val _state = MutableStateFlow(GuidedIntakeState())
    val state: StateFlow<GuidedIntakeState> = _state.asStateFlow()

    // Per-field clarification attempt counts (in-memory, acceptable for pilot session length)
    private val clarificationCounts = mutableMapOf<String, Int>()
    private var malformedResponseCount = 0

    init {
        loadForm()
    }

    private fun loadForm() {
        viewModelScope.launch {
            try {
                _state.update { it.copy(isLoading = true, userLanguage = localeManager.getCurrentLanguage(appContext)) }

                if (formId == COASTAL_GATEWAY_ID) {
                    // Built-in form — parse from assets, seed DB if not already there
                    val form = loadCoastalGatewayForm(appContext)
                    formRepository.saveForm(form)   // upsert — satisfies FK for intake flow
                    initFormState(form)
                } else {
                    formRepository.getFormByIdFlow(formId).collect { form ->
                        if (form != null) {
                            initFormState(form)
                        } else {
                            _state.update { it.copy(error = "Form not found", isLoading = false) }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading form", e)
                _state.update { it.copy(error = "Failed to load form: ${e.message}", isLoading = false) }
            }
        }
    }

    private suspend fun initFormState(form: Form) {
        val flow = intakeRepository.getOrCreateFlow(form.id)

        // Pre-fill language + self-filling fields from app state so Claude never asks them
        val language = localeManager.getCurrentLanguage(appContext)
        val languageLabel = when (language) {
            "es" -> "Español"
            "zh" -> "中文"
            "fr" -> "Français"
            "hi" -> "हिन्दी"
            "ar" -> "Arabic"
            else -> "English"
        }
        val prefilled = form.fields.map { f ->
            when (f.id) {
                "preferred_language" -> f.copy(value = languageLabel)
                "filling_for_self"   -> f.copy(value = "Myself")
                else                 -> f
            }
        }
        // Persist pre-filled values to DB so engine sees them as already answered
        prefilled.filter { it.value != null }.forEach { f ->
            formRepository.updateFieldValue(f.id, f.value)
        }

        val allQuestions = intakeRepository.generateQuestionsFromFields(prefilled)
        val activeQuestions = intakeRepository.getActiveQuestions(form.id, allQuestions, flow)

        _state.update {
            it.copy(
                form = form,
                fields = prefilled,
                intakeFlow = flow,
                allQuestions = allQuestions,
                activeQuestions = activeQuestions,
                isLoading = false
            )
        }

        if (activeQuestions.isNotEmpty()) setCurrentQuestion(flow.currentQuestionIndex)
        if (_state.value.chatMessages.isEmpty()) askNextQuestion()
    }

    /**
     * Process a patient message through the conversation engine.
     * Routes the resulting IntakeAction to the appropriate handler.
     */
    fun sendMessage(message: String) {
        if (message.isBlank()) return

        viewModelScope.launch {
            val userMessage = ChatMessage(text = message, isFromUser = true, timestamp = System.currentTimeMillis())
            _state.update { it.copy(chatMessages = it.chatMessages + userMessage, isLoadingResponse = true, error = null) }

            try {
                val currentState = _state.value
                val action = engine.processInput(
                    message = message,
                    fields = currentState.fields,
                    history = currentState.chatMessages,
                    language = currentState.userLanguage,
                    clarificationCounts = clarificationCounts,
                    malformedCount = malformedResponseCount,
                    formId = formId
                )
                handleAction(action)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing message", e)
                saveSessionState()
                _state.update { it.copy(error = "Failed to get response: ${e.message}", isLoadingResponse = false) }
            }
        }
    }

    /**
     * Route each IntakeAction to the correct handler.
     */
    private suspend fun handleAction(action: IntakeAction) {
        when (action) {
            is IntakeAction.SetField -> handleSetField(action)
            is IntakeAction.AskClarification -> handleAskClarification(action)
            is IntakeAction.SkipSection -> handleSkipSection(action)
            is IntakeAction.FlagForClinic -> handleFlagForClinic(action)
            is IntakeAction.TransitionToReview -> handleTransitionToReview()
            is IntakeAction.Fallback -> handleFallback(action)
        }
    }

    private suspend fun handleSetField(action: IntakeAction.SetField) {
        // Write value to Room DB
        formRepository.updateFieldValue(action.fieldId, action.value)
        intakeRepository.markFieldsInferred(formId, listOf(action.fieldId))

        // Update in-memory field state
        val updatedFields = _state.value.fields.map { f ->
            if (f.id == action.fieldId) f.copy(value = action.value) else f
        }

        // Reset clarification count for this field — it was answered
        clarificationCounts.remove(action.fieldId)

        _state.update {
            it.copy(
                fields = updatedFields,
                isLoadingResponse = false,
                chatMessages = it.chatMessages + ChatMessage(
                    text = action.questionText,
                    isFromUser = false,
                    timestamp = System.currentTimeMillis()
                )
            )
        }

        // Update branching and check completion
        updateBranchingAndProgress(updatedFields)

        Log.d(TAG, "SetField: ${action.fieldId} = ${action.value} (confidence ${action.confidence})")
    }

    private fun handleAskClarification(action: IntakeAction.AskClarification) {
        // Increment clarification count for this field
        action.fieldId?.let { clarificationCounts[it] = (clarificationCounts[it] ?: 0) + 1 }

        _state.update {
            it.copy(
                isLoadingResponse = false,
                chatMessages = it.chatMessages + ChatMessage(
                    text = action.questionText,
                    isFromUser = false,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
        Log.d(TAG, "AskClarification for field: ${action.fieldId} (count: ${clarificationCounts[action.fieldId]})")
    }

    private suspend fun handleSkipSection(action: IntakeAction.SkipSection) {
        if (action.fieldIds.isNotEmpty()) {
            intakeRepository.markFieldsSkipped(formId, action.fieldIds)
        }

        _state.update {
            it.copy(
                isLoadingResponse = false,
                chatMessages = it.chatMessages + ChatMessage(
                    text = action.questionText,
                    isFromUser = false,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
        Log.d(TAG, "SkipSection: ${action.fieldIds}")
    }

    private suspend fun handleFlagForClinic(action: IntakeAction.FlagForClinic) {
        intakeRepository.markFieldsSkipped(formId, listOf(action.fieldId))
        clarificationCounts.remove(action.fieldId)

        _state.update {
            it.copy(
                isLoadingResponse = false,
                chatMessages = it.chatMessages + ChatMessage(
                    text = action.questionText,
                    isFromUser = false,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
        Log.d(TAG, "FlagForClinic: ${action.fieldId} — ${action.reason}")
    }

    private fun handleTransitionToReview() {
        _state.update { it.copy(isLoadingResponse = false, isComplete = true) }
        Log.d(TAG, "TransitionToReview")
    }

    private fun handleFallback(action: IntakeAction.Fallback) {
        malformedResponseCount++
        _state.update {
            it.copy(
                isLoadingResponse = false,
                chatMessages = it.chatMessages + ChatMessage(
                    text = action.questionText,
                    isFromUser = false,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
        Log.w(TAG, "Fallback question used. Session malformed count: $malformedResponseCount")
    }

    /**
     * Ask the first question on session start — engine decides where to begin.
     */
    private fun askNextQuestion() {
        viewModelScope.launch {
            val currentState = _state.value
            if (currentState.fields.isEmpty()) return@launch

            _state.update { it.copy(isLoadingResponse = true) }
            try {
                val action = engine.processInput(
                    message = "Hello, I'm ready to begin my intake.",
                    fields = currentState.fields,
                    history = emptyList(),
                    language = currentState.userLanguage,
                    clarificationCounts = clarificationCounts,
                    malformedCount = malformedResponseCount,
                    formId = formId
                )
                handleAction(action)
            } catch (e: Exception) {
                Log.e(TAG, "Error asking first question", e)
                _state.update { it.copy(isLoadingResponse = false) }
            }
        }
    }

    /**
     * Update progress indicator and check if all required fields are filled.
     */
    private suspend fun updateBranchingAndProgress(updatedFields: List<FormField>) {
        val flow = _state.value.intakeFlow ?: return

        val fieldsToSkip = intakeRepository.getFieldsToSkip(formId, flow, updatedFields)
        if (fieldsToSkip.isNotEmpty()) intakeRepository.markFieldsSkipped(formId, fieldsToSkip)

        val updatedFlow = flow.copy(skippedFieldIds = fieldsToSkip.toSet())
        val activeQuestions = intakeRepository.getActiveQuestions(formId, _state.value.allQuestions, updatedFlow)

        val filledRequired = updatedFields.count { it.required && !it.value.isNullOrBlank() }
        val totalRequired = updatedFields.count { it.required }

        _state.update {
            it.copy(
                intakeFlow = updatedFlow,
                activeQuestions = activeQuestions,
                filledRequiredCount = filledRequired,
                totalRequiredCount = totalRequired
            )
        }

        if (engine.allRequiredFilled(updatedFields)) {
            handleTransitionToReview()
        }
    }

    // ─── Supporting actions ───────────────────────────────────────────────────

    fun setCurrentQuestion(index: Int) {
        val currentState = _state.value
        if (index >= 0 && index < currentState.activeQuestions.size) {
            _state.update {
                it.copy(currentQuestionIndex = index, currentQuestion = currentState.activeQuestions[index])
            }
            viewModelScope.launch { intakeRepository.setCurrentQuestion(formId, index) }
        }
    }

    fun confirmCurrentField() {
        val currentQuestion = _state.value.currentQuestion ?: return
        val field = _state.value.fields.find { it.id == currentQuestion.fieldId } ?: return
        if (field.value != null) {
            viewModelScope.launch {
                try {
                    intakeRepository.markFieldConfirmed(formId, currentQuestion.fieldId)
                    Log.d(TAG, "Confirmed field: ${currentQuestion.fieldId}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error confirming field", e)
                }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun completeIntake() {
        viewModelScope.launch {
            try {
                formRepository.updateFormStatus(formId, com.medpull.kiosk.data.models.FormStatus.COMPLETED)
                _state.update { it.copy(isComplete = true) }
                Log.d(TAG, "Intake completed")
            } catch (e: Exception) {
                Log.e(TAG, "Error completing intake", e)
                _state.update { it.copy(error = "Failed to save: ${e.message}") }
            }
        }
    }

    private suspend fun saveSessionState() {
        try {
            val flow = _state.value.intakeFlow ?: return
            intakeRepository.updateFlow(flow.copy(currentQuestionIndex = _state.value.currentQuestionIndex))
            Log.d(TAG, "Session state saved")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save session state", e)
        }
    }
}

data class GuidedIntakeState(
    val form: com.medpull.kiosk.data.models.Form? = null,
    val fields: List<FormField> = emptyList(),
    val intakeFlow: FormIntakeFlow? = null,
    val allQuestions: List<IntakeQuestion> = emptyList(),
    val activeQuestions: List<IntakeQuestion> = emptyList(),
    val currentQuestionIndex: Int = 0,
    val currentQuestion: IntakeQuestion? = null,
    val chatMessages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingResponse: Boolean = false,
    val error: String? = null,
    val isComplete: Boolean = false,
    val userLanguage: String = "en",
    val filledRequiredCount: Int = 0,
    val totalRequiredCount: Int = 0
)
