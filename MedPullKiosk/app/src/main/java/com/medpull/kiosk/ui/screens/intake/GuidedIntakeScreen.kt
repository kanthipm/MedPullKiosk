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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.medpull.kiosk.ui.screens.ai.ChatMessage
import com.medpull.kiosk.ui.screens.ai.HandwritingInput
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Guided intake screen.
 *
 * Layout: the current AI question is displayed large and centered on the left/main area.
 * The patient types (or speaks) their answer in the input bar at the bottom.
 * A "Need Help?" button on the right edge opens a collapsible chat sidebar showing the
 * full conversation history and an extra clarification input.
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
    var chatPanelOpen by remember { mutableStateOf(false) }
    val chatListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    // Auto-show handwriting panel when on a signature field
    val isSignatureField = state.currentAskingField?.fieldType == com.medpull.kiosk.data.models.FieldType.SIGNATURE
    var showHandwriting by remember { mutableStateOf(false) }
    LaunchedEffect(state.currentAskingField?.id) {
        showHandwriting = isSignatureField
    }

    // Speech-to-text
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

    // Text-to-speech
    var ttsReady by remember { mutableStateOf(false) }
    var speakingMessageId by remember { mutableStateOf(-1L) }
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
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { speakingMessageId = -1L }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { speakingMessageId = -1L }
            })
        }
    }

    // Auto-scroll sidebar to bottom when messages arrive
    LaunchedEffect(state.chatMessages.size) {
        if (state.chatMessages.isNotEmpty()) {
            coroutineScope.launch { chatListState.animateScrollToItem(state.chatMessages.size - 1) }
        }
    }

    LaunchedEffect(state.isComplete) { if (state.isComplete) onComplete() }

    // The last AI message is the current question to display
    val currentQuestion = remember(state.chatMessages) {
        state.chatMessages.lastOrNull { !it.isFromUser }
    }

    val sendMessage: () -> Unit = {
        if (messageText.isNotBlank() && !state.isLoadingResponse) {
            viewModel.sendMessage(messageText)
            messageText = ""
            showHandwriting = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = state.form?.fileName ?: "Patient Intake",
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (state.totalCount > 0) {
                            val progress = state.filledCount.toFloat() / state.totalCount
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                state.isLoading -> LoadingState()
                state.form == null -> ErrorState(message = state.error ?: "Form not found")
                else -> {
                    Row(modifier = Modifier.fillMaxSize()) {

                        // ── Main question area ────────────────────────────────────
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            // Consent batch panel — shown instead of chat Q&A for consent fields
                            val consentBatch = state.consentBatchFields
                            if (consentBatch != null) {
                                ConsentBatchPanel(
                                    fields = consentBatch,
                                    isLoading = state.isLoadingResponse,
                                    onSubmit = { answers -> viewModel.submitConsentBatch(answers) },
                                    modifier = Modifier.weight(1f).fillMaxWidth()
                                )
                                return@Column
                            }

                            // Error banner
                            state.error?.let { error ->
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Error, null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
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

                            // Center: big question
                            Box(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                when {
                                    state.isLoadingResponse -> {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            CircularProgressIndicator(modifier = Modifier.size(40.dp))
                                            Spacer(Modifier.height(16.dp))
                                            Text(
                                                "Processing your answer...",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                    currentQuestion != null -> {
                                        val currentAskingField = state.currentAskingField
                                        // Strip trailing parentheticals from question text
                                        val displayText = currentQuestion.text
                                            .replace(Regex("\\s*\\([^)]{0,80}\\)\\s*$"), "").trim()

                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(horizontal = 48.dp, vertical = 24.dp)
                                        ) {
                                            // Speak button
                                            if (ttsReady) {
                                                IconButton(
                                                    onClick = {
                                                        if (speakingMessageId == currentQuestion.timestamp) {
                                                            tts.stop(); speakingMessageId = -1L
                                                        } else {
                                                            tts.stop()
                                                            tts.speak(
                                                                displayText,
                                                                TextToSpeech.QUEUE_FLUSH, null,
                                                                currentQuestion.timestamp.toString()
                                                            )
                                                            speakingMessageId = currentQuestion.timestamp
                                                        }
                                                    },
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .background(
                                                            MaterialTheme.colorScheme.primaryContainer,
                                                            shape = CircleShape
                                                        )
                                                ) {
                                                    Icon(
                                                        imageVector = if (speakingMessageId == currentQuestion.timestamp)
                                                            Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                                        contentDescription = "Read aloud",
                                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }
                                                Spacer(Modifier.height(24.dp))
                                            }

                                            Text(
                                                text = displayText,
                                                style = MaterialTheme.typography.headlineMedium.copy(
                                                    fontWeight = FontWeight.Normal,
                                                    fontSize = 28.sp,
                                                    lineHeight = 40.sp
                                                ),
                                                textAlign = TextAlign.Center,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )

                                            // Description / hint line from schema ai_note
                                            val description = currentAskingField?.description
                                            if (!description.isNullOrBlank()) {
                                                Spacer(Modifier.height(12.dp))
                                                Text(
                                                    text = description,
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontSize = 14.sp
                                                    ),
                                                    textAlign = TextAlign.Center,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                                    modifier = Modifier.padding(horizontal = 16.dp)
                                                )
                                            }
                                        }
                                    }
                                    else -> {
                                        Text(
                                            "Starting your intake...",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                        )
                                    }
                                }
                            }

                            // Handwriting panel (above input)
                            if (showHandwriting) {
                                HandwritingInput(
                                    language = state.userLanguage,
                                    onTextRecognized = { text ->
                                        messageText = if (messageText.isBlank()) text else "$messageText $text"
                                    },
                                    onSwitchToKeyboard = { showHandwriting = false },
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }

                            // Option chips — RADIO/DROPDOWN: horizontal scroll row
                            //                MULTI_SELECT:     vertical chip grid + confirm
                            val optionField = state.currentAskingField
                            if (optionField != null && optionField.options.isNotEmpty() && !state.isLoadingResponse) {
                                if (optionField.fieldType == com.medpull.kiosk.data.models.FieldType.MULTI_SELECT) {
                                    // Multi-select: vertical list of toggle chips + Confirm + None
                                    val currentSelections = remember(optionField.id, optionField.value) {
                                        mutableStateOf(
                                            optionField.value
                                                ?.split(",")
                                                ?.map { it.trim() }
                                                ?.filter { it.isNotBlank() }
                                                ?.toMutableSet()
                                                ?: mutableSetOf()
                                        )
                                    }
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                                            Text(
                                                "Select all that apply:",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            optionField.options.forEach { option ->
                                                val selected = option in currentSelections.value
                                                FilterChip(
                                                    selected = selected,
                                                    onClick = {
                                                        val updated = currentSelections.value.toMutableSet()
                                                        if (selected) updated.remove(option) else updated.add(option)
                                                        currentSelections.value = updated
                                                        viewModel.updateMultiSelectField(
                                                            optionField.id,
                                                            updated.joinToString(", ")
                                                        )
                                                    },
                                                    label = { Text(option, style = MaterialTheme.typography.bodyMedium) },
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                                )
                                            }
                                            Spacer(Modifier.height(8.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedButton(
                                                    onClick = { viewModel.sendMessage("None") },
                                                    modifier = Modifier.weight(1f)
                                                ) { Text("None") }
                                                if (currentSelections.value.isNotEmpty()) {
                                                    Button(
                                                        onClick = {
                                                            viewModel.sendMessage(
                                                                currentSelections.value.joinToString(", ")
                                                            )
                                                        },
                                                        modifier = Modifier.weight(2f)
                                                    ) {
                                                        Text("Confirm (${currentSelections.value.size})")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // RADIO / DROPDOWN: wrapping chip grid (no scroll, all visible)
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        FlowRow(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            optionField.options.forEach { option ->
                                                FilterChip(
                                                    selected = false,
                                                    onClick = { viewModel.sendMessage(option) },
                                                    label = {
                                                        Text(option, style = MaterialTheme.typography.bodyMedium)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Answer input bar
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 4.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.Bottom
                                    ) {
                                        OutlinedTextField(
                                            value = messageText,
                                            onValueChange = { messageText = it },
                                            modifier = Modifier.weight(1f),
                                            placeholder = {
                                                Text(
                                                    "Type your answer here...",
                                                    style = MaterialTheme.typography.bodyLarge
                                                )
                                            },
                                            enabled = !state.isLoadingResponse,
                                            maxLines = 4,
                                            textStyle = MaterialTheme.typography.bodyLarge,
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                            keyboardActions = KeyboardActions(onSend = { sendMessage() })
                                        )
                                        Spacer(Modifier.width(8.dp))

                                        // Handwriting
                                        IconButton(onClick = { showHandwriting = !showHandwriting }) {
                                            Icon(
                                                if (showHandwriting) Icons.Default.Keyboard else Icons.Default.Draw,
                                                contentDescription = if (showHandwriting) "Keyboard" else "Write",
                                                tint = if (showHandwriting) MaterialTheme.colorScheme.primary
                                                       else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        // Mic
                                        if (speechAvailable) {
                                            IconButton(
                                                onClick = {
                                                    if (isListening) {
                                                        speechRecognizer?.stopListening(); isListening = false
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
                                                    tint = if (isListening) MaterialTheme.colorScheme.error
                                                           else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        // Send
                                        FilledIconButton(
                                            onClick = sendMessage,
                                            enabled = messageText.isNotBlank() && !state.isLoadingResponse
                                        ) {
                                            Icon(Icons.Default.Send, contentDescription = "Send")
                                        }
                                    }
                                }
                            }
                        }

                        // ── Chat panel toggle tab ─────────────────────────────────
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(if (chatPanelOpen) 0.dp else 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!chatPanelOpen) {
                                FilledTonalButton(
                                    onClick = { chatPanelOpen = true },
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp),
                                    shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Default.Chat,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "Help",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }

                        // ── Chat sidebar ──────────────────────────────────────────
                        AnimatedVisibility(
                            visible = chatPanelOpen,
                            enter = slideInHorizontally(initialOffsetX = { it }),
                            exit = slideOutHorizontally(targetOffsetX = { it })
                        ) {
                            Surface(
                                modifier = Modifier.width(360.dp).fillMaxHeight(),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                tonalElevation = 2.dp
                            ) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    // Header
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Chat,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.size(20.dp)
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
                                                    Icons.Default.Close,
                                                    contentDescription = "Close",
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

                                    // Clarification input in sidebar
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(8.dp),
                                            verticalAlignment = Alignment.Bottom
                                        ) {
                                            OutlinedTextField(
                                                value = messageText,
                                                onValueChange = { messageText = it },
                                                modifier = Modifier.weight(1f),
                                                placeholder = { Text("Ask for clarification...") },
                                                enabled = !state.isLoadingResponse,
                                                maxLines = 3,
                                                textStyle = MaterialTheme.typography.bodyMedium
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            FilledIconButton(
                                                onClick = sendMessage,
                                                enabled = messageText.isNotBlank() && !state.isLoadingResponse
                                            ) {
                                                Icon(Icons.Default.Send, contentDescription = "Send")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

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
    val selections = remember(fields) {
        mutableStateMapOf<String, String>()
    }
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
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
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
                    enabled = allAnswered && !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Continue")
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
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
