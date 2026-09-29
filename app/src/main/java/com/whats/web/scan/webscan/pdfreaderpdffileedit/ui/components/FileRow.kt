package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.components

import android.text.format.Formatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.whats.web.scan.webscan.pdfreaderpdffileedit.R
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.LibraryFile
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.BrandRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val rowDate = SimpleDateFormat("yy/MM/dd", Locale.getDefault())

fun formatModified(millis: Long): String = rowDate.format(Date(millis))

/** FR-013. Also the selection-mode row (FR-019) — same layout, check instead of the star. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRow(
    item: LibraryFile,
    onOpen: () -> Unit,
    onToggleFavourite: () -> Unit,
    onMenu: () -> Unit,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongPress: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onLongClick = onLongPress, onClick = onOpen)
                .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TypeBadge(item.file.type)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = item.file.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${formatModified(item.file.modified)} • " +
                        Formatter.formatShortFileSize(context, item.file.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selectionMode) {
                Icon(
                    imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (selected) BrandRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(24.dp),
                )
            } else {
                IconButton(onClick = onToggleFavourite) {
                    Icon(
                        imageVector = if (item.favourite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = stringResource(R.string.cd_favourite),
                        tint = if (item.favourite) BrandRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onMenu) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.cd_more),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
    }
}
