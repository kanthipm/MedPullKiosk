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
        const val MEDICAID_RENEWAL_ID = "medicaid_renewal_intake"
        private const val SCHEMA_FILE = "schemas/coastal_gateway_intake.json"
        private const val SCHEMA_FILE_MEDICAID = "schemas/medicaid_renewal_intake.json"
        const val DEMO_USER_ID = "demo_user"
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
         * Consent fields that are shown together in a single batch UI instead of
         * one-by-one chat questions. All four must be answered before advancing.
         */
        val CONSENT_GROUP_FIELD_IDS = setOf(
            "hipaa_consent"
        )

        /**
         * Deterministic skip rules derived from schema skip_if blocks.
         * fieldId → triggerValue → list of fields to skip.
         *
         * The ViewModel applies these the moment a field value is saved, so the AI
         * never needs to decide whether to ask insurance questions to an uninsured patient.
         */
        val SKIP_RULES: Map<String, Map<String, List<String>>> = mapOf(
            // Coastal Gateway
            "has_insurance" to mapOf(
                "No" to listOf("primary_insurance_provider")
            ),
            "allergies_any" to mapOf(
                "No" to listOf("allergies_list")
            ),
            // Medicaid Renewal
            "has_other_insurance" to mapOf(
                "No" to listOf("other_insurance_type")
            ),
            "authorized_representative" to mapOf(
                "No, I am completing this myself" to listOf("representative_name", "representative_relationship")
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

        /** Parse the Medicaid Renewal JSON schema into a Form + field list. */
        fun loadMedicaidRenewalForm(context: Context): Form {
            val json = context.assets.open(SCHEMA_FILE_MEDICAID).bufferedReader().readText()
            val root = JSONObject(json)
            val formName = root.optString("form_name", "Medicaid Coverage Renewal")
            val sections = root.optJSONArray("sections") ?: return Form(
                id = MEDICAID_RENEWAL_ID, userId = "builtin",
                fileName = formName, originalFileUri = "",
                status = FormStatus.READY, fields = emptyList()
            )

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
                        formId = MEDICAID_RENEWAL_ID,
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
                id = MEDICAID_RENEWAL_ID,
                userId = "builtin",
                fileName = formName,
                originalFileUri = "",
                status = FormStatus.READY,
                fields = fields
            )
        }
    }

    private val formId: String = savedStateHandle.get<String>("formId") ?: ""

    private val _state = MutableStateFlow(GuidedIntakeState())
    val state: StateFlow<GuidedIntakeState> = _state.asStateFlow()

    init {
        loadForm()
    }

    // ─── Form Loading ─────────────────────────────────────────────────────────

    /**
     * Overlay previously-saved field values from the DB onto a freshly-parsed schema form.
     * Prevents saveForm(REPLACE) from wiping mid-session progress on reload.
     */
    private suspend fun mergeWithSavedValues(schema: Form): Form {
        val saved = try {
            formRepository.getFormById(schema.id)?.fields
                ?.filter { !it.value.isNullOrBlank() }
                ?.associateBy { it.id }
        } catch (e: Exception) { null }
        if (saved.isNullOrEmpty()) return schema
        return schema.copy(
            fields = schema.fields.map { f ->
                saved[f.id]?.let { s -> f.copy(value = s.value) } ?: f
            }
        )
    }

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
                    val schema = loadCoastalGatewayForm(appContext)
                    val merged = mergeWithSavedValues(schema)
                    formRepository.saveForm(merged)
                    initFormState(merged)
                } else if (formId == MEDICAID_RENEWAL_ID) {
                    val schema = loadMedicaidRenewalForm(appContext)
                    val merged = mergeWithSavedValues(schema)
                    formRepository.saveForm(merged)
                    initFormState(merged)
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

        // Apply cross-form demographic prefill from patient cache.
        // Use "demo_user" when no authenticated user (demo mode bypass).
        val userId = authRepository.getCurrentUserId()?.takeIf { it.isNotBlank() } ?: DEMO_USER_ID
        val cache = formRepository.getPatientCache(userId)

        // Identify which blank fields (not restored from DB) get values from the cache.
        // These are the ones that need patient confirmation.
        val newlyCacheFilledFields: List<FormField> = if (cache != null) {
            withLanguage.mapNotNull { f ->
                val cachedValue = cache.valueForFieldId(f.id)
                if (f.id in PatientCacheEntity.DEMOGRAPHIC_FIELD_IDS &&
                    !cachedValue.isNullOrBlank() &&
                    f.value.isNullOrBlank() // was blank before cache — not from a restored session
                ) f.copy(value = cachedValue) else null
            }
        } else emptyList()

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

        _state.update {
            it.copy(
                form = form,
                fields = withCache,
                intakeFlow = flow,
                skippedFieldIds = restoredSkips,
                chatMessages = emptyList(),
                userLanguage = language,
                isLoading = false,
                filledCount = filledCount,
                totalCount = totalCount,
                pendingConfirmFields = newlyCacheFilledFields.takeIf { it.isNotEmpty() }
            )
        }

        // If there are pre-filled fields to confirm, pause and wait for user confirmation.
        // Otherwise begin the intake immediately.
        if (newlyCacheFilledFields.isEmpty()) {
            advanceToNextField()
        }
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

        // If the next unanswered field is a consent field, surface the entire consent
        // group as a batch rather than asking them one-by-one.
        if (next != null && next.id in CONSENT_GROUP_FIELD_IDS) {
            val pendingConsentFields = state.fields.filter { f ->
                f.id in CONSENT_GROUP_FIELD_IDS && f.value.isNullOrBlank() && f.id !in allSkipped
            }
            if (pendingConsentFields.isNotEmpty()) {
                _state.update { it.copy(consentBatchFields = pendingConsentFields, isLoadingResponse = false) }
                return
            }
        }

        when {
            next == null -> {
                // All fields addressed — save cache then go to review
                Log.d(TAG, "All fields addressed — transitioning to review")
                persistPatientCache()
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
                        timestamp = System.currentTimeMillis(),
                        isClarification = looksLikeQuestion(message)
                    ),
                    isLoadingResponse = true,
                    error = null
                )
            }

            try {
                // If the patient is asking a question rather than providing an answer,
                // route to the clarification handler instead of answer parsing.
                if (looksLikeQuestion(message)) {
                    val answer = engine.answerClarification(
                        question = message,
                        field = targetField,
                        language = state.userLanguage
                    )
                    _state.update {
                        it.copy(
                            isLoadingResponse = false,
                            chatMessages = it.chatMessages + ChatMessage(
                                text = answer,
                                isFromUser = false,
                                timestamp = System.currentTimeMillis(),
                                isClarification = true  // stays in sidebar only
                            )
                        )
                    }
                    return@launch
                }

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

    /** Heuristic: does this look like a question rather than an answer? */
    private fun looksLikeQuestion(text: String): Boolean {
        val trimmed = text.trim().lowercase()
        if (trimmed.endsWith("?")) return true
        val questionStarters = listOf("what", "why", "how", "when", "where", "who", "which",
            "do i", "do you", "can i", "can you", "should i", "is this", "what does",
            "what is", "why do", "why does", "i don't understand", "i dont understand",
            "what do you mean", "explain", "help me understand", "not sure what")
        return questionStarters.any { trimmed.startsWith(it) }
    }

    /** Mark the form complete immediately so the patient can review what they've filled so far. */
    fun skipToReview() {
        persistPatientCache()
        _state.update { it.copy(isComplete = true) }
    }

    /**
     * Patient confirmed pre-filled demographic info — accept all cached values,
     * dismiss the confirm panel, and start the intake at the first unanswered field.
     */
    fun confirmPrefill() {
        _state.update { it.copy(pendingConfirmFields = null) }
        advanceToNextField()
    }

    /**
     * Patient wants to re-enter their info from scratch — clear the pre-filled values
     * for demographic fields and start the intake from the beginning.
     */
    fun dismissPrefillAndStartFresh() {
        val clearedFields = _state.value.fields.map { f ->
            if (f.id in PatientCacheEntity.DEMOGRAPHIC_FIELD_IDS) f.copy(value = null) else f
        }
        _state.update { it.copy(pendingConfirmFields = null, fields = clearedFields) }
        viewModelScope.launch {
            clearedFields.filter { it.id in PatientCacheEntity.DEMOGRAPHIC_FIELD_IDS }.forEach { f ->
                formRepository.updateFieldValue(f.id, null)
            }
            advanceToNextField()
        }
    }

    /** Update a single field's value while the patient is editing in the confirm panel. */
    fun updateConfirmField(fieldId: String, newValue: String) {
        _state.update { s ->
            s.copy(
                pendingConfirmFields = s.pendingConfirmFields?.map { f ->
                    if (f.id == fieldId) f.copy(value = newValue) else f
                },
                fields = s.fields.map { f ->
                    if (f.id == fieldId) f.copy(value = newValue.takeIf { it.isNotBlank() }) else f
                }
            )
        }
    }

    /** Save key demographics to the patient cache so the next form can pre-fill. */
    private fun persistPatientCache() {
        viewModelScope.launch {
            try {
                val userId = authRepository.getCurrentUserId()?.takeIf { it.isNotBlank() } ?: DEMO_USER_ID
                formRepository.savePatientCache(userId, _state.value.fields)
            } catch (e: Exception) {
                Log.w(TAG, "Could not save patient cache", e)
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

    // ─── Consent Batch Submit ─────────────────────────────────────────────────

    /**
     * Save all consent field answers at once and advance past the consent section.
     * [answers] maps fieldId → selected option string.
     */
    fun submitConsentBatch(answers: Map<String, String>) {
        viewModelScope.launch {
            _state.update { it.copy(consentBatchFields = null, isLoadingResponse = true) }

            var updatedFields = _state.value.fields
            val allNewSkips = mutableSetOf<String>()

            answers.forEach { (fieldId, value) ->
                try {
                    formRepository.updateFieldValue(fieldId, value)
                    intakeRepository.markFieldsInferred(formId, listOf(fieldId))
                } catch (e: Exception) {
                    Log.w(TAG, "Could not persist consent field $fieldId", e)
                }
                updatedFields = updatedFields.map { f -> if (f.id == fieldId) f.copy(value = value) else f }
                allNewSkips += computeSkips(fieldId, value)
            }

            val updatedSkipped = _state.value.skippedFieldIds + allNewSkips
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
                    filledCount = filledCount,
                    totalCount = totalCount,
                    chatMessages = it.chatMessages + ChatMessage(
                        text = "Thank you — your consent selections have been recorded.",
                        isFromUser = false,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }

            if (allNewSkips.isNotEmpty()) {
                intakeRepository.markFieldsSkipped(formId, allNewSkips.toList())
            }

            Log.d(TAG, "Consent batch submitted: ${answers.size} fields. New skips: ${allNewSkips.size}")
            advanceToNextField()
        }
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
    val totalCount: Int = 0,
    /** Non-null when the consent batch UI should be shown instead of chat Q&A. */
    val consentBatchFields: List<FormField>? = null,
    /** Non-null when pre-filled fields from a previous visit need patient confirmation. */
    val pendingConfirmFields: List<FormField>? = null
)
