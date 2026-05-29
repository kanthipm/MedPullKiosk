package com.medpull.kiosk.ui.screens.ai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.medpull.kiosk.R

/**
 * Signature capture canvas.
 *
 * Unlike HandwritingInput, this does NOT convert strokes to text.
 * The strokes are rendered in ink-pen style and exported as a Bitmap when
 * the user taps Done. The bitmap is passed to [onSignatureCaptured].
 */
@Composable
fun SignatureCapture(
    onSignatureCaptured: (Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    // Each stroke is a list of points
    val completedStrokes = remember { mutableStateListOf<List<Offset>>() }
    val currentStroke = remember { mutableStateListOf<Offset>() }
    var hasStrokes by remember { mutableStateOf(false) }

    // Capture canvas size for bitmap generation
    var canvasWidthPx by remember { mutableStateOf(0) }
    var canvasHeightPx by remember { mutableStateOf(0) }

    val strokeColor = MaterialTheme.colorScheme.onSurface
    val borderColor = MaterialTheme.colorScheme.outline
    val density = LocalDensity.current

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp)
                )
                .border(
                    width = 1.5.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(12.dp)
                )
                .pointerInput(Unit) {
                    canvasWidthPx = size.width
                    canvasHeightPx = size.height
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        currentStroke.clear()
                        currentStroke.add(down.position)
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (change.pressed) {
                                currentStroke.add(change.position)
                                change.consume()
                            }
                        } while (event.changes.any { it.pressed })
                        if (currentStroke.size >= 2) {
                            completedStrokes.add(currentStroke.toList())
                            hasStrokes = true
                        }
                        currentStroke.clear()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Canvas(
                modifier = Modifier.fillMaxSize().padding(4.dp)
            ) {
                // Record canvas size for bitmap export
                canvasWidthPx = size.width.toInt()
                canvasHeightPx = size.height.toInt()

                val style = Stroke(
                    width = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
                for (stroke in completedStrokes) {
                    if (stroke.size >= 2) {
                        val path = Path().apply {
                            moveTo(stroke[0].x, stroke[0].y)
                            for (i in 1 until stroke.size) lineTo(stroke[i].x, stroke[i].y)
                        }
                        drawPath(path, strokeColor, style = style)
                    }
                }
                // In-progress stroke
                if (currentStroke.size >= 2) {
                    val path = Path().apply {
                        moveTo(currentStroke[0].x, currentStroke[0].y)
                        for (i in 1 until currentStroke.size) lineTo(currentStroke[i].x, currentStroke[i].y)
                    }
                    drawPath(path, strokeColor, style = style)
                }
            }

            if (!hasStrokes && currentStroke.isEmpty()) {
                Text(
                    text = stringResource(R.string.sign_here),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
        }

        // Baseline rule (cosmetic)
        HorizontalDivider(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(
                onClick = {
                    completedStrokes.clear()
                    currentStroke.clear()
                    hasStrokes = false
                }
            ) {
                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.clear))
            }

            Button(
                onClick = {
                    val w = if (canvasWidthPx > 0) canvasWidthPx else 800
                    val h = if (canvasHeightPx > 0) canvasHeightPx else 200
                    val bitmap = renderSignatureToBitmap(completedStrokes, w, h, density.density)
                    onSignatureCaptured(bitmap)
                },
                enabled = hasStrokes
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.done))
            }
        }
    }
}

/**
 * Renders the captured strokes onto an [android.graphics.Bitmap] suitable for PDF overlay.
 */
private fun renderSignatureToBitmap(
    strokes: List<List<Offset>>,
    widthPx: Int,
    heightPx: Int,
    density: Float
): Bitmap {
    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        strokeWidth = 3f * density
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    for (stroke in strokes) {
        if (stroke.size < 2) continue
        val path = android.graphics.Path()
        path.moveTo(stroke[0].x, stroke[0].y)
        for (i in 1 until stroke.size) {
            path.lineTo(stroke[i].x, stroke[i].y)
        }
        canvas.drawPath(path, paint)
    }

    return bitmap
}
