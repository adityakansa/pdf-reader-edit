package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.DocType
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.TypeExcel
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.TypePdf
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.TypePpt
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.TypeWord

/** FR-012: one colour per family, used by the badge, the chip tint and the file card. */
val DocType.color: Color
    get() = when (this) {
        DocType.PDF -> TypePdf
        DocType.WORD -> TypeWord
        DocType.EXCEL -> TypeExcel
        DocType.PPT -> TypePpt
    }

val DocType.badgeLabel: String
    get() = when (this) {
        DocType.PDF -> "PDF"
        DocType.WORD -> "DOC"
        DocType.EXCEL -> "XLS"
        DocType.PPT -> "PPT"
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
