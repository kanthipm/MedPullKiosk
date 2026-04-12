package com.medpull.kiosk.ui.screens.intake

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medpull.kiosk.data.engine.IntakeConversationEngine
import com.medpull.kiosk.data.models.FieldType
import com.medpull.kiosk.data.models.FieldUpdate
import com.medpull.kiosk.data.models.Form
import com.medpull.kiosk.data.models.FormField
import com.medpull.kiosk.data.models.FormIntakeFlow
import com.medpull.kiosk.data.models.FormStatus
import com.medpull.kiosk.data.local.entities.PatientCacheEntity
import com.medpull.kiosk.data.repository.AuthRepository
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

/**
 * ViewModel for the guided intake screen.
 *
 * DESIGN: ViewModel drives, engine assists.
 *
 * The ViewModel owns all field progression logic:
 *   - Which field to ask next (deterministic, schema order)
 *   - Skip rules (derived from schema's skip_if blocks, applied on field save)
 *   - When to transition to review (all fields addressed)
 *
 * The engine has two focused jobs:
 *   - generateQuestion(field)  → warm question text
 *   - parseAnswer(field, text) → extracted value + bonus fills
 *
 * This replaces the previous free-form "AI decides everything" architecture
 * that caused ~70% of fields to be silently skipped.
 */
@HiltViewModel
class GuidedIntakeViewModel @Inject constructor(
    private val formRepository: FormRepository,
    private val intakeRepository: GuidedIntakeRepository,
    private val authRepository: AuthRepository,
    private val engine: IntakeConversationEngine,
    private val localeManager: LocaleManager,
    @ApplicationContext private val appContext: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "GuidedIntakeViewModel"
        const val COASTAL_GATEWAY_ID = "coastal_gateway_intake"
        private const val SCHEMA_FILE = "schemas/coastal_gateway_intake.json"
        private const val CONFIDENCE_THRESHOLD = 0.75f
        private const val MAX_CLARIFICATIONS = 2

        /**
         * Fields that belong to the review step, not the intake conversation.
         * name_confirmation: covered by the review screen edit-in-place.
         * telehealth_notice_acknowledged: skip for in-person visits (default).
         */
        val SKIP_DURING_INTAKE = setOf(
            "name_confirmation",
            "telehealth_notice_acknowledged"
        )

        /**
         * Deterministic skip rules derived from schema skip_if blocks.
         * fieldId → triggerValue → list of fields to skip.
         *
         * The ViewModel applies these the moment a field value is saved, so the AI
         * never needs to decide whether to ask insurance questions to an uninsured patient.
         */
        val SKIP_RULES: Map<String, Map<String, List<String>>> = mapOf(
            "physical_same_as_mailing" to mapOf(
                "Yes" to listOf(
                    "physical_address_street", "physical_city", "physical_state", "physical_zip"
                )
            ),
            "has_insurance" to mapOf(
                "No" to listOf(
                    "primary_insurance_provider", "primary_insurance_id", "primary_insurance_group",
                    "policyholder_is_self", "policyholder_name", "policyholder_dob",
                    "policyholder_relationship", "has_secondary_insurance",
                    "secondary_insurance_provider", "secondary_insurance_id", "secondary_insurance_group"
                )
            ),
            "policyholder_is_self" to mapOf(
                "Yes" to listOf("policyholder_name", "policyholder_dob", "policyholder_relationship")
            ),
            "has_secondary_insurance" to mapOf(
                "No" to listOf(
                    "secondary_insurance_provider", "secondary_insurance_id", "secondary_insurance_group"
                )
            ),
            "family_history_any" to mapOf(
                "No" to listOf("family_history_conditions", "family_history_members")
            ),
            "medical_conditions" to mapOf(
                "None" to listOf("other_health_concerns")
            ),
            "tobacco_use" to mapOf(
                "No" to listOf("tobacco_type", "tobacco_frequency")
            ),
            "alcohol_use" to mapOf(
                "No" to listOf("alcohol_frequency")
            ),
            "surgeries_any" to mapOf(
                "No" to listOf("surgeries_list")
            ),
            "medications_any" to mapOf(
                "No" to listOf("medications_list")
            ),
            "allergies_any" to mapOf(
                "No" to listOf("allergies_list")
            ),
            "authorized_phi_contacts_any" to mapOf(
                "No" to listOf(
                    "authorized_phi_contact_1_name", "authorized_phi_contact_1_relationship",
                    "authorized_phi_contact_2_name", "authorized_phi_contact_2_relationship"
                )
            ),
            "filling_for_self" to mapOf(
                "Myself" to listOf("representative_name", "representative_relationship")
            )
        )

        /** Parse the Coastal Gateway JSON schema into a Form + field list. */
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
                    val opts = field.optJSONArray("options")
                        ?.let { arr -> (0 until arr.length()).map { arr.getString(it) } }
                        ?: emptyList()
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
                            "multi_select" -> FieldType.MULTI_SELECT
                            "signature" -> FieldType.SIGNATURE
                            "static_label" -> FieldType.STATIC_LABEL
                            "number", "phone", "zip", "email" -> FieldType.NUMBER
                            else -> FieldType.TEXT
                        },
                        required = field.optBoolean("required", false),
                        options = opts,
                        description = field.optString("ai_note", "").ifBlank { null }
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

    init {
        loadForm()
    }

    // ─── Form Loading ─────────────────────────────────────────────────────────

    private fun loadForm() {
        viewModelScope.launch {
            try {
                _state.update {
                    it.copy(
                        isLoading = true,
                        userLanguage = localeManager.getCurrentLanguage(appContext)
                    )
                }

                if (formId == COASTAL_GATEWAY_ID) {
                    val form = loadCoastalGatewayForm(appContext)
                    formRepository.saveForm(form)
                    initFormState(form)
                } else {
                    formRepository.getFormByIdFlow(formId).collect { form ->
                        if (form != null) initFormState(form)
                        else _state.update { it.copy(error = "Form not found", isLoading = false) }
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
        val language = localeManager.getCurrentLanguage(appContext)

        // Pre-fill preferred_language from app locale
        val languageLabel = when (language) {
            "es" -> "Español"; "zh" -> "中文"; "fr" -> "Français"
            "hi" -> "हिन्दी"; "ar" -> "Arabic"; else -> "English"
        }
        val withLanguage = form.fields.map { f ->
            if (f.id == "preferred_language") f.copy(value = languageLabel) else f
        }

        // Apply cross-form demographic prefill from patient cache
        val userId = authRepository.getCurrentUserId() ?: ""
        val cache = if (userId.isNotBlank()) formRepository.getPatientCache(userId) else null
        val withCache = if (cache != null) {
            withLanguage.map { f ->
                val cached = cache.valueForFieldId(f.id)
                if (f.id in PatientCacheEntity.DEMOGRAPHIC_FIELD_IDS && !cached.isNullOrBlank())
                    f.copy(value = cached)
                else f
            }
        } else withLanguage

        // Persist pre-filled values to DB
        withCache.filter { it.value != null }.forEach { f ->
            formRepository.updateFieldValue(f.id, f.value!!)
        }

        // Restore skip state from previous session
        val restoredSkips = flow.skippedFieldIds

        val allSkipped = restoredSkips + SKIP_DURING_INTAKE
        val filledCount = withCache.count { f ->
            f.id !in allSkipped &&
            f.fieldType != FieldType.STATIC_LABEL &&
            !f.value.isNullOrBlank()
        }
        val totalCount = withCache.count { f ->
            f.id !in allSkipped && f.fieldType != FieldType.STATIC_LABEL
        }

        val welcomeMessages: List<ChatMessage> = if (cache != null) listOf(
            ChatMessage(
                text = "Welcome back! I've pre-filled your contact information from your previous visit. Let's confirm anything that may have changed and continue.",
                isFromUser = false,
                timestamp = System.currentTimeMillis()
            )
        ) else emptyList()

        _state.update {
            it.copy(
                form = form,
                fields = withCache,
                intakeFlow = flow,
                skippedFieldIds = restoredSkips,
                chatMessages = welcomeMessages,
                userLanguage = language,
                isLoading = false,
                filledCount = filledCount,
                totalCount = totalCount
            )
        }

        // Begin deterministic field-by-field progression
        advanceToNextField()
    }

    // ─── Field Progression State Machine ─────────────────────────────────────

    /**
     * Find the next field that needs answering and either:
     *  - Deliver a static label (framing text) and recurse
     *  - Set currentAskingField and generate a question
     *  - Transition to review if all fields are addressed
     */
    private fun advanceToNextField() {
        val state = _state.value
        val allSkipped = state.skippedFieldIds + SKIP_DURING_INTAKE

        val next = state.fields.firstOrNull { f ->
            f.id !in allSkipped && f.value.isNullOrBlank()
        }

        when {
            next == null -> {
                // All fields addressed — go to review
                Log.d(TAG, "All fields addressed — transitioning to review")
                _state.update { it.copy(isComplete = true, isLoadingResponse = false) }
            }
            next.fieldType == FieldType.STATIC_LABEL -> {
                // Deliver framing text and auto-advance (no user input needed)
                deliverStaticLabel(next)
            }
            else -> {
                // Ask this specific field
                startAskingField(next)
            }
        }
    }

    /** Deliver a static_label framing message and immediately advance. */
    private fun deliverStaticLabel(field: FormField) {
        val text = field.description ?: field.fieldName
        viewModelScope.launch {
            try {
                formRepository.updateFieldValue(field.id, "delivered")
            } catch (e: Exception) {
                Log.w(TAG, "Could not persist static label delivery for ${field.id}", e)
            }
            _state.update { s ->
                s.copy(
                    fields = s.fields.map { f ->
                        if (f.id == field.id) f.copy(value = "delivered") else f
                    },
                    chatMessages = s.chatMessages + ChatMessage(
                        text = text,
                        isFromUser = false,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
            advanceToNextField()
        }
    }

    /** Set [field] as the current target and generate its question via the engine. */
    private fun startAskingField(field: FormField) {
        _state.update { it.copy(currentAskingField = field, clarificationCount = 0) }
        askCurrentField()
    }

    /** Call engine.generateQuestion and post the result as an AI message. */
    private fun askCurrentField() {
        viewModelScope.launch {
            val state = _state.value
            val field = state.currentAskingField ?: return@launch
            _state.update { it.copy(isLoadingResponse = true) }
            try {
                val question = engine.generateQuestion(
                    field = field,
                    filledFields = state.fields.filter { !it.value.isNullOrBlank() },
                    language = state.userLanguage,
                    guardianMode = state.guardianMode
                )
                _state.update {
                    it.copy(
                        isLoadingResponse = false,
                        chatMessages = it.chatMessages + ChatMessage(
                            text = question,
                            isFromUser = false,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "generateQuestion failed for ${field.id}", e)
                val fallback = intakeRepository.generateFallbackQuestion(field)
                _state.update {
                    it.copy(
                        isLoadingResponse = false,
                        chatMessages = it.chatMessages + ChatMessage(
                            text = fallback,
                            isFromUser = false,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    // ─── Answer Handling ──────────────────────────────────────────────────────

    /**
     * Process a patient's answer.
     *
     * Routes to the engine's parseAnswer for the current target field, then:
     *   - High confidence → save + advance to next field
     *   - Needs clarification → ask follow-up (same field, increment count)
     *   - Too many clarifications → escalate to staff flag and advance
     */
    fun sendMessage(message: String) {
        if (message.isBlank()) return
        val state = _state.value
        val targetField = state.currentAskingField ?: return

        viewModelScope.launch {
            _state.update {
                it.copy(
                    chatMessages = it.chatMessages + ChatMessage(
                        text = message,
                        isFromUser = true,
                        timestamp = System.currentTimeMillis()
                    ),
                    isLoadingResponse = true,
                    error = null
                )
            }

            try {
                val result = engine.parseAnswer(
                    field = targetField,
                    userAnswer = message,
                    allFields = state.fields,
                    language = state.userLanguage
                )

                when {
                    result.value != null && result.confidence >= CONFIDENCE_THRESHOLD -> {
                        saveFieldAndAdvance(targetField, result.value, result.alsoFills)
                    }

                    state.clarificationCount >= MAX_CLARIFICATIONS -> {
                        escalateField(targetField)
                    }

                    result.needsClarification -> {
                        val clarificationText = result.clarificationQuestion.ifBlank {
                            "Could you clarify that for me?"
                        }
                        _state.update {
                            it.copy(
                                isLoadingResponse = false,
                                clarificationCount = it.clarificationCount + 1,
                                chatMessages = it.chatMessages + ChatMessage(
                                    text = clarificationText,
                                    isFromUser = false,
                                    timestamp = System.currentTimeMillis()
                                )
                            )
                        }
                    }

                    else -> {
                        _state.update {
                            it.copy(
                                isLoadingResponse = false,
                                clarificationCount = it.clarificationCount + 1,
                                chatMessages = it.chatMessages + ChatMessage(
                                    text = "I want to make sure I got that right — could you say that again?",
                                    isFromUser = false,
                                    timestamp = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing answer", e)
                _state.update {
                    it.copy(
                        isLoadingResponse = false,
                        error = "Could not process your answer. Please try again."
                    )
                }
            }
        }
    }

    /** Save a field value (and any bonus fills), apply skip rules, update counts, advance. */
    private suspend fun saveFieldAndAdvance(
        field: FormField,
        value: String,
        alsoFills: List<FieldUpdate>
    ) {
        // Persist primary field
        formRepository.updateFieldValue(field.id, value)
        intakeRepository.markFieldsInferred(formId, listOf(field.id))

        // Persist bonus fields
        alsoFills.forEach { bonus -> formRepository.updateFieldValue(bonus.fieldId, bonus.value) }

        // Update in-memory fields
        val updatedFields = _state.value.fields.map { f ->
            when {
                f.id == field.id -> f.copy(value = value)
                else -> alsoFills.find { it.fieldId == f.id }?.let { f.copy(value = it.value) } ?: f
            }
        }

        // Compute new skips triggered by this field's value
        val newSkips = computeSkips(field.id, value)
        val allBonus = alsoFills.flatMap { bonus -> computeSkips(bonus.fieldId, bonus.value).toList() }
        val allNewSkips = newSkips + allBonus

        val updatedSkipped = _state.value.skippedFieldIds + allNewSkips

        // Guardian mode
        val newGuardianMode = when {
            field.id == "filling_for_self" && value == "Someone else" -> true
            else -> _state.value.guardianMode
        }

        // Recalculate progress
        val skipSet = updatedSkipped + SKIP_DURING_INTAKE
        val filledCount = updatedFields.count { f ->
            f.id !in skipSet && f.fieldType != FieldType.STATIC_LABEL && !f.value.isNullOrBlank()
        }
        val totalCount = updatedFields.count { f ->
            f.id !in skipSet && f.fieldType != FieldType.STATIC_LABEL
        }

        _state.update {
            it.copy(
                fields = updatedFields,
                skippedFieldIds = updatedSkipped,
                guardianMode = newGuardianMode,
                isLoadingResponse = false,
                filledCount = filledCount,
                totalCount = totalCount
            )
        }

        // Persist skip state
        if (allNewSkips.isNotEmpty()) {
            intakeRepository.markFieldsSkipped(formId, allNewSkips.toList())
        }

        Log.d(TAG, "Saved ${field.id}=$value. Bonus fills: ${alsoFills.size}. New skips: ${allNewSkips.size}")

        // Continue to next field
        advanceToNextField()
    }

    /**
     * Compute which fields to skip based on a field_id + value pair.
     * Handles both exact-match rules and the multi-select "None" case.
     */
    private fun computeSkips(fieldId: String, value: String): Set<String> {
        val rules = SKIP_RULES[fieldId] ?: return emptySet()
        val result = mutableSetOf<String>()
        rules[value]?.let { result.addAll(it) }
        // multi-select: if "None" is the only or first selection
        if (fieldId == "medical_conditions" && (value == "None" || value.startsWith("None,"))) {
            rules["None"]?.let { result.addAll(it) }
        }
        return result
    }

    /** Flag a field for staff and skip it after too many failed clarification attempts. */
    private suspend fun escalateField(field: FormField) {
        intakeRepository.markFieldsSkipped(formId, listOf(field.id))
        _state.update {
            it.copy(
                skippedFieldIds = it.skippedFieldIds + field.id,
                isLoadingResponse = false,
                chatMessages = it.chatMessages + ChatMessage(
                    text = "No problem — a staff member can help with that part. Let's keep going.",
                    isFromUser = false,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
        Log.w(TAG, "Escalated field ${field.id} after $MAX_CLARIFICATIONS failed attempts")
        advanceToNextField()
    }

    // ─── Multi-Select Helper ──────────────────────────────────────────────────

    /** Updates in-memory multi-select state without triggering an AI parse call. */
    fun updateMultiSelectField(fieldId: String, value: String) {
        _state.update {
            it.copy(
                fields = it.fields.map { f ->
                    if (f.id == fieldId) f.copy(value = value.ifBlank { null }) else f
                },
                currentAskingField = it.currentAskingField?.let { caf ->
                    if (caf.id == fieldId) caf.copy(value = value.ifBlank { null }) else caf
                }
            )
        }
    }

    // ─── Public Actions ───────────────────────────────────────────────────────

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun completeIntake() {
        viewModelScope.launch {
            try {
                formRepository.updateFormStatus(formId, FormStatus.COMPLETED)
                _state.update { it.copy(isComplete = true) }
            } catch (e: Exception) {
                Log.e(TAG, "Error completing intake", e)
                _state.update { it.copy(error = "Failed to save: ${e.message}") }
            }
        }
    }
}

data class GuidedIntakeState(
    val form: Form? = null,
    val fields: List<FormField> = emptyList(),
    val intakeFlow: FormIntakeFlow? = null,
    val skippedFieldIds: Set<String> = emptySet(),
    val currentAskingField: FormField? = null,
    val clarificationCount: Int = 0,
    val chatMessages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingResponse: Boolean = false,
    val error: String? = null,
    val isComplete: Boolean = false,
    val userLanguage: String = "en",
    val guardianMode: Boolean = false,
    val filledCount: Int = 0,
    val totalCount: Int = 0
)
