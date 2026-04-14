package com.medpull.kiosk.ui.screens.intake

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.medpull.kiosk.data.models.FieldType
import com.medpull.kiosk.ui.screens.ai.ChatMessage
import com.medpull.kiosk.ui.screens.ai.HandwritingInput
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Typeform-style guided intake screen.
 *
 * One question at a time, vertically centered. The answer input lives directly
 * below the question text — not in a separate bottom bar. Slide-up animation
 * between questions. "press Enter ↵" affordance. Thin progress line at top.
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
        // Auto-focus text input when question changes (non-chip, non-signature fields)
        val f = state.currentAskingField
        if (f != null && f.options.isEmpty() && !isSignatureField) {
            try { focusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    // ── Speech-to-text ────────────────────────────────────────────────────────
    val speechAvailable = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    var isListening by remember { mutableStateOf(false) }
    val speechRecognizer = remember {
        if (speechAvailable) SpeechRecognizer.createSpeechRecognizer(context) else null
    }
    val speechLocale = remember(state.userLanguage) {
        when (state.userLanguage) {
            "es" -> "es-ES"; "zh" -> "zh-CN"; "fr" -> "fr-FR"
            "hi" -> "hi-IN"; "ar" -> "ar-SA"; else -> "en-US"
        }
    }
    val startListening: () -> Unit = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, speechLocale)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizer?.startListening(intent)
        isListening = true
    }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startListening() }

    DisposableEffect(speechRecognizer) {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    messageText = if (messageText.isBlank()) text else "$messageText $text"
                }
                isListening = false
            }
            override fun onError(error: Int) { isListening = false }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        onDispose { speechRecognizer?.destroy() }
    }

    // ── Text-to-speech ────────────────────────────────────────────────────────
    var ttsReady by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    val tts = remember {
        TextToSpeech(context) { status -> ttsReady = status == TextToSpeech.SUCCESS }
    }
    DisposableEffect(Unit) { onDispose { tts.shutdown() } }
    LaunchedEffect(state.userLanguage, ttsReady) {
        if (ttsReady) {
            val locale = when (state.userLanguage) {
                "es" -> Locale("es"); "zh" -> Locale.CHINESE
                "fr" -> Locale.FRENCH; "hi" -> Locale("hi"); "ar" -> Locale("ar")
                else -> Locale.ENGLISH
            }
            tts.language = locale
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { isSpeaking = true }
                override fun onDone(utteranceId: String?) { isSpeaking = false }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { isSpeaking = false }
            })
        }
    }

    LaunchedEffect(state.isComplete) { if (state.isComplete) onComplete() }

    val currentQuestion: ChatMessage? = remember(state.chatMessages) {
        state.chatMessages.lastOrNull { !it.isFromUser }
    }

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

        // Subtle back button + form name (top-left)
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.Default.ArrowBack, "Back",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                )
            }
            Text(
                text = state.form?.fileName ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
            )
        }

        when {
            state.isLoading -> LoadingState()
            state.form == null -> ErrorState(state.error ?: "Form not found")
            else -> {
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

                                    // Description hint (ai_note from schema)
                                    val description = field?.description
                                    if (!description.isNullOrBlank()) {
                                        Spacer(Modifier.height(10.dp))
                                        Text(
                                            text = description,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                        )
                                    }

                                    Spacer(Modifier.height(36.dp))

                                    // Inline answer area — type varies by field
                                    when {
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
                if (!state.isLoadingResponse && currentQuestion != null && state.consentBatchFields == null) {
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
                                            speechRecognizer?.stopListening()
                                            isListening = false
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

                    // Clarification input inside sidebar
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            OutlinedTextField(
                                value = messageText,
                                onValueChange = { messageText = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Ask for clarification...") },
                                enabled = !state.isLoadingResponse,
                                maxLines = 3,
                                textStyle = MaterialTheme.typography.bodyMedium,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = {
                                    if (messageText.isNotBlank() && !state.isLoadingResponse) {
                                        viewModel.sendMessage(messageText)
                                        messageText = ""
                                    }
                                })
                            )
                            Spacer(Modifier.width(4.dp))
                            FilledIconButton(
                                onClick = {
                                    if (messageText.isNotBlank() && !state.isLoadingResponse) {
                                        viewModel.sendMessage(messageText)
                                        messageText = ""
                                    }
                                },
                                enabled = messageText.isNotBlank() && !state.isLoadingResponse
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
            if (!field.description.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = field.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
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
