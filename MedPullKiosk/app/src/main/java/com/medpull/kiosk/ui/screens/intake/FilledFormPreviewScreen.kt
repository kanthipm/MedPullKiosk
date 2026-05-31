package com.medpull.kiosk.ui.screens.intake

import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.medpull.kiosk.R
import com.medpull.kiosk.ui.components.BackButton
import com.medpull.kiosk.ui.components.InteractivePdfViewer

/**
 * Full-screen filled form preview shown after the patient reviews and submits.
 * Displays the original PDF with answers overlaid, plus Print / Send / Done actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilledFormPreviewScreen(
    onDone: () -> Unit,
    onBack: () -> Unit = {},
    viewModel: FilledFormPreviewViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current  // still needed for print
    val filledFormFallback = stringResource(R.string.filled_form)
    val patientIntakeFormTitle = stringResource(R.string.patient_intake_form)
    val previousPageLabel = stringResource(R.string.previous_page)
    val nextPageLabel = stringResource(R.string.next_page)
    val resetZoomLabel = stringResource(R.string.reset_zoom)

    var currentPage by remember { mutableIntStateOf(0) }
    var pageCount by remember { mutableIntStateOf(1) }
    var userScale by remember { mutableFloatStateOf(1f) }
    var userOffsetX by remember { mutableFloatStateOf(0f) }
    var userOffsetY by remember { mutableFloatStateOf(0f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            state.formName.ifBlank { filledFormFallback },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (!state.isLoading && state.pdfFile != null) {
                            Text(
                                stringResource(R.string.page_of, currentPage + 1, pageCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                navigationIcon = {
                    BackButton(
                        onClick = onBack,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                },
                actions = {
                    // Page navigation
                    if (!state.isLoading && pageCount > 1) {
                        IconButton(
                            onClick = { if (currentPage > 0) { currentPage--; userScale = 1f; userOffsetX = 0f; userOffsetY = 0f } },
                            enabled = currentPage > 0
                        ) { Icon(Icons.Default.ChevronLeft, previousPageLabel) }
                        IconButton(
                            onClick = { if (currentPage < pageCount - 1) { currentPage++; userScale = 1f; userOffsetX = 0f; userOffsetY = 0f } },
                            enabled = currentPage < pageCount - 1
                        ) { Icon(Icons.Default.ChevronRight, nextPageLabel) }
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Print button
                    OutlinedButton(
                        onClick = {
                            val file = state.pdfFile ?: return@OutlinedButton
                            try {
                                val printManager = context.getSystemService(android.content.Context.PRINT_SERVICE) as PrintManager
                                val jobName = state.formName.ifBlank { patientIntakeFormTitle }
                                WebView(context).apply {
                                    webViewClient = WebViewClient()
                                    loadUrl("file://${file.absolutePath}")
                                    setWebViewClient(object : WebViewClient() {
                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            val printAdapter = createPrintDocumentAdapter(jobName)
                                            printManager.print(
                                                jobName,
                                                printAdapter,
                                                PrintAttributes.Builder().build()
                                            )
                                        }
                                    })
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("FilledFormPreview", "Print failed", e)
                            }
                        },
                        enabled = state.pdfFile != null,
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) {
                        Icon(Icons.Default.Print, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.print))
                    }

                    // Send to clinic button
                    Button(
                        onClick = { if (!state.isSent) viewModel.sendToClinic() },
                        enabled = state.pdfFile != null && !state.isSending,
                        colors = if (state.isSent)
                            ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                        else ButtonDefaults.buttonColors(),
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) {
                        if (state.isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else if (state.isSent) {
                            Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.sent))
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.send_to_clinic))
                        }
                    }

                    // Done button
                    OutlinedButton(
                        onClick = onDone,
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.done))
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.isLoading -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            stringResource(R.string.filling_answers),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                state.error != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline, null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            state.error!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(onClick = onDone) { Text(stringResource(R.string.go_back)) }
                    }
                }

                state.pdfFile != null -> {
                    // Zoom reset hint badge
                    AnimatedVisibility(
                        visible = userScale > 1.2f,
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp),
                        enter = fadeIn(), exit = fadeOut()
                    ) {
                        SmallFloatingActionButton(
                            onClick = { userScale = 1f; userOffsetX = 0f; userOffsetY = 0f },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Icon(Icons.Default.ZoomOut, resetZoomLabel, modifier = Modifier.size(18.dp))
                        }
                    }

                    InteractivePdfViewer(
                        pdfFile = state.pdfFile!!,
                        currentPage = currentPage,
                        fields = emptyList(),   // no edit overlays — the PDF already has values baked in
                        showOverlays = false,
                        userScale = userScale,
                        userOffsetX = userOffsetX,
                        userOffsetY = userOffsetY,
                        onTransformChanged = { s, ox, oy -> userScale = s; userOffsetX = ox; userOffsetY = oy },
                        onFieldClick = {},
                        onCheckboxToggle = {},
                        onPageCountLoaded = { pageCount = it },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

