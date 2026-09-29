package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Icons Material does not have, drawn on the same 24 × 24 grid so they sit with the rest. */
object AppIcons {

    /** A tilted eraser: solid rubber above, outlined tip below, and the line it rubs along. */
    val Eraser: ImageVector by lazy {
        ImageVector.Builder(name = "Eraser", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .path(fill = SolidColor(Color.Black)) {
                moveTo(14.2f, 3.2f)
                lineTo(20.8f, 9.8f)
                lineTo(15.2f, 15.4f)
                lineTo(8.6f, 8.8f)
                close()
            }
            .path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(8.6f, 8.8f)
                lineTo(15.2f, 15.4f)
                lineTo(11.2f, 19.4f)
                lineTo(7.4f, 19.4f)
                lineTo(4.0f, 16.0f)
                close()
            }
            .path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(13.5f, 20.2f)
                lineTo(20.5f, 20.2f)
            }
            .build()
    }
}
