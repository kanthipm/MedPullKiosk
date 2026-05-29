package com.medpull.kiosk.ui.screens.intake

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.medpull.kiosk.R
import com.medpull.kiosk.data.models.FieldType
import com.medpull.kiosk.data.models.FormField
import com.medpull.kiosk.ui.screens.ai.ChatMessage
import com.medpull.kiosk.ui.screens.ai.HandwritingInput
import com.medpull.kiosk.ui.screens.ai.SignatureCapture
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

/**
 * Phases of the voice-first answer flow used by the sliding-fee intake.
 *
 * Speaking  → the question is being read aloud (mic off)
 * Listening → mic is open; the arch animates and the live transcript streams in
 * Review    → speech ended; show Accept / Re-record / Edit
 * Editing   → keyboard is open to edit the transcript (or type from scratch)
 * Prompt    → idle/ready; tap the arch to start speaking
 */
private enum class VoicePhase { Idle, Speaking, Listening, Review, Editing, Prompt }

/**
 * Typeform-style guided intake screen.
 *
 * One question at a time, vertically centered. The answer input lives directly
 * below the question text — not in a separate bottom bar. Slide-up animation
 * between questions. "press Enter ↵" affordance. Thin progress line at top.
 *
 * Sliding-fee free-text questions use a dedicated voice-first layout: the
 * question is spoken aloud, an animated blue arch reacts to the microphone, the
 * spoken answer streams in beneath the arch, and the patient confirms with
 * Accept / Re-record / Edit or falls back to typing.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GuidedIntakeScreen(
    onNavigateBack: () -> Unit,
    onComplete: () -> Unit,
    viewModel: GuidedIntakeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var messageText by remember { mutableStateOf("") }
    var chatText by remember { mutableStateOf("") }   // separate from main answer field
    val focusRequester = remember { FocusRequester() }
    var chatPanelOpen by remember { mutableStateOf(false) }
    val chatListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Auto-scroll chat sidebar to latest message
    LaunchedEffect(state.chatMessages.size) {
        if (state.chatMessages.isNotEmpty() && chatPanelOpen) {
            coroutineScope.launch {
                chatListState.animateScrollToItem(state.chatMessages.size - 1)
            }
        }
    }

    val isSignatureField = state.currentAskingField?.fieldType == FieldType.SIGNATURE
    var showHandwriting by remember { mutableStateOf(false) }

    LaunchedEffect(state.currentAskingField?.id) {
        showHandwriting = isSignatureField
        val f = state.currentAskingField
        // Pre-fill the input with the existing answer when navigating back to a field
        val existingValue = f?.value?.takeIf { it.isNotBlank() && it != "delivered" }
        messageText = existingValue ?: ""
        // Auto-focus text input when question changes (non-chip, non-signature fields)
        if (f != null && f.options.isEmpty() && !isSignatureField) {
            try { focusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    // Refocus the main answer field whenever the chat panel is closed
    LaunchedEffect(chatPanelOpen) {
        if (!chatPanelOpen) {
            val f = state.currentAskingField
            if (f != null && f.options.isEmpty() && f.fieldType != FieldType.SIGNATURE) {
                try { focusRequester.requestFocus() } catch (_: Exception) {}
            }
        }
    }

    val currentQuestion: ChatMessage? = remember(state.chatMessages) {
        state.chatMessages.lastOrNull { !it.isFromUser && !it.isClarification }
    }

    // ── Voice-first mode (sliding fee, free-text questions only) ────────────────
    // Voice-first replaces the typed input with: auto-spoken question, an animated
    // blue arch that reacts to the mic, a live blue transcript, and an
    // accept / re-record / edit confirmation step. Choice-chip, multi-select and
    // signature questions keep their specialized inputs (but are still spoken).
    val isSlidingFee = state.form?.id == GuidedIntakeViewModel.SLIDING_FEE_ID
    val activeField = state.currentAskingField
    val isFreeTextField = activeField == null ||
        (activeField.options.isEmpty() &&
            activeField.fieldType != FieldType.SIGNATURE &&
            activeField.fieldType != FieldType.MULTI_SELECT)
    val voiceFirstActive = isSlidingFee && isFreeTextField &&
        currentQuestion != null && !state.isLoadingResponse &&
        state.consentBatchFields == null && state.pendingConfirmFields == null

    var voicePhase by remember { mutableStateOf(VoicePhase.Idle) }
    var liveTranscript by remember { mutableStateOf("") }
    var finalTranscript by remember { mutableStateOf("") }
    var micAmplitude by remember { mutableFloatStateOf(0f) }
    var voiceEditText by remember { mutableStateOf("") }
    val voiceEditFocus = remember { FocusRequester() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    // ── Speech-to-text ────────────────────────────────────────────────────────
    val speechAvailable = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    var isListening by remember { mutableStateOf(false) }
    val speechLocale = remember(state.userLanguage) {
        when (state.userLanguage) {
            "es" -> "es-ES"; "zh" -> "zh-CN"; "fr" -> "fr-FR"
            "ja" -> "ja-JP"; "pt" -> "pt-BR"
            "ar" -> "ar-SA"; "ru" -> "ru-RU"; else -> "en-US"
        }
    }

    // Mode-aware callbacks read by the recognition listener.
    val voiceActiveRef = rememberUpdatedState(voiceFirstActive)
    val onRms = rememberUpdatedState<(Float) -> Unit> { db ->
        if (voiceActiveRef.value) micAmplitude = ((db + 2f) / 12f).coerceIn(0f, 1f)
    }
    val onPartial = rememberUpdatedState<(String) -> Unit> { text ->
        if (voiceActiveRef.value && text.isNotBlank()) liveTranscript = text
    }
    val onFinal = rememberUpdatedState<(String) -> Unit> { text ->
        if (voiceActiveRef.value) {
            if (text.isNotBlank()) {
                liveTranscript = text
                finalTranscript = text
                voicePhase = VoicePhase.Review
            } else {
                voicePhase = VoicePhase.Prompt
            }
        } else if (text.isNotBlank()) {
            messageText = if (messageText.isBlank()) text else "$messageText $text"
        }
        isListening = false
        micAmplitude = 0f
    }
    val onSpeechError = rememberUpdatedState<() -> Unit> {
        isListening = false
        micAmplitude = 0f
        if (voiceActiveRef.value && finalTranscript.isBlank()) voicePhase = VoicePhase.Prompt
    }

    // IMPORTANT: a single SpeechRecognizer instance becomes unreliable after a
    // timeout/no-match error — the next startListening() can silently no-op or
    // report ERROR_RECOGNIZER_BUSY. That's why "tap to speak" stopped working
    // after the first timeout. We therefore create a FRESH recognizer for every
    // listening session and tear the previous one down first.
    val recognizerHolder = remember { mutableStateOf<SpeechRecognizer?>(null) }
    val buildListener: () -> RecognitionListener = {
        object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                onFinal.value(text ?: "")
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) onPartial.value(text)
            }
            override fun onRmsChanged(rmsdB: Float) { onRms.value(rmsdB) }
            override fun onError(error: Int) { onSpeechError.value() }
            override fun onReadyForSpeech(params: Bundle?) {
                if (voiceActiveRef.value) voicePhase = VoicePhase.Listening
            }
            override fun onBeginningOfSpeech() {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }
    val startListening: () -> Unit = start@{
        if (!speechAvailable) return@start
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, speechLocale)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        // Tear down any prior session and start with a clean recognizer.
        recognizerHolder.value?.let { old ->
            try { old.cancel(); old.destroy() } catch (_: Exception) {}
        }
        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        sr.setRecognitionListener(buildListener())
        recognizerHolder.value = sr
        try {
            sr.startListening(intent)
            isListening = true
        } catch (_: Exception) {
            isListening = false
            if (voiceActiveRef.value) voicePhase = VoicePhase.Prompt
        }
    }
    val cancelListening: () -> Unit = {
        recognizerHolder.value?.let { try { it.cancel() } catch (_: Exception) {} }
        isListening = false
        micAmplitude = 0f
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (voiceActiveRef.value) {
                liveTranscript = ""; finalTranscript = ""
                voicePhase = VoicePhase.Listening
            }
            startListening()
        } else if (voiceActiveRef.value) {
            voicePhase = VoicePhase.Prompt
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            recognizerHolder.value?.let { try { it.destroy() } catch (_: Exception) {} }
            recognizerHolder.value = null
        }
    }

    // ── Text-to-speech ────────────────────────────────────────────────────────
    var ttsReady by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    val tts = remember {
        TextToSpeech(context) { status -> ttsReady = status == TextToSpeech.SUCCESS }
    }
    DisposableEffect(Unit) { onDispose { tts.shutdown() } }
    // Whether, once a question finishes being read aloud, the mic should start
    // automatically (voice-first + permission already granted).
    val canAutoListenRef = rememberUpdatedState(
        voiceFirstActive && speechAvailable &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    )
    val startListeningRef = rememberUpdatedState(startListening)
    LaunchedEffect(state.userLanguage, ttsReady) {
        if (ttsReady) {
            val locale = when (state.userLanguage) {
                "es" -> Locale("es"); "zh" -> Locale.CHINESE
                "fr" -> Locale.FRENCH; "ja" -> Locale.JAPANESE
                "pt" -> Locale("pt"); "ar" -> Locale("ar")
                "ru" -> Locale("ru")
                else -> Locale.ENGLISH
            }
            tts.language = locale
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    mainHandler.post { isSpeaking = true }
                }
                override fun onDone(utteranceId: String?) {
                    mainHandler.post {
                        isSpeaking = false
                        // When the question finishes, auto-open the mic for hands-free use.
                        if (utteranceId == "question" && voicePhase == VoicePhase.Speaking) {
                            if (canAutoListenRef.value) {
                                liveTranscript = ""; finalTranscript = ""
                                voicePhase = VoicePhase.Listening
                                startListeningRef.value()
                            } else if (voiceActiveRef.value) {
                                voicePhase = VoicePhase.Prompt
                            }
                        }
                    }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    mainHandler.post { isSpeaking = false }
                }
            })
        }
    }

    // Auto-speak each new question; reset the voice flow for the new field.
    LaunchedEffect(currentQuestion?.text, voiceFirstActive, ttsReady) {
        if (voiceFirstActive && currentQuestion != null) {
            liveTranscript = ""
            finalTranscript = ""
            voiceEditText = ""
            micAmplitude = 0f
            val q = currentQuestion.text
                .replace(Regex("\\s*\\([^)]{0,80}\\)\\s*$"), "").trim()
            if (ttsReady && q.isNotBlank()) {
                voicePhase = VoicePhase.Speaking
                tts.speak(q, TextToSpeech.QUEUE_FLUSH, null, "question")
            } else {
                voicePhase = VoicePhase.Prompt
            }
        }
    }

    // Auto-speak the choice-chip / signature questions too (sliding fee), so every
    // question is read aloud — these keep their specialized inputs (no mic flow).
    LaunchedEffect(currentQuestion?.text, voiceFirstActive, ttsReady, state.consentBatchFields) {
        if (isSlidingFee && !voiceFirstActive && ttsReady && currentQuestion != null &&
            state.consentBatchFields == null && state.pendingConfirmFields == null &&
            !state.isLoadingResponse
        ) {
            val q = currentQuestion.text
                .replace(Regex("\\s*\\([^)]{0,80}\\)\\s*$"), "").trim()
            if (q.isNotBlank()) tts.speak(q, TextToSpeech.QUEUE_FLUSH, null, "q")
        }
    }

    // Request mic permission up front so hands-free auto-listen can work.
    LaunchedEffect(voiceFirstActive) {
        if (voiceFirstActive && speechAvailable &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Bring up the keyboard whenever we enter the edit/type phase.
    LaunchedEffect(voicePhase) {
        if (voicePhase == VoicePhase.Editing) {
            try { voiceEditFocus.requestFocus() } catch (_: Exception) {}
        }
    }

    // ── Voice action handlers ───────────────────────────────────────────────────
    val voiceStartListening: () -> Unit = {
        if (speechAvailable) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                if (isSpeaking) { tts.stop(); isSpeaking = false }
                liveTranscript = ""
                voicePhase = VoicePhase.Listening
                startListening()
            } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
    val onVoiceAccept: () -> Unit = {
        val t = finalTranscript.trim()
        if (t.isNotBlank() && !state.isLoadingResponse) viewModel.sendMessage(t)
    }
    val onVoiceRerecord: () -> Unit = {
        finalTranscript = ""
        liveTranscript = ""
        voiceStartListening()
    }
    val onVoiceEdit: () -> Unit = {
        if (isListening) cancelListening()
        voiceEditText = finalTranscript
        voicePhase = VoicePhase.Editing
    }
    val onVoiceStartTyping: () -> Unit = {
        if (isListening) cancelListening()
        if (isSpeaking) { tts.stop(); isSpeaking = false }
        voiceEditText = ""
        voicePhase = VoicePhase.Editing
    }
    val onVoiceSendEdit: () -> Unit = {
        val t = voiceEditText.trim()
        if (t.isNotBlank() && !state.isLoadingResponse) viewModel.sendMessage(t)
    }

    LaunchedEffect(state.isComplete) { if (state.isComplete) onComplete() }

    val sendMessage: () -> Unit = {
        if (messageText.isNotBlank() && !state.isLoadingResponse) {
            viewModel.sendMessage(messageText)
            messageText = ""
            showHandwriting = false
        }
    }

    // ── Full-screen Typeform layout ────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Thin progress line — replaces the chunky AppBar
        if (state.totalCount > 0) {
            LinearProgressIndicator(
                progress = { state.filledCount.toFloat() / state.totalCount },
                modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.TopCenter),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        // Top bar: back button + form name (left) + review button (right)
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (viewModel.hasPreviousField()) viewModel.goToPreviousField()
                else onNavigateBack()
            }) {
                Icon(
                    Icons.Default.ArrowBack, "Back",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                )
            }
            Text(
                text = state.form?.fileName ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = { viewModel.skipToReview() },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Icon(
                    Icons.Default.RateReview,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Review so far",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        when {
            state.isLoading -> LoadingState()
            state.form == null -> ErrorState(state.error ?: "Form not found")
            else -> {
                // Pre-fill confirm panel takes priority (cross-form returning patient)
                val confirmFields = state.pendingConfirmFields
                if (confirmFields != null) {
                    ConfirmPrefillPanel(
                        fields = confirmFields,
                        onConfirm = { viewModel.confirmPrefill() },
                        onStartFresh = { viewModel.dismissPrefillAndStartFresh() },
                        onFieldEdit = { id, value -> viewModel.updateConfirmField(id, value) },
                        modifier = Modifier.fillMaxSize().padding(top = 52.dp)
                    )
                    return@Box
                }

                // Consent batch takes over the whole screen
                val consentBatch = state.consentBatchFields
                if (consentBatch != null) {
                    ConsentBatchPanel(
                        fields = consentBatch,
                        isLoading = state.isLoadingResponse,
                        onSubmit = { answers -> viewModel.submitConsentBatch(answers) },
                        modifier = Modifier.fillMaxSize().padding(top = 52.dp)
                    )
                    return@Box
                }

                // Error banner
                state.error?.let { error ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(top = 52.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Error, null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.clearError() }) {
                                Icon(Icons.Default.Close, "Dismiss", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                if (voiceFirstActive) {
                    VoiceFirstQuestion(
                        questionText = (currentQuestion?.text ?: "")
                            .replace(Regex("\\s*\\([^)]{0,80}\\)\\s*$"), "").trim(),
                        phase = voicePhase,
                        liveTranscript = liveTranscript,
                        finalTranscript = finalTranscript,
                        amplitude = micAmplitude,
                        editText = voiceEditText,
                        onEditTextChange = { voiceEditText = it },
                        editFocusRequester = voiceEditFocus,
                        onAccept = onVoiceAccept,
                        onRerecord = onVoiceRerecord,
                        onEdit = onVoiceEdit,
                        onSendEdit = onVoiceSendEdit,
                        onStartTyping = onVoiceStartTyping,
                        onTapToSpeak = voiceStartListening,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 52.dp)
                    )
                } else {

                // Center: animated question + inline answer
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 52.dp, bottom = 88.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        state.isLoadingResponse -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                        }

                        currentQuestion != null -> {
                            AnimatedContent(
                                targetState = currentQuestion,
                                transitionSpec = {
                                    (slideInVertically { it / 3 } + fadeIn()) togetherWith
                                    (slideOutVertically { -it / 3 } + fadeOut())
                                },
                                label = "question"
                            ) { question ->
                                val field = state.currentAskingField
                                val displayText = question.text
                                    .replace(Regex("\\s*\\([^)]{0,80}\\)\\s*$"), "").trim()

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 48.dp),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    // Question text
                                    Text(
                                        text = displayText,
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontWeight = FontWeight.Normal,
                                            fontSize = 28.sp,
                                            lineHeight = 38.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )


                                    Spacer(Modifier.height(36.dp))

                                    // Inline answer area — type varies by field
                                    when {
                                        // Signature: dedicated drawing canvas, bitmap capture
                                        field != null && field.fieldType == FieldType.SIGNATURE -> {
                                            SignatureCapture(
                                                onSignatureCaptured = { bitmap ->
                                                    viewModel.submitSignature(bitmap)
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }

                                        // Handwriting mode for non-signature text fields
                                        showHandwriting -> {
                                            HandwritingInput(
                                                language = state.userLanguage,
                                                onTextRecognized = { text ->
                                                    messageText = if (messageText.isBlank()) text else "$messageText $text"
                                                },
                                                onSwitchToKeyboard = { showHandwriting = false },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }

                                        field != null &&
                                        field.fieldType == FieldType.MULTI_SELECT &&
                                        field.options.isNotEmpty() -> {
                                            MultiSelectInline(field = field, viewModel = viewModel)
                                        }

                                        field != null && field.options.isNotEmpty() -> {
                                            RadioChipsInline(
                                                field = field,
                                                onSelect = { viewModel.sendMessage(it) }
                                            )
                                        }

                                        else -> {
                                            // Typeform underline text input
                                            TypeformTextInput(
                                                value = messageText,
                                                onValueChange = { messageText = it },
                                                onSend = sendMessage,
                                                enabled = !state.isLoadingResponse,
                                                focusRequester = focusRequester,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        else -> {
                            Text(
                                "Starting your intake...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                            )
                        }
                    }
                }

                // Bottom bar: OK + "press Enter ↵" + mic / draw / TTS
                // Hidden for signature fields — SignatureCapture has its own Done button
                if (!state.isLoadingResponse && currentQuestion != null &&
                    state.consentBatchFields == null &&
                    state.currentAskingField?.fieldType != FieldType.SIGNATURE
                ) {
                    val field = state.currentAskingField
                    val isTextInput = field == null ||
                        (field.options.isEmpty() && field.fieldType != FieldType.MULTI_SELECT)

                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                        color = MaterialTheme.colorScheme.background,
                        tonalElevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 48.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (isTextInput && !showHandwriting) {
                                Button(
                                    onClick = sendMessage,
                                    enabled = messageText.isNotBlank(),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    Text("OK", fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp))
                                }
                                Text(
                                    text = "press Enter ↵",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                )
                            }

                            Spacer(Modifier.weight(1f))

                            // Handwriting toggle
                            IconButton(onClick = { showHandwriting = !showHandwriting }) {
                                Icon(
                                    if (showHandwriting) Icons.Default.Keyboard else Icons.Default.Draw,
                                    contentDescription = if (showHandwriting) "Keyboard" else "Write",
                                    tint = if (showHandwriting)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                )
                            }

                            // Microphone
                            if (speechAvailable) {
                                IconButton(
                                    onClick = {
                                        if (isListening) {
                                            cancelListening()
                                        } else if (ContextCompat.checkSelfPermission(
                                                context, Manifest.permission.RECORD_AUDIO
                                            ) == PackageManager.PERMISSION_GRANTED
                                        ) {
                                            startListening()
                                        } else {
                                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    },
                                    enabled = !state.isLoadingResponse
                                ) {
                                    Icon(
                                        if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                                        contentDescription = if (isListening) "Stop" else "Speak",
                                        tint = if (isListening)
                                            MaterialTheme.colorScheme.error
                                        else
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                    )
                                }
                            }

                            // TTS read-aloud
                            if (ttsReady) {
                                IconButton(
                                    onClick = {
                                        if (isSpeaking) {
                                            tts.stop()
                                            isSpeaking = false
                                        } else {
                                            val text = currentQuestion.text
                                                .replace(Regex("\\s*\\([^)]{0,80}\\)\\s*$"), "").trim()
                                            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "q")
                                        }
                                    }
                                ) {
                                    Icon(
                                        if (isSpeaking) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                        contentDescription = if (isSpeaking) "Stop reading" else "Read aloud",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                    )
                                }
                            }
                        }
                    }
                }
                }
            }
        }

        // ── Floating chat FAB (bottom-right, always visible) ─────────────────
        if (!chatPanelOpen && state.form != null && !state.isLoading) {
            val unreadCount = state.chatMessages.size
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 24.dp)
            ) {
                FloatingActionButton(
                    onClick = {
                        chatPanelOpen = true
                        coroutineScope.launch {
                            if (state.chatMessages.isNotEmpty()) {
                                chatListState.animateScrollToItem(state.chatMessages.size - 1)
                            }
                        }
                    },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(Icons.Default.Chat, contentDescription = "Open conversation history")
                }
                // Badge showing message count
                if (unreadCount > 0) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(18.dp)
                            .align(Alignment.TopEnd)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (unreadCount > 9) "9+" else unreadCount.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
        }

        // ── Chat sidebar (slides in from right) ──────────────────────────────
        AnimatedVisibility(
            visible = chatPanelOpen,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Surface(
                modifier = Modifier.width(360.dp).fillMaxHeight(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 8.dp,
                shadowElevation = 16.dp,
                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Chat, null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Conversation",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { chatPanelOpen = false },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close, "Close",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    // Message history
                    LazyColumn(
                        state = chatListState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.chatMessages) { message ->
                            ChatBubble(message = message)
                        }
                        if (state.isLoadingResponse) {
                            item { TypingIndicator() }
                        }
                    }

                    HorizontalDivider()

                    // Clarification input inside sidebar — uses chatText, NOT messageText
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            OutlinedTextField(
                                value = chatText,
                                onValueChange = { chatText = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Ask a question...") },
                                enabled = !state.isLoadingResponse,
                                maxLines = 3,
                                textStyle = MaterialTheme.typography.bodyMedium,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = {
                                    if (chatText.isNotBlank() && !state.isLoadingResponse) {
                                        viewModel.sendChatMessage(chatText)
                                        chatText = ""
                                    }
                                })
                            )
                            Spacer(Modifier.width(4.dp))
                            FilledIconButton(
                                onClick = {
                                    if (chatText.isNotBlank() && !state.isLoadingResponse) {
                                        viewModel.sendChatMessage(chatText)
                                        chatText = ""
                                    }
                                },
                                enabled = chatText.isNotBlank() && !state.isLoadingResponse
                            ) {
                                Icon(Icons.Default.Send, "Send")
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Chat bubble + typing indicator ──────────────────────────────────────────

@Composable
private fun ChatBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = if (message.isFromUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (message.isFromUser)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.secondaryContainer
            ),
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (message.isFromUser) 16.dp else 4.dp,
                bottomEnd = if (message.isFromUser) 4.dp else 16.dp
            )
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (message.isFromUser)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
private fun TypingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        shape = CircleShape
                    )
            )
            if (it < 2) Spacer(Modifier.width(4.dp))
        }
    }
}

// ─── Typeform underline text input ────────────────────────────────────────────

@Composable
private fun TypeformTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.focusRequester(focusRequester),
        enabled = enabled,
        textStyle = MaterialTheme.typography.headlineSmall.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Light,
            fontSize = 24.sp
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { onSend() }),
        maxLines = 4,
        decorationBox = { innerTextField ->
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = "Type your answer...",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Light,
                                fontSize = 24.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
                            )
                        )
                    }
                    innerTextField()
                }
                // Typeform signature underline
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                    thickness = 2.dp
                )
            }
        }
    )
}

// ─── Voice-first question (sliding fee) ───────────────────────────────────────

/**
 * Full-screen voice-first layout for a single free-text question.
 *
 * The question sits BIG and centered, slightly above the vertical middle. An
 * animated blue arch below it reacts to the microphone; the recognized speech
 * streams in beneath the arch in blue. Once speech ends, Accept / Re-record /
 * Edit appear. A low-key "Type your response" button sits at the bottom.
 */
@Composable
private fun VoiceFirstQuestion(
    questionText: String,
    phase: VoicePhase,
    liveTranscript: String,
    finalTranscript: String,
    amplitude: Float,
    editText: String,
    onEditTextChange: (String) -> Unit,
    editFocusRequester: FocusRequester,
    onAccept: () -> Unit,
    onRerecord: () -> Unit,
    onEdit: () -> Unit,
    onSendEdit: () -> Unit,
    onStartTyping: () -> Unit,
    onTapToSpeak: () -> Unit,
    modifier: Modifier = Modifier
) {
    val blue = MaterialTheme.colorScheme.primary

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Bias the question block slightly above vertical center
            Spacer(Modifier.weight(0.85f))

            Text(
                text = questionText,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 46.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(28.dp))

            // Animated blue arch — reacts to the mic; tappable to (re)start.
            // The whole padded band is the tap target (not just the thin line).
            val archTappable = phase == VoicePhase.Prompt || phase == VoicePhase.Review
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (archTappable) Modifier.clickable { onTapToSpeak() }
                        else Modifier
                    )
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                VoiceArch(
                    amplitude = amplitude,
                    active = phase == VoicePhase.Listening,
                    color = blue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            when (phase) {
                VoicePhase.Editing -> {
                    VoiceEditField(
                        value = editText,
                        onValueChange = onEditTextChange,
                        onSend = onSendEdit,
                        focusRequester = editFocusRequester,
                        accentColor = blue
                    )
                }
                else -> {
                    val transcript = if (phase == VoicePhase.Review) finalTranscript else liveTranscript
                    if (transcript.isNotBlank()) {
                        Text(
                            text = transcript,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = blue,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        val hint = when (phase) {
                            VoicePhase.Speaking -> stringResource(R.string.voice_speaking)
                            VoicePhase.Listening -> stringResource(R.string.voice_listening)
                            else -> stringResource(R.string.voice_tap_to_speak)
                        }
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.titleMedium,
                            color = blue.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    // The labeled "Tap to speak" affordance must itself
                                    // be tappable, not just the arch above it.
                                    if (phase == VoicePhase.Prompt)
                                        Modifier.clickable { onTapToSpeak() }
                                    else Modifier
                                )
                                .padding(vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // Accept / Re-record / Edit — after speech ends
            if (phase == VoicePhase.Review && finalTranscript.isNotBlank()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onAccept,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.voice_accept), fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = onRerecord,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.Replay, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.voice_rerecord))
                    }
                    OutlinedButton(
                        onClick = onEdit,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.voice_edit))
                    }
                }
            }

            Spacer(Modifier.weight(1.15f))
        }

        // Low-key "Type your response" — out of the way but easy to tap
        if (phase != VoicePhase.Editing) {
            TextButton(
                onClick = onStartTyping,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
            ) {
                Icon(
                    Icons.Default.Keyboard, null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.voice_type_response),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

/**
 * The blue arch waveform. A gentle upward bow whose middle shimmers with a
 * traveling sine wave; the wave's amplitude tracks the microphone level so the
 * line visibly "moves" while the patient speaks.
 */
@Composable
private fun VoiceArch(
    amplitude: Float,
    active: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "voiceArch")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    // Noise-gate the mic level: ambient/silence maps to ~0 so the line stays
    // still while the mic is open, and louder speech drives a bigger wave.
    val gatedAmp = ((amplitude - 0.2f) / 0.8f).coerceIn(0f, 1f)
    val animatedAmp by animateFloatAsState(
        targetValue = if (active) gatedAmp else 0.12f,
        animationSpec = tween(90),
        label = "amp"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val midY = h / 2f
        val archHeight = h * 0.13f
        val maxWave = h * 0.34f
        // Idle keeps a faint constant shimmer; while listening there is NO floor,
        // so the wave only appears when real audio is being picked up and scales
        // with how loud the voice is.
        val idleFloor = if (active) 0f else h * 0.05f
        val ampPx = idleFloor + animatedAmp * maxWave
        val steps = 80
        val path = Path()
        for (i in 0..steps) {
            val t = i.toFloat() / steps              // 0..1 across the width
            val x = w * t
            val envelope = sin(t * PI).toFloat()     // 0 at the ends, 1 in the middle
            val arch = -archHeight * envelope        // gentle upward bow
            val wave = sin(t * PI.toFloat() * 5f + phase) * envelope * ampPx
            val y = midY + arch + wave
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

/**
 * Centered, underlined editor used by the Edit / Type-your-response paths.
 * Auto-focused by the caller so the keyboard appears immediately.
 */
@Composable
private fun VoiceEditField(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    focusRequester: FocusRequester,
    accentColor: Color
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            textStyle = MaterialTheme.typography.headlineSmall.copy(
                color = accentColor,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            ),
            cursorBrush = SolidColor(accentColor),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            maxLines = 4,
            decorationBox = { inner ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = stringResource(R.string.voice_type_response),
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                                    textAlign = TextAlign.Center
                                )
                            )
                        }
                        inner()
                    }
                    HorizontalDivider(color = accentColor.copy(alpha = 0.6f), thickness = 2.dp)
                }
            }
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onSend,
            enabled = value.isNotBlank(),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.align(Alignment.CenterHorizontally),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Text(stringResource(R.string.voice_send), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Default.Send, null, modifier = Modifier.size(16.dp))
        }
    }
}

// ─── Radio / dropdown chips (inline, Typeform letter-keyed) ───────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RadioChipsInline(
    field: com.medpull.kiosk.data.models.FormField,
    onSelect: (String) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        field.options.forEachIndexed { index, option ->
            val letter = ('A' + index).toString()
            FilterChip(
                selected = false,
                onClick = { onSelect(option) },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = letter,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(option, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            )
        }
    }
}

// ─── Multi-select chips (inline, toggleable) ──────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultiSelectInline(
    field: com.medpull.kiosk.data.models.FormField,
    viewModel: GuidedIntakeViewModel
) {
    val currentSelections = remember(field.id, field.value) {
        mutableStateOf(
            field.value
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.toMutableSet()
                ?: mutableSetOf()
        )
    }

    Column {
        Text(
            text = "Select all that apply:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            field.options.forEach { option ->
                val selected = option in currentSelections.value
                FilterChip(
                    selected = selected,
                    onClick = {
                        val updated = currentSelections.value.toMutableSet()
                        if (selected) updated.remove(option) else updated.add(option)
                        currentSelections.value = updated
                        viewModel.updateMultiSelectField(field.id, updated.joinToString(", "))
                    },
                    label = { Text(option, style = MaterialTheme.typography.bodyMedium) }
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { viewModel.sendMessage("None") }) { Text("None") }
            if (currentSelections.value.isNotEmpty()) {
                Button(
                    onClick = { viewModel.sendMessage(currentSelections.value.joinToString(", ")) },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Confirm (${currentSelections.value.size})")
                }
            }
        }
    }
}

// ─── Consent batch panel ──────────────────────────────────────────────────────

/**
 * Shows all consent fields together in a single batch UI.
 * Each field gets its label, optional description, and radio chips.
 * "Agree to All" pre-selects the first/affirmative option for every field.
 * "Continue" is only enabled once every field has a selection.
 */
@Composable
private fun ConsentBatchPanel(
    fields: List<com.medpull.kiosk.data.models.FormField>,
    isLoading: Boolean,
    onSubmit: (Map<String, String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val selections = remember(fields) { mutableStateMapOf<String, String>() }
    val allAnswered = fields.all { it.id in selections }

    Column(modifier = modifier) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                Text(
                    text = "Consent & Authorizations",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Please review each item and select your preference.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                )
            }
        }

        // "Agree to All" shortcut
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = {
                        fields.forEach { f ->
                            val first = f.options.firstOrNull()
                            if (first != null) selections[f.id] = first
                        }
                    },
                    enabled = !isLoading
                ) {
                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Agree to All")
                }
            }
        }

        HorizontalDivider()

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(fields) { field ->
                ConsentFieldCard(
                    field = field,
                    selectedOption = selections[field.id],
                    onOptionSelected = { option -> selections[field.id] = option },
                    enabled = !isLoading
                )
            }
        }

        HorizontalDivider()

        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!allAnswered) {
                    Text(
                        text = "${fields.count { it.id in selections }} of ${fields.size} answered",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Button(
                    onClick = { onSubmit(selections.toMap()) },
                    enabled = allAnswered && !isLoading,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Continue")
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConsentFieldCard(
    field: com.medpull.kiosk.data.models.FormField,
    selectedOption: String?,
    onOptionSelected: (String) -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selectedOption != null)
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = field.fieldName,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                field.options.forEach { option ->
                    FilterChip(
                        selected = selectedOption == option,
                        onClick = { if (enabled) onOptionSelected(option) },
                        label = { Text(option, style = MaterialTheme.typography.bodySmall) }
                    )
                }
            }
        }
    }
}

// ─── Utility composables ──────────────────────────────────────────────────────

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("Loading form...", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun ErrorState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.Error, null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─── Confirm Pre-fill Panel ───────────────────────────────────────────────────

/**
 * Shown when a returning patient's demographic info has been pre-loaded from a
 * previous form. Patient can confirm everything looks right or edit individual fields.
 */
@Composable
private fun ConfirmPrefillPanel(
    fields: List<FormField>,
    onConfirm: () -> Unit,
    onStartFresh: () -> Unit,
    onFieldEdit: (fieldId: String, newValue: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var editingFieldId by remember { mutableStateOf<String?>(null) }
    var editingValue by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Welcome back!",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "We found your information from your last visit. Please confirm it's still correct.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Editable field list
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(fields) { field ->
                val isEditing = editingFieldId == field.id
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isEditing)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = field.fieldName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Spacer(Modifier.height(4.dp))
                            if (isEditing) {
                                OutlinedTextField(
                                    value = editingValue,
                                    onValueChange = { editingValue = it },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = MaterialTheme.typography.bodyMedium,
                                    keyboardActions = KeyboardActions(onDone = {
                                        onFieldEdit(field.id, editingValue)
                                        editingFieldId = null
                                    })
                                )
                            } else {
                                Text(
                                    text = field.value ?: "—",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        if (isEditing) {
                            IconButton(onClick = {
                                onFieldEdit(field.id, editingValue)
                                editingFieldId = null
                            }) {
                                Icon(Icons.Default.Check, "Save", tint = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            IconButton(onClick = {
                                editingFieldId = field.id
                                editingValue = field.value ?: ""
                            }) {
                                Icon(
                                    Icons.Default.Edit, "Edit",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Action buttons
        Button(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Looks good — continue", style = MaterialTheme.typography.titleSmall)
        }

        Spacer(Modifier.height(10.dp))

        TextButton(onClick = onStartFresh) {
            Text(
                text = "Enter my info fresh",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}
