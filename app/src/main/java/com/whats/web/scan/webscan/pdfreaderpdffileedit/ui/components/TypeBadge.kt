package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.DocType
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.TypeExcel
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.TypePdf
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.TypePpt
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.TypeText
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.TypeWord

/** FR-012: one colour per family, used by the badge, the chip tint and the file card. */
val DocType.color: Color
    get() = when (this) {
        DocType.PDF -> TypePdf
        DocType.WORD -> TypeWord
        DocType.EXCEL -> TypeExcel
        DocType.PPT -> TypePpt
        DocType.TEXT -> TypeText
    }

val DocType.badgeLabel: String
    get() = when (this) {
        DocType.PDF -> "PDF"
        DocType.WORD -> "DOC"
        DocType.EXCEL -> "XLS"
        DocType.PPT -> "PPT"
        DocType.TEXT -> "TXT"
    }

@Composable
fun TypeBadge(type: DocType, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(34.dp, 30.dp)
            .background(type.color, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = type.badgeLabel,
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/** The one-letter mark on the document icon, as file managers and Office show them. */
val DocType.iconLabel: String
    get() = when (this) {
        DocType.PDF -> "PDF"
        DocType.WORD -> "W"
        DocType.EXCEL -> "X"
        DocType.PPT -> "P"
        DocType.TEXT -> "T"
    }

/**
 * A sheet of paper with a folded corner in the family colour and its mark in white — the file icon of the
 * library rows and the Home category cards. Drawn, not a bitmap, so it is sharp at every size and needs
 * no asset per type.
 */
@Composable
fun FileTypeIcon(type: DocType, modifier: Modifier = Modifier, iconSize: Dp = 36.dp) {
    val color = type.color
    Box(modifier.size(iconSize * 0.86f, iconSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val fold = w * 0.30f
            val radius = w * 0.14f
            val body = Path().apply {
                moveTo(radius, 0f)
                lineTo(w - fold, 0f)
                lineTo(w, fold)
                lineTo(w, h - radius)
                quadraticTo(w, h, w - radius, h)
                lineTo(radius, h)
                quadraticTo(0f, h, 0f, h - radius)
                lineTo(0f, radius)
                quadraticTo(0f, 0f, radius, 0f)
                close()
            }
            drawPath(body, color)
            val corner = Path().apply {
                moveTo(w - fold, 0f)
                lineTo(w - fold, fold * 0.75f)
                quadraticTo(w - fold, fold, w - fold * 0.75f, fold)
                lineTo(w, fold)
                close()
            }
            drawPath(corner, Color.White.copy(alpha = 0.45f))
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.06f),
                topLeft = Offset(0f, h * 0.92f),
                size = Size(w, h * 0.08f),
                cornerRadius = CornerRadius(radius, radius),
            )
        }
        Text(
            text = type.iconLabel,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = if (type == DocType.PDF) (iconSize.value * 0.24f).sp else (iconSize.value * 0.40f).sp,
            modifier = Modifier.padding(top = iconSize * 0.12f),
            maxLines = 1,
        )
    }
}
