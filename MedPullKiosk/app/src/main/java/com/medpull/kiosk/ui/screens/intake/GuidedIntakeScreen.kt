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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.medpull.kiosk.R
import com.medpull.kiosk.ui.screens.ai.ChatMessage
import com.medpull.kiosk.ui.screens.ai.HandwritingInput
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Guided intake conversational screen
 * Shows questions one-by-one with chat interface for natural language responses
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuidedIntakeScreen(
    onNavigateBack: () -> Unit,
    onComplete: () -> Unit,
    viewModel: GuidedIntakeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var showHandwriting by remember { mutableStateOf(false) }

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
    var speakingTimestamp by remember { mutableStateOf(-1L) }
    val tts = remember {
        TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            tts.shutdown()
        }
    }

    LaunchedEffect(state.userLanguage, ttsReady) {
        if (ttsReady) {
            val locale = when (state.userLanguage) {
                "es" -> Locale("es")
                "zh" -> Locale.CHINESE
                "fr" -> Locale.FRENCH
                "hi" -> Locale("hi")
                "ar" -> Locale("ar")
                else -> Locale.ENGLISH
            }
            tts.language = locale
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { speakingTimestamp = -1L }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { speakingTimestamp = -1L }
            })
        }
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(state.chatMessages.size) {
        if (state.chatMessages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(state.chatMessages.size - 1)
            }
        }
    }

    // Handle completion
    LaunchedEffect(state.isComplete) {
        if (state.isComplete) {
            onComplete()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = state.form?.fileName ?: "Guided Intake",
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (state.totalRequiredCount > 0) {
                            val progress = state.filledRequiredCount.toFloat() / state.totalRequiredCount
                            Text(
                                text = "${state.filledRequiredCount} of ${state.totalRequiredCount} required fields",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                state.isLoading -> {
                    LoadingState()
                }
                state.form == null -> {
                    ErrorState(message = state.error ?: "Form not found")
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Chat messages area
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            if (state.chatMessages.isEmpty() && !state.isLoadingResponse) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Tell us your answer",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(state.chatMessages) { message ->
                                        ChatBubble(
                                            message = message,
                                            isSpeaking = speakingTimestamp == message.timestamp,
                                            onSpeak = if (!message.isFromUser && ttsReady) {
                                                {
                                                    if (speakingTimestamp == message.timestamp) {
                                                        tts.stop()
                                                        speakingTimestamp = -1L
                                                    } else {
                                                        tts.stop()
                                                        tts.speak(
                                                            message.text,
                                                            TextToSpeech.QUEUE_FLUSH,
                                                            null,
                                                            message.timestamp.toString()
                                                        )
                                                        speakingTimestamp = message.timestamp
                                                    }
                                                }
                                            } else null
                                        )
                                    }

                                    if (state.isLoadingResponse) {
                                        item {
                                            TypingIndicator()
                                        }
                                    }
                                }
                            }
                        }

                        // Error message
                        state.error?.let { error ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Error,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = error,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { viewModel.clearError() }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Dismiss",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Handwriting input panel (shown above input card when active)
                        if (showHandwriting) {
                            HandwritingInput(
                                language = state.userLanguage,
                                onTextRecognized = { text ->
                                    messageText = if (messageText.isBlank()) text else "$messageText $text"
                                },
                                onSwitchToKeyboard = { showHandwriting = false },
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        // Input area
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
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
                                    placeholder = { Text("Type your answer...") },
                                    enabled = !state.isLoadingResponse,
                                    maxLines = 3
                                )

                                // Handwriting toggle
                                IconButton(onClick = { showHandwriting = !showHandwriting }) {
                                    Icon(
                                        imageVector = if (showHandwriting) Icons.Default.Keyboard else Icons.Default.Draw,
                                        contentDescription = if (showHandwriting) "Keyboard" else "Write by hand",
                                        tint = if (showHandwriting) MaterialTheme.colorScheme.primary
                                               else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Speech-to-text
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
                                            imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                                            contentDescription = if (isListening) "Stop" else "Speak your answer",
                                            tint = if (isListening) MaterialTheme.colorScheme.error
                                                   else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // Send
                                IconButton(
                                    onClick = {
                                        if (messageText.isNotBlank()) {
                                            viewModel.sendMessage(messageText)
                                            messageText = ""
                                            showHandwriting = false
                                        }
                                    },
                                    enabled = messageText.isNotBlank() && !state.isLoadingResponse
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Send,
                                        contentDescription = "Send",
                                        tint = if (messageText.isNotBlank() && !state.isLoadingResponse)
                                            MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    )
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
private fun ChatBubble(
    message: ChatMessage,
    isSpeaking: Boolean = false,
    onSpeak: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (message.isFromUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 280.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (message.isFromUser) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                }
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message.isFromUser) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                    modifier = Modifier.weight(1f)
                )

                if (!message.isFromUser && onSpeak != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onSpeak,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (isSpeaking) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = "Speak",
                            modifier = Modifier.size(16.dp),
                            tint = if (message.isFromUser) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(4.dp)
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Loading form...",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun ErrorState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
    }
}
