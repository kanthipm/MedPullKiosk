package com.medpull.kiosk.ui.screens.ai

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.*
import kotlinx.coroutines.delay

/**
 * Handwriting input canvas with ML Kit Digital Ink Recognition.
 * Supports all app languages. Auto-recognizes after a 1.5s pause.
 */
@Composable
fun HandwritingInput(
    language: String,
    onTextRecognized: (String) -> Unit,
    onSwitchToKeyboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    var modelReady by remember { mutableStateOf(false) }
    var modelDownloading by remember { mutableStateOf(false) }
    var recognizing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Drawing state
    val completedPaths = remember { mutableStateListOf<List<Offset>>() }
    val currentPathPoints = remember { mutableStateListOf<Offset>() }

    // ML Kit ink strokes
    val inkStrokes = remember { mutableStateListOf<Ink.Stroke>() }

    // Flag to prevent recognition while user is mid-stroke
    var isDrawing by remember { mutableStateOf(false) }

    // Auto-recognition trigger (timestamp of last completed stroke)
    var lastStrokeTime by remember { mutableStateOf(0L) }

    var recognizer by remember { mutableStateOf<DigitalInkRecognizer?>(null) }

    val languageTag = remember(language) {
        when (language) {
            "es" -> "es-ES"
            "zh" -> "zh-Hans-CN"
            "fr" -> "fr-FR"
            "ja" -> "ja"
            "pt" -> "pt-BR"
            "ar" -> "ar"
            "ru" -> "ru-RU"
            else -> "en-US"
        }
    }

    // Download model when language changes
    LaunchedEffect(languageTag) {
        modelDownloading = true
        modelReady = false
        error = null
        recognizer?.close()
        recognizer = null

        val modelIdentifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag(languageTag)
        if (modelIdentifier == null) {
            error = "Language not supported for handwriting"
            modelDownloading = false
            return@LaunchedEffect
        }

        val model = DigitalInkRecognitionModel.builder(modelIdentifier).build()
        RemoteModelManager.getInstance()
            .download(model, DownloadConditions.Builder().build())
            .addOnSuccessListener {
                recognizer = DigitalInkRecognition.getClient(
                    DigitalInkRecognizerOptions.builder(model).build()
                )
                modelReady = true
                modelDownloading = false
            }
            .addOnFailureListener { e ->
                Log.e("HandwritingInput", "Model download failed", e)
                error = "Failed to download handwriting model"
                modelDownloading = false
            }
    }

    // Auto-recognize 1.5s after the last completed stroke
    LaunchedEffect(lastStrokeTime) {
        if (lastStrokeTime > 0 && inkStrokes.isNotEmpty() && modelReady) {
            delay(1500)
            if (isDrawing || inkStrokes.isEmpty()) return@LaunchedEffect

            recognizing = true
            val inkBuilder = Ink.builder()
            inkStrokes.forEach { inkBuilder.addStroke(it) }
            val ink = inkBuilder.build()

            recognizer?.recognize(ink)
                ?.addOnSuccessListener { result ->
                    val text = result.candidates.firstOrNull()?.text
                    if (!text.isNullOrBlank()) {
                        onTextRecognized(text)
                    }
                    completedPaths.clear()
                    inkStrokes.clear()
                    recognizing = false
                }
                ?.addOnFailureListener {
                    Log.e("HandwritingInput", "Recognition failed", it)
                    recognizing = false
                }
        }
    }

    DisposableEffect(Unit) {
        onDispose { recognizer?.close() }
    }

    val strokeColor = MaterialTheme.colorScheme.onSurface
    val borderColor = MaterialTheme.colorScheme.outline

    Column(modifier = modifier) {
        // Drawing canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp)
                )
                .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                .then(
                    if (modelReady) {
                        Modifier.pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                down.consume()
                                isDrawing = true

                                val strokeBuilder = Ink.Stroke.builder()
                                strokeBuilder.addPoint(
                                    Ink.Point.create(
                                        down.position.x,
                                        down.position.y,
                                        System.currentTimeMillis()
                                    )
                                )
                                currentPathPoints.clear()
                                currentPathPoints.add(down.position)

                                do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    if (change.pressed) {
                                        strokeBuilder.addPoint(
                                            Ink.Point.create(
                                                change.position.x,
                                                change.position.y,
                                                System.currentTimeMillis()
                                            )
                                        )
                                        currentPathPoints.add(change.position)
                                        change.consume()
                                    }
                                } while (event.changes.any { it.pressed })

                                inkStrokes.add(strokeBuilder.build())
                                completedPaths.add(currentPathPoints.toList())
                                currentPathPoints.clear()
                                isDrawing = false
                                lastStrokeTime = System.currentTimeMillis()
                            }
                        }
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                val strokeStyle = Stroke(
                    width = 4.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
                for (points in completedPaths) {
                    if (points.size >= 2) {
                        val path = Path().apply {
                            moveTo(points[0].x, points[0].y)
                            for (i in 1 until points.size) {
                                lineTo(points[i].x, points[i].y)
                            }
                        }
                        drawPath(path, strokeColor, style = strokeStyle)
                    }
                }
                // Current in-progress stroke
                if (currentPathPoints.size >= 2) {
                    val path = Path().apply {
                        moveTo(currentPathPoints[0].x, currentPathPoints[0].y)
                        for (i in 1 until currentPathPoints.size) {
                            lineTo(currentPathPoints[i].x, currentPathPoints[i].y)
                        }
                    }
                    drawPath(path, strokeColor, style = strokeStyle)
                }
            }

            // Overlay states
            if (modelDownloading) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Downloading handwriting model...",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (recognizing) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }

            if (modelReady && completedPaths.isEmpty() && currentPathPoints.isEmpty() && !recognizing) {
                Text(
                    "Write here",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }

            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Buttons row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(
                onClick = {
                    completedPaths.clear()
                    currentPathPoints.clear()
                    inkStrokes.clear()
                }
            ) {
                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Clear")
            }

            TextButton(onClick = onSwitchToKeyboard) {
                Icon(Icons.Default.Keyboard, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Keyboard")
            }
        }
    }
}
