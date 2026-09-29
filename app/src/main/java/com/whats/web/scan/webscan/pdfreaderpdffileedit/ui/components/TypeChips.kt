package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.whats.web.scan.webscan.pdfreaderpdffileedit.R
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.DocType

/** `null` is the "All" chip. */
@Composable
fun TypeChips(
    selected: DocType?,
    onSelect: (DocType?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries: List<Pair<DocType?, Int>> = listOf(
        null to R.string.chip_all,
        DocType.PDF to R.string.chip_pdf,
        DocType.WORD to R.string.chip_word,
        DocType.EXCEL to R.string.chip_excel,
        DocType.PPT to R.string.chip_ppt,
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        entries.forEach { (type, label) ->
            val isSelected = type == selected
            val tint = type?.color ?: MaterialTheme.colorScheme.primary
            Box(
                modifier = Modifier
                    .background(
                        color = if (isSelected) tint.copy(alpha = 0.12f) else Color.Transparent,
                        shape = RoundedCornerShape(50),
                    )
                    .clickable { onSelect(type) }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) tint else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
