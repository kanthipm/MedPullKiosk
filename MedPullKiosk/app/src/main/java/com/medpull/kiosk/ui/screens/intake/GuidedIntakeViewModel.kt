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
import com.medpull.kiosk.R
import com.medpull.kiosk.ui.screens.ai.ChatMessage
import com.medpull.kiosk.utils.AppStrings
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
    private val appStrings: AppStrings,
    @ApplicationContext private val appContext: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "GuidedIntakeViewModel"
        const val COASTAL_GATEWAY_ID = "coastal_gateway_intake"
        const val MEDICAID_RENEWAL_ID = "medicaid_renewal_intake"
        const val SLIDING_FEE_ID = "sliding_fee_intake"
        // All built-in conversational forms live here as declarative JSON schemas.
        // Adding a new form is just dropping a *.json file in this assets folder —
        // it is auto-discovered by its "form_id" (or filename) at runtime. No new
        // Kotlin code, skip rules, or consent wiring required.
        private const val SCHEMAS_DIR = "schemas"
        const val DEMO_USER_ID = "demo_user"
        private const val CONFIDENCE_THRESHOLD = 0.75f
        private const val MAX_CLARIFICATIONS = 2

        /**
         * Result of parsing a declarative form schema: the form + field list plus
         * all the behavior metadata the intake state machine needs, derived
         * entirely from the JSON (no per-form Kotlin).
         */
        data class LoadedSchema(
            val form: Form,
            /** triggerFieldId → triggerValue → list of field ids to skip. */
            val skipRules: Map<String, Map<String, List<String>>>,
            /** Consent fields surfaced together in the batch panel. */
            val consentGroupFieldIds: Set<String>,
            /** Fields handled by the review step, not asked during the conversation. */
            val skipDuringIntakeIds: Set<String>
        )

        /**
         * Auto-discover every built-in schema: formId → asset path.
         *
         * Lists every *.json under assets/schemas and keys it by its "form_id"
         * (falling back to the filename). This is what makes the conversational
         * interface work on ANY form — adding one is just dropping a JSON file.
         */
        fun discoverSchemas(context: Context): Map<String, String> {
            val files = context.assets.list(SCHEMAS_DIR)
                ?.filter { it.endsWith(".json") } ?: emptyList()
            val map = LinkedHashMap<String, String>()
            for (file in files) {
                val path = "$SCHEMAS_DIR/$file"
                val id = try {
                    JSONObject(context.assets.open(path).bufferedReader().readText())
                        .optString("form_id")
                        .ifBlank { file.removeSuffix(".json") }
                } catch (e: Exception) {
                    file.removeSuffix(".json")
                }
                map[id] = path
            }
            return map
        }

        /** Map a schema "type" string to a FieldType. */
        private fun fieldTypeOf(type: String): FieldType = when (type) {
            "date" -> FieldType.DATE
            "checkbox" -> FieldType.CHECKBOX
            "radio" -> FieldType.RADIO
            "dropdown" -> FieldType.DROPDOWN
            "multi_select" -> FieldType.MULTI_SELECT
            "signature" -> FieldType.SIGNATURE
            "static_label" -> FieldType.STATIC_LABEL
            "number", "phone", "zip", "email" -> FieldType.NUMBER
            else -> FieldType.TEXT
        }

        /**
         * Generic parser for any declarative form schema. Replaces the previous
         * per-form loaders — the schema fully describes fields, skip logic,
         * consent batching, and which fields to defer to the review step.
         */
        fun loadSchemaForm(context: Context, formId: String, assetPath: String): LoadedSchema {
            val root = JSONObject(context.assets.open(assetPath).bufferedReader().readText())
            val formName = root.optString("form_name", formId)
            val sections = root.optJSONArray("sections")

            val fields = mutableListOf<FormField>()
            if (sections != null) {
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
                            formId = formId,
                            fieldName = field.optString("label"),
                            originalText = field.optString("label"),
                            translatedText = field.optString("label"),
                            fieldType = fieldTypeOf(field.optString("type")),
                            required = field.optBoolean("required", false),
                            options = opts,
                            description = field.optString("ai_note", "").ifBlank { null }
                        )
                    }
                }
            }

            // skip_rules: [{ when_field, equals, skip: [...] }] → nested map
            val skipRules = mutableMapOf<String, MutableMap<String, MutableList<String>>>()
            root.optJSONArray("skip_rules")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val rule = arr.getJSONObject(i)
                    val whenField = rule.optString("when_field")
                    val equals = rule.optString("equals")
                    val skip = rule.optJSONArray("skip")
                        ?.let { s -> (0 until s.length()).map { s.getString(it) } } ?: emptyList()
                    if (whenField.isNotBlank() && equals.isNotBlank()) {
                        skipRules.getOrPut(whenField) { mutableMapOf() }
                            .getOrPut(equals) { mutableListOf() }
                            .addAll(skip)
                    }
                }
            }

            fun stringSet(key: String): Set<String> =
                root.optJSONArray(key)
                    ?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
                    ?: emptySet()

            return LoadedSchema(
                form = Form(
                    id = formId,
                    userId = "builtin",
                    fileName = formName,
                    originalFileUri = "",
                    status = FormStatus.READY,
                    fields = fields
                ),
                skipRules = skipRules,
                consentGroupFieldIds = stringSet("consent_batch_fields"),
                skipDuringIntakeIds = stringSet("skip_during_intake")
            )
        }
    }

    private val formId: String = savedStateHandle.get<String>("formId") ?: ""
    // True when navigating back from the review screen — open last answered field instead of going to review.
    private val editLast: Boolean = savedStateHandle.get<Boolean>("editLast") ?: false
    private var editLastConsumed = false

    // Behavior metadata for the active form, loaded from its schema (empty for
    // ad-hoc / uploaded forms that have no schema).
    private var skipRules: Map<String, Map<String, List<String>>> = emptyMap()
    private var consentGroupFieldIds: Set<String> = emptySet()
    private var skipDuringIntakeIds: Set<String> = emptySet()

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

                // Auto-discover a declarative schema for this formId. Any built-in
                // form (sliding fee, coastal gateway, medicaid, or a future one)
                // is loaded through the same generic path — no per-form code.
                val schemaPath = try {
                    discoverSchemas(appContext)[formId]
                } catch (e: Exception) {
                    Log.w(TAG, "Schema discovery failed", e); null
                }

                if (schemaPath != null) {
                    val loaded = loadSchemaForm(appContext, formId, schemaPath)
                    skipRules = loaded.skipRules
                    consentGroupFieldIds = loaded.consentGroupFieldIds
                    skipDuringIntakeIds = loaded.skipDuringIntakeIds
                    val merged = mergeWithSavedValues(loaded.form)
                    formRepository.saveForm(merged)
                    initFormState(merged)
                } else {
                    // Ad-hoc / uploaded (Textract) form straight from the DB. It has
                    // no schema metadata, but the conversational engine still drives
                    // it field-by-field generically.
                    skipRules = emptyMap()
                    consentGroupFieldIds = emptySet()
                    skipDuringIntakeIds = emptySet()
                    formRepository.getFormByIdFlow(formId).collect { form ->
                        if (form != null) initFormState(form)
                        else _state.update { it.copy(error = appStrings.get(R.string.form_not_found), isLoading = false) }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading form", e)
                _state.update { it.copy(error = appStrings.get(R.string.err_failed_load_form, e.message ?: ""), isLoading = false) }
            }
        }
    }

    private suspend fun initFormState(form: Form) {
        val flow = intakeRepository.getOrCreateFlow(form.id)
        val language = localeManager.getCurrentLanguage(appContext)

        // Pre-fill preferred_language from app locale
        val languageLabel = when (language) {
            "es" -> "Español"; "zh" -> "中文"; "fr" -> "Français"
            "ja" -> "日本語"; "pt" -> "Português"
            "ar" -> "العربية"; "ru" -> "Русский"; else -> "English"
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

        val allSkipped = restoredSkips + skipDuringIntakeIds
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
        val allSkipped = state.skippedFieldIds + skipDuringIntakeIds

        val next = state.fields.firstOrNull { f ->
            f.id !in allSkipped && f.value.isNullOrBlank()
        }

        // If the next unanswered field is a consent field, surface the entire consent
        // group as a batch rather than asking them one-by-one.
        if (next != null && next.id in consentGroupFieldIds) {
            val pendingConsentFields = state.fields.filter { f ->
                f.id in consentGroupFieldIds && f.value.isNullOrBlank() && f.id !in allSkipped
            }
            if (pendingConsentFields.isNotEmpty()) {
                _state.update { it.copy(consentBatchFields = pendingConsentFields, isLoadingResponse = false) }
                return
            }
        }

        when {
            next == null -> {
                if (editLast && !editLastConsumed) {
                    // Returning from review screen — re-open last answered field for editing
                    editLastConsumed = true
                    goToPreviousField()
                } else {
                    // All fields addressed — save cache then go to review
                    Log.d(TAG, "All fields addressed — transitioning to review")
                    persistPatientCache()
                    _state.update { it.copy(isComplete = true, isLoadingResponse = false) }
                }
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

    // ─── Back Navigation ─────────────────────────────────────────────────────

    /**
     * Returns true if there is a previous answered field to navigate back to.
     * Used by the screen to decide whether the back button exits or goes to prev question.
     */
    fun hasPreviousField(): Boolean {
        val s = _state.value
        val allSkipped = s.skippedFieldIds + skipDuringIntakeIds
        val answered = s.fields.filter { f ->
            f.id !in allSkipped && f.fieldType != FieldType.STATIC_LABEL &&
            !f.value.isNullOrBlank() && f.value != "delivered"
        }
        return if (s.currentAskingField != null) {
            val idx = s.fields.indexOfFirst { it.id == s.currentAskingField.id }
            answered.any { f -> s.fields.indexOfFirst { it.id == f.id } < idx }
        } else {
            answered.isNotEmpty()
        }
    }

    /**
     * Navigate back to the previous answered field without erasing its value.
     * The existing answer is pre-filled in the input so the user can keep or change it.
     * If the user submits a new answer, it overwrites; if they submit the same, it's a no-op.
     */
    fun goToPreviousField() {
        val s = _state.value
        val allSkipped = s.skippedFieldIds + skipDuringIntakeIds
        val answered = s.fields.filter { f ->
            f.id !in allSkipped && f.fieldType != FieldType.STATIC_LABEL &&
            !f.value.isNullOrBlank() && f.value != "delivered"
        }
        val target = if (s.currentAskingField != null) {
            val idx = s.fields.indexOfFirst { it.id == s.currentAskingField.id }
            answered.lastOrNull { f -> s.fields.indexOfFirst { it.id == f.id } < idx }
        } else {
            answered.lastOrNull()
        } ?: return

        // Keep the existing value — just re-ask the question with a fresh chat thread.
        _state.update { it.copy(isComplete = false, chatMessages = emptyList()) }
        startAskingField(target)
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
                            appStrings.get(R.string.err_clarify)
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
                                    text = appStrings.get(R.string.err_try_again),
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
                        error = appStrings.get(R.string.err_process_answer)
                    )
                }
            }
        }
    }

    /**
     * Send a message from the chat sidebar.
     * Chat is ALWAYS treated as a clarifying question — it never fills a form field.
     * This keeps the chat panel as a pure help/guidance channel.
     */
    fun sendChatMessage(message: String) {
        if (message.isBlank()) return
        val field = _state.value.currentAskingField ?: return
        val lang = _state.value.userLanguage

        viewModelScope.launch {
            _state.update {
                it.copy(
                    chatMessages = it.chatMessages + ChatMessage(
                        text = message,
                        isFromUser = true,
                        timestamp = System.currentTimeMillis(),
                        isClarification = true
                    ),
                    isLoadingResponse = true
                )
            }
            val answer = engine.answerClarification(question = message, field = field, language = lang)
            _state.update {
                it.copy(
                    isLoadingResponse = false,
                    chatMessages = it.chatMessages + ChatMessage(
                        text = answer,
                        isFromUser = false,
                        timestamp = System.currentTimeMillis(),
                        isClarification = true
                    )
                )
            }
        }
    }

    /**
     * Submit a handwritten signature bitmap for the current signature field.
     * Saves the bitmap to internal storage and advances past the field —
     * no AI parsing needed for signatures.
     */
    fun submitSignature(bitmap: android.graphics.Bitmap) {
        val field = _state.value.currentAskingField ?: return
        viewModelScope.launch {
            try {
                val dir = java.io.File(appContext.filesDir, "signatures").apply { mkdirs() }
                val file = java.io.File(dir, "sig_${field.id}_${System.currentTimeMillis()}.png")
                file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                saveFieldAndAdvance(field, "signature:${file.absolutePath}", emptyList())
            } catch (e: Exception) {
                Log.w(TAG, "Signature save failed", e)
                // Fall back: mark field as signed with a text marker
                saveFieldAndAdvance(field, "✓ Signed", emptyList())
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
        val skipSet = updatedSkipped + skipDuringIntakeIds
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
        val rules = skipRules[fieldId] ?: return emptySet()
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
                    text = appStrings.get(R.string.msg_staff_help),
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
            val skipSet = updatedSkipped + skipDuringIntakeIds
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
                        text = appStrings.get(R.string.msg_consent_recorded),
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
                _state.update { it.copy(error = appStrings.get(R.string.err_failed_save, e.message ?: "")) }
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
