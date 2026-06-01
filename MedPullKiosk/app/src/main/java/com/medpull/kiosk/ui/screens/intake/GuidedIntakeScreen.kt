package com.medpull.kiosk.ui.screens.intake

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.automirrored.filled.*
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
import com.medpull.kiosk.ui.components.StepBackButton
import com.medpull.kiosk.ui.screens.ai.ChatMessage
import com.medpull.kiosk.ui.screens.ai.HandwritingInput
import com.medpull.kiosk.ui.screens.ai.SignatureCapture
import kotlinx.coroutines.delay
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

private const val TTS_STT_TAG = "GuidedIntakeVoice"

/** Human-readable name for a SpeechRecognizer.ERROR_* code, for logging. */
private fun speechErrorName(code: Int): String = when (code) {
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "NETWORK_TIMEOUT"
    SpeechRecognizer.ERROR_NETWORK -> "NETWORK"
    SpeechRecognizer.ERROR_AUDIO -> "AUDIO"
    SpeechRecognizer.ERROR_SERVER -> "SERVER"
    SpeechRecognizer.ERROR_CLIENT -> "CLIENT"
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "SPEECH_TIMEOUT"
    SpeechRecognizer.ERROR_NO_MATCH -> "NO_MATCH"
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RECOGNIZER_BUSY"
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "INSUFFICIENT_PERMISSIONS"
    SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "TOO_MANY_REQUESTS"
    SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "SERVER_DISCONNECTED"
    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "LANGUAGE_NOT_SUPPORTED"
    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "LANGUAGE_UNAVAILABLE"
    SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT -> "CANNOT_CHECK_SUPPORT"
    else -> "UNKNOWN"
}

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

    // ── Voice-first mode (free-text questions on ANY conversational form) ───────
    // Voice-first replaces the typed input with: auto-spoken question, an animated
    // blue arch that reacts to the mic, a live blue transcript, and an
    // accept / re-record / edit confirmation step. Choice-chip, multi-select and
    // signature questions keep their specialized inputs (but are still spoken).
    // This is driven entirely by field type, so it works for every form the
    // schema engine can load (sliding fee, medical intake, future forms).
    val activeField = state.currentAskingField
    val isFreeTextField = activeField == null ||
        (activeField.options.isEmpty() &&
            activeField.fieldType != FieldType.SIGNATURE &&
            activeField.fieldType != FieldType.MULTI_SELECT)
    val voiceFirstActive = isFreeTextField &&
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
    // SERVER_DISCONNECTED / RECOGNIZER_BUSY are usually transient (the recognition
    // service was still spinning up). Bumping this token re-arms a single retry;
    // didRetry stops it from looping forever on a genuinely-down backend.
    var sttRetryToken by remember { mutableIntStateOf(0) }
    var sttDidRetry by remember { mutableStateOf(false) }
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
    val onSpeechError = rememberUpdatedState<(Int) -> Unit> { error ->
        isListening = false
        micAmplitude = 0f
        // Don't swallow the code — a foreign locale with no on-device language
        // pack returns ERROR_LANGUAGE_UNAVAILABLE/NOT_SUPPORTED and the mic would
        // otherwise appear to do nothing. Surfacing it makes the failure visible.
        Log.w(TTS_STT_TAG, "SpeechRecognizer error ${speechErrorName(error)} ($error) for locale=$speechLocale")
        val transient = error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED ||
            error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
            error == SpeechRecognizer.ERROR_SERVER
        if (transient && !sttDidRetry && voiceActiveRef.value && finalTranscript.isBlank()) {
            sttDidRetry = true
            Log.i(TTS_STT_TAG, "Transient recognizer error — retrying once")
            sttRetryToken++
        } else if (voiceActiveRef.value && finalTranscript.isBlank()) {
            voicePhase = VoicePhase.Prompt
        }
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
            override fun onError(error: Int) { onSpeechError.value(error) }
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
        if (!speechAvailable) {
            Log.w(TTS_STT_TAG, "Speech recognition unavailable on this device")
            if (voiceActiveRef.value) voicePhase = VoicePhase.Prompt
            return@start
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, speechLocale)
            // Some recognizers fall back to the system locale unless the preferred
            // language is also set here — keep both in sync so foreign speech is
            // actually recognized in the patient's language.
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, speechLocale)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Give patients much longer to think and pause before the recognizer
            // decides they're done. Older / slower speakers routinely pause
            // mid-sentence; the default ~1s of trailing silence cut them off. We
            // ask for several seconds of allowable silence and a long minimum
            // listening window. (Google's recognizer may clamp these, but many
            // OEM engines honor them, and they never make the timeout shorter.)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 6000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 6000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 10000L)
            // A few OEM recognizers silently no-op without a calling package.
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
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
    // Fire the one-shot retry armed by onSpeechError on a transient failure.
    LaunchedEffect(sttRetryToken) {
        if (sttRetryToken > 0 && voiceActiveRef.value) {
            delay(400L)
            if (voiceActiveRef.value && finalTranscript.isBlank()) {
                voicePhase = VoicePhase.Listening
                startListening()
            }
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
        } else {
            // Without RECORD_AUDIO the recognizer's app-op never starts ("Operation
            // not started op=RECORD_AUDIO") and the mic silently does nothing.
            Log.w(TTS_STT_TAG, "RECORD_AUDIO permission denied — voice input unavailable")
            if (voiceActiveRef.value) voicePhase = VoicePhase.Prompt
        }
    }

    // Resolve the mic permission during form load — BEFORE the first question —
    // so the very first question can auto-open the mic hands-free instead of
    // stalling on a permission dialog mid-question. Unlike micPermissionLauncher,
    // this one only pre-resolves the grant; it never starts a listening session.
    val micPrewarmLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* pre-resolve only; auto-listen is driven by the question flow */ }

    DisposableEffect(Unit) {
        onDispose {
            recognizerHolder.value?.let { try { it.destroy() } catch (_: Exception) {} }
            recognizerHolder.value = null
        }
    }

    // ── Text-to-speech ────────────────────────────────────────────────────────
    var ttsReady by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    // The TTS engine's remote service can die (DeadObjectException on speak),
    // leaving a dead binder. Bumping this generation rebuilds the engine; keying
    // the instance on it disposes the old one and re-binds. ttsReady flips
    // false→true on rebuild, which re-fires the speak effect to recover speech.
    var ttsGeneration by remember { mutableIntStateOf(0) }
    var ttsDidReinit by remember { mutableStateOf(false) }
    // The very first real speak() on a freshly-bound engine is slow (the service
    // binds and the voice pipeline spins up). We prime it once with a silent
    // utterance during form load so the first spoken QUESTION plays immediately
    // instead of lagging. Keyed per engine instance via ttsGeneration.
    var ttsWarmedUp by remember(ttsGeneration) { mutableStateOf(false) }
    val tts = remember(ttsGeneration) {
        TextToSpeech(context) { status -> ttsReady = status == TextToSpeech.SUCCESS }
    }
    DisposableEffect(tts) { onDispose { try { tts.stop(); tts.shutdown() } catch (_: Exception) {} } }

    // Leave the spoken-question phase: open the mic hands-free if we can, else
    // fall back to tap-to-speak. Guarded so it only acts while still "Speaking",
    // making it safe to call from the TTS callbacks AND the watchdog below.
    // rememberUpdatedState keeps the set-once TTS listener pointed at the latest.
    val proceedAfterQuestionRef = rememberUpdatedState<() -> Unit> {
        if (voiceActiveRef.value && voicePhase == VoicePhase.Speaking) {
            val granted = speechAvailable && ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                liveTranscript = ""; finalTranscript = ""
                voicePhase = VoicePhase.Listening
                startListening()
            } else {
                voicePhase = VoicePhase.Prompt
            }
        }
    }

    LaunchedEffect(state.userLanguage, ttsReady) {
        if (ttsReady) {
            // Country-qualified locales: several engines report a bare language
            // tag ("zh", "ar") as NOT_SUPPORTED but accept the regional variant
            // (zh-CN, ar-SA) whose voice data is actually installed.
            val locale = when (state.userLanguage) {
                "es" -> Locale("es", "ES"); "zh" -> Locale.SIMPLIFIED_CHINESE
                "fr" -> Locale("fr", "FR"); "ja" -> Locale.JAPANESE
                "pt" -> Locale("pt", "BR"); "ar" -> Locale("ar", "SA")
                "ru" -> Locale("ru", "RU")
                else -> Locale.US
            }
            // tts.language ignores the result, so a missing voice silently keeps
            // the previous (English) voice — foreign questions then come out in
            // English or not at all. Check the code and log so it's diagnosable.
            val result = tts.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                Log.w(
                    TTS_STT_TAG,
                    "TTS locale $locale unavailable (code=$result) for " +
                        "'${state.userLanguage}'; voice data may need installing"
                )
            } else {
                Log.i(TTS_STT_TAG, "TTS locale set to $locale (code=$result)")
            }
            // Prime THIS engine instance once so the first real question speaks
            // without cold-start delay. A real (inaudible) synthesis — not
            // playSilentUtterance — is what forces the voice model to load; silence
            // skips the model on many engines, leaving the first question to pay the
            // load cost. Volume 0 keeps it silent while exercising the full pipeline.
            // (SpeechPrewarmer already warmed the shared service from the previous
            // screen; this covers the screen's own freshly-built instance.)
            if (!ttsWarmedUp) {
                ttsWarmedUp = true
                try {
                    val warmParams = Bundle().apply {
                        putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 0f)
                    }
                    tts.speak("ok", TextToSpeech.QUEUE_FLUSH, warmParams, "warmup")
                } catch (_: Exception) {}
            }
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.i(TTS_STT_TAG, "TTS speaking '$utteranceId' in $locale")
                    mainHandler.post { isSpeaking = true }
                }
                override fun onDone(utteranceId: String?) {
                    mainHandler.post {
                        isSpeaking = false
                        if (utteranceId == "question") proceedAfterQuestionRef.value()
                    }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    Log.w(TTS_STT_TAG, "TTS error speaking '$utteranceId'")
                    // A QUESTION failure must NOT strand the user on "Speaking" —
                    // advance. The silent "warmup" utterance is intentionally flushed
                    // by the question's QUEUE_FLUSH; its error must NOT advance the
                    // flow (that would skip the mic past the spoken question).
                    mainHandler.post {
                        isSpeaking = false
                        if (utteranceId == "question") proceedAfterQuestionRef.value()
                    }
                }
                override fun onError(utteranceId: String?, errorCode: Int) {
                    Log.w(TTS_STT_TAG, "TTS error speaking '$utteranceId' (code=$errorCode)")
                    mainHandler.post {
                        isSpeaking = false
                        if (utteranceId == "question") proceedAfterQuestionRef.value()
                    }
                }
            })
        }
    }

    // One engine-rebuild budget per question — keyed on the question only (NOT
    // ttsReady), so the post-rebuild ttsReady flip can't re-arm it into a loop.
    LaunchedEffect(currentQuestion?.text) { ttsDidReinit = false }

    // Auto-speak each new question, then open the mic once it finishes.
    //
    // The mic is opened by the TTS onDone/onError callbacks (right when speech
    // ends). The delay below is ONLY a safety net for engines that never call
    // back — and it is sized to the estimated speech length so we never open the
    // mic while TTS is still talking (doing so made the mic capture the speaker,
    // which both broke recognition and pinned the waveform at max).
    LaunchedEffect(currentQuestion?.text, voiceFirstActive, ttsReady) {
        if (voiceFirstActive && currentQuestion != null) {
            liveTranscript = ""
            finalTranscript = ""
            voiceEditText = ""
            micAmplitude = 0f
            sttDidRetry = false  // each question gets a fresh retry budget
            val q = currentQuestion.text
                .replace(Regex("\\s*\\([^)]{0,80}\\)\\s*$"), "").trim()
            voicePhase = VoicePhase.Speaking

            // TTS init is async — on the very first question ttsReady is often
            // still false. Don't mistake that for a broken engine (which would
            // skip speech and jump straight to the mic). Wait: this effect is
            // keyed on ttsReady, so it re-fires and speaks the moment the engine
            // is ready. The delay is only a safety net if it never initializes.
            if (!ttsReady) {
                delay(4000L)
                if (voicePhase == VoicePhase.Speaking && !ttsReady) proceedAfterQuestionRef.value()
                return@LaunchedEffect
            }

            val result = if (q.isNotBlank())
                tts.speak(q, TextToSpeech.QUEUE_FLUSH, null, "question")
            else TextToSpeech.ERROR

            // A dead TTS service returns ERROR (DeadObjectException). Rebuild the
            // engine once; the ttsReady flip re-fires this effect to speak again.
            if (result == TextToSpeech.ERROR && ttsReady && !ttsDidReinit) {
                Log.w(TTS_STT_TAG, "TTS speak failed — reinitializing engine and retrying")
                ttsDidReinit = true
                ttsReady = false
                ttsGeneration++
                return@LaunchedEffect
            }
            val spokeOk = result == TextToSpeech.SUCCESS

            if (!spokeOk && ttsDidReinit && !ttsReady) {
                // Engine is rebuilding — don't open the mic yet; this effect
                // re-fires (and re-speaks) when ttsReady returns. Safety net only:
                // if the rebuild never completes, advance so we never strand here.
                delay(4000L)
                if (voicePhase == VoicePhase.Speaking && !ttsReady) proceedAfterQuestionRef.value()
            } else if (!spokeOk) {
                // TTS won't speak at all — go to the mic almost immediately.
                delay(300L)
                if (voicePhase == VoicePhase.Speaking) proceedAfterQuestionRef.value()
            } else {
                // Backstop only. ~90ms/char + 1.5s buffer roughly matches speech
                // length; onDone normally fires first and advances us.
                val estimateMs = (q.length * 90L + 1500L).coerceIn(2500L, 12000L)
                delay(estimateMs)
                if (voicePhase == VoicePhase.Speaking) proceedAfterQuestionRef.value()
            }
        }
    }

    // Auto-speak the choice-chip / signature questions too, so every question is
    // read aloud — these keep their specialized inputs (no mic flow).
    LaunchedEffect(currentQuestion?.text, voiceFirstActive, ttsReady, state.consentBatchFields) {
        if (!voiceFirstActive && ttsReady && currentQuestion != null &&
            state.consentBatchFields == null && state.pendingConfirmFields == null &&
            !state.isLoadingResponse
        ) {
            val q = currentQuestion.text
                .replace(Regex("\\s*\\([^)]{0,80}\\)\\s*$"), "").trim()
            if (q.isNotBlank()) tts.speak(q, TextToSpeech.QUEUE_FLUSH, null, "q")
        }
    }

    // Request mic permission as soon as the screen opens (during form load) so
    // hands-free auto-listen is ready by the very first question and never has to
    // pause for a permission dialog mid-question.
    LaunchedEffect(speechAvailable) {
        if (speechAvailable &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            micPrewarmLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Pre-bind the speech-recognition service during form load. The first real
    // startListening() otherwise pays the full RecognitionService bind cost — the
    // visible "microphone coming on" lag on the first one or two questions. We warm
    // it with a THROWAWAY recognizer and only query support (checkRecognitionSupport,
    // API 33+) — never startListening — so the mic never opens and no chime plays.
    // The real per-session recognizers are still created fresh in startListening();
    // this only warms the underlying service process so that bind is already paid.
    var sttWarmedUp by remember { mutableStateOf(false) }
    LaunchedEffect(speechAvailable) {
        if (speechAvailable && !sttWarmedUp &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            sttWarmedUp = true
            try {
                val warm = SpeechRecognizer.createSpeechRecognizer(context)
                val warmIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, speechLocale)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                }
                warm.checkRecognitionSupport(
                    warmIntent,
                    context.mainExecutor,
                    object : RecognitionSupportCallback {
                        override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                            try { warm.destroy() } catch (_: Exception) {}
                        }
                        override fun onError(error: Int) {
                            try { warm.destroy() } catch (_: Exception) {}
                        }
                    }
                )
            } catch (_: Exception) {}
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
                sttDidRetry = false  // fresh user-initiated session
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
            // Big back button: always leaves the whole form (back to the form /
            // program selector). Stepping back through questions is the separate,
            // smaller "Prev question" button below.
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                )
            }
            // Small in-form step-back: revisit the previous answered question without
            // leaving the form. Hidden on the first question (nothing to go back to).
            if (viewModel.hasPreviousField()) {
                StepBackButton(
                    label = stringResource(R.string.prev_question),
                    onClick = { viewModel.goToPreviousField() }
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
                    text = stringResource(R.string.review_so_far),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        when {
            state.isLoading -> LoadingState()
            state.form == null -> ErrorState(state.error ?: stringResource(R.string.form_not_found))
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
                                Icon(Icons.Default.Close, stringResource(R.string.dismiss), modifier = Modifier.size(16.dp))
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

                                // Button-press questions (radio / dropdown / multi-select)
                                // get the same BIG, centered question as the voice text
                                // questions, with large full-width option buttons.
                                val isOptionField = field != null && field.options.isNotEmpty() &&
                                    field.fieldType != FieldType.SIGNATURE

                                if (isOptionField && field != null) {
                                    // Scrollable: long option lists (e.g. 18 medical
                                    // conditions) overflow the screen. Without scroll
                                    // the Confirm/None buttons get clipped below the
                                    // fold and the patient can't advance. A scroll
                                    // container keeps every option AND the action
                                    // buttons reachable for ANY option count.
                                    // (weight() spacers can't be used inside a
                                    // verticalScroll — they need fixed heights here.)
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState())
                                            .padding(horizontal = 32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Spacer(Modifier.height(40.dp))
                                        Text(
                                            text = displayText,
                                            style = MaterialTheme.typography.displayMedium.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                lineHeight = 56.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(Modifier.height(36.dp))
                                        if (field.fieldType == FieldType.MULTI_SELECT) {
                                            BigMultiSelect(
                                                field = field,
                                                viewModel = viewModel,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        } else {
                                            BigOptionButtons(
                                                options = field.options,
                                                onSelect = { viewModel.sendMessage(it) },
                                                modifier = Modifier.fillMaxWidth(),
                                                optionLabels = field.optionLabels
                                            )
                                        }
                                        Spacer(Modifier.height(40.dp))
                                    }
                                } else {
                                    // Question text, shared by every inline answer type.
                                    val questionHeader = @Composable {
                                        Text(
                                            text = displayText,
                                            style = MaterialTheme.typography.headlineMedium.copy(
                                                fontWeight = FontWeight.Normal,
                                                fontSize = 34.sp,
                                                lineHeight = 44.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    // Inline answer area — signature / handwriting / typed text.
                                    when {
                                        field != null && field.fieldType == FieldType.SIGNATURE -> {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 48.dp),
                                                horizontalAlignment = Alignment.Start
                                            ) {
                                                questionHeader()
                                                Spacer(Modifier.height(36.dp))
                                                SignatureCapture(
                                                    onSignatureCaptured = { bitmap ->
                                                        viewModel.submitSignature(bitmap)
                                                    },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }

                                        showHandwriting -> {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 48.dp),
                                                horizontalAlignment = Alignment.Start
                                            ) {
                                                questionHeader()
                                                Spacer(Modifier.height(36.dp))
                                                HandwritingInput(
                                                    language = state.userLanguage,
                                                    onTextRecognized = { text ->
                                                        messageText = if (messageText.isBlank()) text else "$messageText $text"
                                                    },
                                                    onSwitchToKeyboard = { showHandwriting = false },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }

                                        else -> {
                                            // Typeform underline text input. Top-anchored,
                                            // scrollable and keyboard-padded so the field and
                                            // its underline stay above the on-screen keyboard —
                                            // the screen is short in landscape, so a vertically
                                            // centered field would be covered while typing.
                                            // (Signature/handwriting stay un-scrolled above so
                                            // their drawing gestures aren't stolen by scroll.)
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .verticalScroll(rememberScrollState())
                                                    .imePadding()
                                                    .padding(horizontal = 48.dp),
                                                horizontalAlignment = Alignment.Start
                                            ) {
                                                Spacer(Modifier.height(8.dp))
                                                questionHeader()
                                                Spacer(Modifier.height(36.dp))
                                                TypeformTextInput(
                                                    value = messageText,
                                                    onValueChange = { messageText = it },
                                                    onSend = sendMessage,
                                                    enabled = !state.isLoadingResponse,
                                                    focusRequester = focusRequester,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                Spacer(Modifier.height(24.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        else -> {
                            Text(
                                stringResource(R.string.starting_intake),
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
                    // Any field WITHOUT options gets the typed-answer OK button. A
                    // multi_select normally has options (handled by BigMultiSelect, so
                    // no OK here); but if one is ever authored with no options it would
                    // otherwise render with no way to submit — fall back to text input
                    // so the patient can always advance.
                    val isTextInput = field == null || field.options.isEmpty()
                    val isMultiSelect = field?.fieldType == FieldType.MULTI_SELECT

                    // Show the bar only when it carries an action: the typed-answer
                    // OK button, or Continue/None for multi-select (which would
                    // otherwise strand the patient below a long, scrollable option
                    // list). Single-select option taps auto-advance — no bar needed.
                    if ((isTextInput && !showHandwriting) || isMultiSelect) {
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
                            if (isMultiSelect) {
                                // Selections are persisted to field.value on every
                                // tap, so this always-visible bar reflects the live
                                // count and submits the same canonical CSV the option
                                // grid produced. This is the patient's reliable way to
                                // advance — the grid itself has no submit button.
                                val selectedCount = field?.value
                                    ?.split(",")?.map { it.trim() }?.count { it.isNotBlank() } ?: 0
                                OutlinedButton(
                                    onClick = { viewModel.sendMessage("None") },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.heightIn(min = 60.dp),
                                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp)
                                ) {
                                    Text(
                                        stringResource(R.string.none),
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                Button(
                                    onClick = { field?.value?.let { viewModel.sendMessage(it) } },
                                    enabled = selectedCount > 0,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.heightIn(min = 60.dp),
                                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp)
                                ) {
                                    Text(
                                        if (selectedCount == 0) stringResource(R.string.confirm)
                                        else stringResource(R.string.confirm_with_count, selectedCount),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Icon(Icons.Default.Check, null, modifier = Modifier.size(22.dp))
                                }
                            } else {
                                // Typed-answer OK button
                                Button(
                                    onClick = sendMessage,
                                    enabled = messageText.isNotBlank(),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.heightIn(min = 60.dp),
                                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp)
                                ) {
                                    Text(
                                        stringResource(R.string.ok),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Icon(Icons.Default.Check, null, modifier = Modifier.size(22.dp))
                                }
                                Text(
                                    text = stringResource(R.string.press_enter),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                )
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                    }
                }
                }
            }
        }

        // ── Floating chat FAB (bottom-right) ─────────────────────────────────
        // Temporarily hidden for the demo. Flip this back to true to restore the
        // conversation panel button — all of its code below is left intact.
        val showConversationFab = false
        @Suppress("KotlinConstantConditions")
        if (showConversationFab && !chatPanelOpen && state.form != null && !state.isLoading) {
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
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = stringResource(R.string.open_conversation_history))
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
                                Icons.AutoMirrored.Filled.Chat, null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.conversation),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { chatPanelOpen = false },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close, stringResource(R.string.close),
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
                                placeholder = { Text(stringResource(R.string.ask_question)) },
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
                                Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.voice_send))
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
            fontSize = 30.sp,
            lineHeight = 38.sp
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
                            text = stringResource(R.string.type_answer),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Light,
                                fontSize = 30.sp,
                                lineHeight = 38.sp,
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
    val tapAnywhereSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier.then(
            // Tap ANYWHERE on the screen to start speaking while idle. No ripple
            // (it would flash the whole screen); the Type button still takes its
            // own taps. Only active in the Prompt phase so it never hijacks the
            // Accept/Re-record/Edit buttons or the keyboard.
            if (phase == VoicePhase.Prompt)
                Modifier.clickable(
                    interactionSource = tapAnywhereSource,
                    indication = null
                ) { onTapToSpeak() }
            else Modifier
        )
    ) {
        // While typing, use a top-anchored, keyboard-aware layout so the text
        // field and Send button stay visible above the on-screen keyboard
        // (which covers the lower half of the screen). The default voice layout
        // centers everything vertically, which pushes the field under the
        // keyboard where the patient can't see what they're typing.
        if (phase == VoicePhase.Editing) {
            VoiceTypeLayout(
                questionText = questionText,
                value = editText,
                onValueChange = onEditTextChange,
                onSend = onSendEdit,
                focusRequester = editFocusRequester,
                accentColor = blue
            )
        } else {
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
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 56.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            // Tell the patient exactly when to talk. Android's recognizer plays a
            // chime as the mic opens; this instruction is shown while the question
            // is being read, while the mic is opening, and when idle — but hidden
            // once they're reviewing or editing an answer.
            if (phase == VoicePhase.Speaking ||
                phase == VoicePhase.Listening ||
                phase == VoicePhase.Prompt
            ) {
                Spacer(Modifier.height(18.dp))
                Text(
                    text = stringResource(R.string.voice_chime_instruction),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Medium,
                        lineHeight = 30.sp
                    ),
                    color = blue.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(28.dp))

            // Animated blue arch — reacts to the mic. In Review, tapping it
            // re-records; in Prompt the whole screen is the tap target.
            val archTappable = phase == VoicePhase.Review
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
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Medium,
                                lineHeight = 40.sp
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
                            style = MaterialTheme.typography.headlineSmall,
                            color = blue.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // Accept / Re-record / Edit — after speech ends. Large, kiosk-friendly
            // touch targets so older patients can tap them easily.
            if (phase == VoicePhase.Review && finalTranscript.isNotBlank()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onAccept,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.heightIn(min = 72.dp),
                        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 18.dp)
                    ) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.voice_accept),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    OutlinedButton(
                        onClick = onRerecord,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.heightIn(min = 72.dp),
                        contentPadding = PaddingValues(horizontal = 26.dp, vertical = 18.dp)
                    ) {
                        Icon(Icons.Default.Replay, null, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.voice_rerecord),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    OutlinedButton(
                        onClick = onEdit,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.heightIn(min = 72.dp),
                        contentPadding = PaddingValues(horizontal = 26.dp, vertical = 18.dp)
                    ) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.voice_edit),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1.15f))
        }
        }

        // "Type your response" — out of the way but a large, easy tap target
        // for patients who prefer the keyboard over speaking.
        if (phase != VoicePhase.Editing) {
            OutlinedButton(
                onClick = onStartTyping,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 16.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .heightIn(min = 64.dp)
                    .padding(bottom = 24.dp)
            ) {
                Icon(
                    Icons.Default.Keyboard, null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.voice_type_response),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
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
 * Keyboard-aware layout for the typing/edit path. The question and input are
 * anchored toward the TOP of the screen instead of vertically centered, so the
 * field and Send button stay above the on-screen keyboard (which covers the
 * lower half) and the patient can see what they're typing. The column is
 * scrollable as a safety net on shorter screens, and imePadding() keeps it
 * clear of the keyboard on edge-to-edge devices.
 *
 * The question is rendered smaller here than in the voice prompt so the input
 * sits high and comfortably in view; the swap animates as the window resizes
 * when the keyboard opens.
 */
@Composable
private fun VoiceTypeLayout(
    questionText: String,
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    focusRequester: FocusRequester,
    accentColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))
        Text(
            text = questionText,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.SemiBold,
                lineHeight = 40.sp
            ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(32.dp))
        VoiceEditField(
            value = value,
            onValueChange = onValueChange,
            onSend = onSend,
            focusRequester = focusRequester,
            accentColor = accentColor
        )
        Spacer(Modifier.height(40.dp))
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
            textStyle = MaterialTheme.typography.headlineMedium.copy(
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
                                style = MaterialTheme.typography.headlineMedium.copy(
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
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onSend,
            enabled = value.isNotBlank(),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .heightIn(min = 64.dp),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp)
        ) {
            Text(
                stringResource(R.string.voice_send),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(10.dp))
            Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(24.dp))
        }
    }
}

// ─── Big option buttons (radio / dropdown) ────────────────────────────────────

/**
 * One large, tappable option button. Fills the width given to it (via weight)
 * and is tall and clean, sized for kiosk touch. Highlights when [selected].
 */
@Composable
private fun BigOptionButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.heightIn(min = 104.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Lays option buttons out as large, equal-width buttons that fill the row.
 * Few options sit in a single row; more wrap into a clean 2-column grid.
 */
@Composable
private fun BigOptionButtons(
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    optionLabels: List<String> = emptyList()
) {
    val cols = if (options.size <= 3) options.size.coerceAtLeast(1) else 2
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        options.indices.chunked(cols).forEach { rowIndices ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowIndices.forEach { i ->
                    val canonical = options[i]
                    // Display the localized label, but always submit the canonical value.
                    val display = optionLabels.getOrNull(i)?.takeIf { it.isNotBlank() } ?: canonical
                    BigOptionButton(
                        text = display,
                        selected = false,
                        onClick = { onSelect(canonical) },
                        modifier = Modifier.weight(1f)
                    )
                }
                // Keep buttons uniformly sized when the last row is partial.
                repeat(cols - rowIndices.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ─── Big multi-select (toggleable) ────────────────────────────────────────────

@Composable
private fun BigMultiSelect(
    field: com.medpull.kiosk.data.models.FormField,
    viewModel: GuidedIntakeViewModel,
    modifier: Modifier = Modifier
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
    val cols = if (field.options.size <= 3) field.options.size.coerceAtLeast(1) else 2

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.select_all_apply),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        field.options.indices.chunked(cols).forEach { rowIndices ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowIndices.forEach { i ->
                    // Selections are tracked and submitted by canonical English value;
                    // the button shows the localized label.
                    val canonical = field.options[i]
                    val display = field.optionLabels.getOrNull(i)?.takeIf { it.isNotBlank() } ?: canonical
                    val selected = canonical in currentSelections.value
                    BigOptionButton(
                        text = display,
                        selected = selected,
                        onClick = {
                            val updated = currentSelections.value.toMutableSet()
                            if (selected) updated.remove(canonical) else updated.add(canonical)
                            currentSelections.value = updated
                            viewModel.updateMultiSelectField(field.id, updated.joinToString(", "))
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(cols - rowIndices.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        // Submit (Continue / None) lives in the always-visible bottom bar so the
        // patient is never stranded below this scrollable option list.
        Spacer(Modifier.height(8.dp))
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
                    text = stringResource(R.string.consent_authorizations),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.consent_review_prompt),
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
                    Text(stringResource(R.string.agree_to_all))
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
                        text = stringResource(
                            R.string.consent_progress,
                            fields.count { it.id in selections },
                            fields.size
                        ),
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
                    Text(stringResource(R.string.continue_action))
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(18.dp))
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
                text = field.translatedText ?: field.fieldName,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                field.options.indices.forEach { i ->
                    // Chips display localized labels; selection keys off canonical values.
                    val canonical = field.options[i]
                    val display = field.optionLabels.getOrNull(i)?.takeIf { it.isNotBlank() } ?: canonical
                    FilterChip(
                        selected = selectedOption == canonical,
                        onClick = { if (enabled) onOptionSelected(canonical) },
                        label = { Text(display, style = MaterialTheme.typography.bodySmall) }
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
            Text(stringResource(R.string.loading_form), style = MaterialTheme.typography.bodyLarge)
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
            .padding(horizontal = 48.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.welcome_back),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.prefill_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            items(fields.size) { index ->
                val field = fields[index]
                val isEditing = editingFieldId == field.id

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = field.translatedText ?: field.fieldName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                            Spacer(Modifier.height(6.dp))
                            if (isEditing) {
                                OutlinedTextField(
                                    value = editingValue,
                                    onValueChange = { editingValue = it },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = MaterialTheme.typography.titleMedium,
                                    shape = MaterialTheme.shapes.large,
                                    keyboardActions = KeyboardActions(onDone = {
                                        onFieldEdit(field.id, editingValue)
                                        editingFieldId = null
                                    })
                                )
                            } else {
                                Text(
                                    text = field.value ?: "—",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                        if (isEditing) {
                            IconButton(onClick = {
                                onFieldEdit(field.id, editingValue)
                                editingFieldId = null
                            }) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = stringResource(R.string.save),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            IconButton(onClick = {
                                editingFieldId = field.id
                                editingValue = field.value ?: ""
                            }) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.enter_value),
                                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }

                if (index < fields.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Text(
                text = stringResource(R.string.prefill_confirm),
                style = MaterialTheme.typography.titleLarge
            )
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onStartFresh,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            shape = MaterialTheme.shapes.large
        ) {
            Text(
                text = stringResource(R.string.prefill_start_fresh),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
    }
}
