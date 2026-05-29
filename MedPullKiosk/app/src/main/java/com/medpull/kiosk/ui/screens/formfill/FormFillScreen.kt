package com.medpull.kiosk.ui.screens.formfill

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.medpull.kiosk.R
import com.medpull.kiosk.data.models.FieldType
import com.medpull.kiosk.data.models.FormField
import com.medpull.kiosk.ui.components.ActivityTracker
import com.medpull.kiosk.ui.components.InteractivePdfViewer
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.intl.LocaleList
import androidx.core.content.ContextCompat
import com.medpull.kiosk.ui.screens.ai.AiChatDialog
import com.medpull.kiosk.ui.screens.ai.HandwritingInput
import com.medpull.kiosk.utils.SessionManager
import java.io.File

/**
 * Form fill screen — full-screen interactive PDF viewer with translation overlays.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormFillScreen(
    sessionManager: SessionManager,
    onNavigateBack: () -> Unit,
    onExport: (String) -> Unit = {},
    viewModel: FormFillViewModel = hiltViewModel()
) {
    ActivityTracker(sessionManager = sessionManager)

    val state by viewModel.state.collectAsState()
    var showAiChat by remember { mutableStateOf(false) }

    // Zoom/pan state — owned here so both gestures and buttons can modify it
    var userScale by remember(state.currentPage) { mutableFloatStateOf(1f) }
    var userOffsetX by remember(state.currentPage) { mutableFloatStateOf(0f) }
    var userOffsetY by remember(state.currentPage) { mutableFloatStateOf(0f) }

    // Handle navigation back
    LaunchedEffect(state.shouldNavigateBack) {
        if (state.shouldNavigateBack) {
            onNavigateBack()
            viewModel.resetNavigation()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.form?.fileName ?: "Form",
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (state.fields.isNotEmpty()) {
                            Text(
                                text = "${state.completionPercentage.toInt()}% Complete",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.saveAndExit() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleFieldOverlays() }) {
                        Icon(
                            imageVector = if (state.showFieldOverlays) {
                                Icons.Default.Visibility
                            } else {
                                Icons.Default.VisibilityOff
                            },
                            contentDescription = "Toggle overlays"
                        )
                    }
                    if (state.completionPercentage >= 100f) {
                        IconButton(onClick = { state.form?.let { onExport(it.id) } }) {
                            Icon(Icons.Default.FileDownload, contentDescription = "Export")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            if (!state.isLoading && state.form != null) {
                FloatingActionButton(
                    onClick = { showAiChat = true },
                    containerColor = MaterialTheme.colorScheme.tertiary
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = stringResource(R.string.ai_copilot)
                    )
                }
            }
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
                    // Full-screen interactive PDF viewer
                    InteractivePdfViewer(
                        pdfFile = File(state.form!!.originalFileUri),
                        currentPage = state.currentPage,
                        fields = state.fields,
                        showOverlays = state.showFieldOverlays,
                        userScale = userScale,
                        userOffsetX = userOffsetX,
                        userOffsetY = userOffsetY,
                        onTransformChanged = { scale, offsetX, offsetY ->
                            userScale = scale
                            userOffsetX = offsetX
                            userOffsetY = offsetY
                        },
                        onFieldClick = { field ->
                            // Only open dialog for non-checkbox fields
                            if (field.fieldType != FieldType.CHECKBOX) {
                                viewModel.selectField(field)
                            }
                        },
                        onCheckboxToggle = { field ->
                            val current = field.value
                            val newValue = if (current == "true" || current == "checked") "" else "true"
                            viewModel.updateFieldValue(field.id, newValue)
                        },
                        onPageCountLoaded = { count ->
                            viewModel.setTotalPages(count)
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Zoom +/- buttons (bottom-end)
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 130.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SmallFloatingActionButton(
                            onClick = { userScale = (userScale * 1.5f).coerceAtMost(5f) },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Zoom in")
                        }
                        SmallFloatingActionButton(
                            onClick = { userScale = (userScale / 1.5f).coerceAtLeast(0.5f) },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Zoom out")
                        }
                    }

                    // Page navigation controls (only for multi-page PDFs)
                    if (state.totalPages > 1) {
                        PageNavigationBar(
                            currentPage = state.currentPage,
                            totalPages = state.totalPages,
                            onPreviousPage = { viewModel.setCurrentPage(state.currentPage - 1) },
                            onNextPage = { viewModel.setCurrentPage(state.currentPage + 1) },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 68.dp)
                        )
                    }

                    // Prominent "Create New Form" button at the bottom
                    if (state.fields.any { !it.value.isNullOrBlank() }) {
                        Button(
                            onClick = { viewModel.generateNewForm() },
                            enabled = !state.isGeneratingForm,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(vertical = 14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PostAdd,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Create New Form",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
                }
            }

            // Progress indicator at bottom
            if (!state.isLoading && state.form != null) {
                LinearProgressIndicator(
                    progress = { state.completionPercentage / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Error snackbar
            state.error?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text(stringResource(R.string.ok))
                        }
                    }
                ) {
                    Text(error)
                }
            }
        }
    }

    // Field input dialog (non-checkbox fields only)
    state.selectedField?.let { field ->
        if (field.fieldType != FieldType.CHECKBOX) {
            FieldInputDialog(
                field = field,
                language = state.userLanguage,
                onDismiss = { viewModel.clearFieldSelection() },
                onSave = { value ->
                    viewModel.updateFieldValue(field.id, value)
                }
            )
        }
    }

    // AI Chat Dialog
    if (showAiChat) {
        AiChatDialog(
            onDismiss = { showAiChat = false },
            language = state.userLanguage,
            formContext = state.form?.fileName,
            formFields = state.fields
        )
    }

    // Generate Form result dialog — with PDF preview + export options
    if (state.isGeneratingForm || state.generatedFormPath != null || state.generatedFormError != null) {
        GenerateFormDialog(
            isGenerating = state.isGeneratingForm,
            generatedPath = state.generatedFormPath,
            error = state.generatedFormError,
            isExporting = state.isExportingGeneratedForm,
            exportSuccess = state.generatedFormExportSuccess,
            onExportToCloud = { viewModel.exportGeneratedFormToS3() },
            onExportToLocal = { viewModel.exportGeneratedFormToLocal() },
            onDismiss = { viewModel.clearGeneratedFormState() }
        )
    }
}

@Composable
private fun PageNavigationBar(
    currentPage: Int,
    totalPages: Int,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 4.dp,
        shadowElevation = 4.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            IconButton(
                onClick = onPreviousPage,
                enabled = currentPage > 0
            ) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous page")
            }

            Text(
                text = "Page ${currentPage + 1} of $totalPages",
                style = MaterialTheme.typography.bodyMedium
            )

            IconButton(
                onClick = onNextPage,
                enabled = currentPage < totalPages - 1
            ) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Next page")
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.loading),
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
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun FieldInputDialog(
    field: FormField,
    language: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val context = LocalContext.current
    var value by remember { mutableStateOf(field.value ?: "") }
    var showHandwriting by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }

    // Speech-to-text
    val speechAvailable = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    val speechRecognizer = remember {
        if (speechAvailable) SpeechRecognizer.createSpeechRecognizer(context) else null
    }
    val onSpeechResult = rememberUpdatedState { text: String ->
        value = if (value.isBlank()) text else "$value $text"
    }
    val speechLocale = remember(language) {
        when (language) {
            "es" -> "es-ES"
            "zh" -> "zh-CN"
            "fr" -> "fr-FR"
            "ja" -> "ja-JP"
            "pt" -> "pt-BR"
            "ar" -> "ar-SA"
            "ru" -> "ru-RU"
            else -> "en-US"
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
    ) { granted ->
        if (granted) startListening()
    }

    DisposableEffect(speechRecognizer) {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) onSpeechResult.value(text)
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

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = field.translatedText ?: field.fieldName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (field.originalText != null && field.translatedText != null) {
                    Text(
                        text = field.originalText!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.enter_value)) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = when (field.fieldType) {
                            FieldType.NUMBER -> KeyboardType.Number
                            FieldType.DATE -> KeyboardType.Number
                            else -> KeyboardType.Text
                        },
                        hintLocales = LocaleList(speechLocale)
                    ),
                    singleLine = field.fieldType != FieldType.TEXT
                )

                // Handwriting & speech-to-text input toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    IconButton(
                        onClick = { showHandwriting = !showHandwriting }
                    ) {
                        Icon(
                            imageVector = if (showHandwriting) Icons.Default.Keyboard else Icons.Default.Draw,
                            contentDescription = if (showHandwriting) "Switch to keyboard" else "Handwriting input",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

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
                            }
                        ) {
                            Icon(
                                imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = if (isListening) "Stop listening" else "Speech to text",
                                tint = if (isListening) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Handwriting input panel
                if (showHandwriting) {
                    HandwritingInput(
                        language = language,
                        onTextRecognized = { text ->
                            value = if (value.isBlank()) text else "$value $text"
                        },
                        onSwitchToKeyboard = { showHandwriting = false }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(onClick = { onSave(value) }) {
                        Text(stringResource(R.string.save))
                    }
                }
            }
        }
    }
}

@Composable
private fun GenerateFormDialog(
    isGenerating: Boolean,
    generatedPath: String?,
    error: String?,
    isExporting: Boolean,
    exportSuccess: String?,
    onExportToCloud: () -> Unit,
    onExportToLocal: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = { if (!isGenerating && !isExporting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when {
                    // Loading state
                    isGenerating -> {
                        Spacer(modifier = Modifier.height(24.dp))
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Generating form...",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    // Error state
                    error != null && generatedPath == null -> {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = onDismiss) {
                            Text(stringResource(R.string.ok))
                        }
                    }

                    // Success state — PDF preview + export options
                    generatedPath != null -> {
                        // Title
                        Text(
                            text = "Form Generated",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // PDF preview
                        PdfPreviewImage(
                            pdfFile = File(generatedPath),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Export success banner
                        if (exportSuccess != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = exportSuccess,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // Export error banner
                        if (error != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Error,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = error,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // Export options
                        if (isExporting) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Exporting...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            // Cloud upload card
                            ExportOptionCard(
                                icon = Icons.Default.CloudUpload,
                                title = "Export to Cloud",
                                description = "Upload to your secure cloud storage",
                                onClick = onExportToCloud,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            // Local save card
                            ExportOptionCard(
                                icon = Icons.Default.SaveAlt,
                                title = "Save to Device",
                                description = "Save a copy to your Documents folder",
                                onClick = onExportToLocal,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Dismiss button
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isExporting
                        ) {
                            Text("Done")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportOptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun PdfPreviewImage(pdfFile: File, modifier: Modifier = Modifier) {
    val bitmap = remember(pdfFile.absolutePath) {
        try {
            val fd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fd)
            val page = renderer.openPage(0)
            val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(android.graphics.Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            renderer.close()
            fd.close()
            bmp
        } catch (e: Exception) {
            null
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "PDF preview",
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Preview unavailable",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}
