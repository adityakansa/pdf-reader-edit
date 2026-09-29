package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.sign

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.whats.web.scan.webscan.pdfreaderpdffileedit.R
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.SignatureInkColor
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.SignaturePaper

/**
 * FR-050. One pad, not pdfscanner's two. The ink is fixed dark on a fixed paper colour in both themes —
 * a signature that inverted with the system theme would be written into other people's documents as
 * white-on-white.
 */
@Composable
fun SignaturePadDialog(onSave: (Bitmap) -> Unit, onDismiss: () -> Unit) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.signature_new), style = MaterialTheme.typography.titleSmall)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f)
                        .padding(vertical = 12.dp)
                        .background(SignaturePaper)
                        .onSizeChanged { size = it }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset -> current = listOf(offset) },
                                onDragEnd = {
                                    if (current.size > 1) strokes.add(current)
                                    current = emptyList()
                                },
                                onDragCancel = { current = emptyList() },
                            ) { change, _ -> current = current + change.position }
                        }
                        .drawWithStrokes(strokes, current),
                )
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) }) {
                        Text(stringResource(R.string.action_undo))
                    }
                    TextButton(onClick = { strokes.clear() }) { Text(stringResource(R.string.action_clear)) }
                    Button(
                        onClick = {
                            val bitmap = renderSignature(strokes, size.width, size.height)
                            if (bitmap != null) onSave(bitmap) else onDismiss()
                        },
                        enabled = strokes.isNotEmpty(),
                    ) { Text(stringResource(R.string.action_save)) }
                }
            }
        }
    }
}

private fun Modifier.drawWithStrokes(strokes: List<List<Offset>>, current: List<Offset>) =
    drawBehind {
        (strokes + listOf(current)).forEach { points ->
            if (points.size < 2) return@forEach
            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(path, SignatureInkColor, style = Stroke(width = STROKE_WIDTH))
        }
    }

/**
 * The strokes as a transparent PNG cropped to the ink, so the signature carries no paper with it when
 * it is dropped on someone's contract.
 */
private fun renderSignature(strokes: List<List<Offset>>, width: Int, height: Int): Bitmap? {
    val points = strokes.flatten()
    if (points.isEmpty() || width <= 0 || height <= 0) return null
    val pad = STROKE_WIDTH
    val left = (points.minOf { it.x } - pad).coerceAtLeast(0f)
    val top = (points.minOf { it.y } - pad).coerceAtLeast(0f)
    val right = (points.maxOf { it.x } + pad).coerceAtMost(width.toFloat())
    val bottom = (points.maxOf { it.y } + pad).coerceAtMost(height.toFloat())
    val cropWidth = (right - left).toInt().coerceAtLeast(1)
    val cropHeight = (bottom - top).toInt().coerceAtLeast(1)

    val bitmap = Bitmap.createBitmap(cropWidth, cropHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = SignatureInkColor.toArgb()
        style = Paint.Style.STROKE
        strokeWidth = STROKE_WIDTH
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    strokes.forEach { stroke ->
        if (stroke.size < 2) return@forEach
        val path = AndroidPath().apply {
            moveTo(stroke.first().x - left, stroke.first().y - top)
            stroke.drop(1).forEach { lineTo(it.x - left, it.y - top) }
        }
        canvas.drawPath(path, paint)
    }
    return bitmap
}

private const val STROKE_WIDTH = 8f
