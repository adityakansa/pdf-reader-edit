package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.components

import android.text.format.Formatter
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.whats.web.scan.webscan.pdfreaderpdffileedit.R
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.DocFile
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.LibraryFile
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.Thumbnails
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.BrandRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** "09/29/2026" in the US, "29/09/2026" in India and the UK — the numeric date the phone's locale uses. */
private val rowDate = SimpleDateFormat(
    android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMddyyyy"),
    Locale.getDefault(),
)

fun formatModified(millis: Long): String = rowDate.format(Date(millis))

/** "1 minute ago", "Yesterday", … for the Recent tab. */
fun formatRelative(millis: Long): String =
    android.text.format.DateUtils.getRelativeTimeSpanString(
        millis,
        System.currentTimeMillis(),
        android.text.format.DateUtils.MINUTE_IN_MILLIS,
    ).toString()

/** Provided once by `MainActivity`; null in previews, where rows fall back to the type tile. */
val LocalThumbnails = staticCompositionLocalOf<Thumbnails?> { null }

/** The page preview for [file], loaded off the main thread and remembered across recompositions. */
@Composable
private fun rememberPreview(file: DocFile): Thumbnails.Preview? {
    val thumbnails = LocalThumbnails.current
    val preview by produceState(thumbnails?.cached(file), file.key, file.size, file.modified) {
        if (value == null && thumbnails != null) value = thumbnails.preview(file)
    }
    return preview
}

/**
 * The document's first page in a small paper frame, as Adobe Scan lists scans. Until the preview is
 * ready (or when there is none: most spreadsheets, old Office formats) the coloured type tile stands in,
 * so every row has the same shape and the type is always readable.
 */
@Composable
fun DocThumb(file: DocFile, preview: Thumbnails.Preview?, modifier: Modifier = Modifier) {
    val bitmap = preview?.bitmap
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (image != null) Color.White else file.type.color.copy(alpha = 0.10f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = image,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "thumbnail",
        ) { shown ->
            if (shown != null) {
                Image(
                    bitmap = shown,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    TypeBadge(file.type)
                }
            }
        }
        if (image != null) {
            TypeBadge(
                file.type,
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(3.dp)
                    .size(24.dp, 16.dp),
            )
        }
        if (preview?.locked == true) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = stringResource(R.string.cd_password_protected),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(14.dp),
            )
        }
    }
}

/** "09/29/2026   113.8 KB", or "2 minutes ago   55.2 KB" on Recent, as One Read lists files. */
@Composable
private fun metaLine(file: DocFile, time: String?): String {
    val context = LocalContext.current
    return "${time ?: formatModified(file.modified)}   ${Formatter.formatShortFileSize(context, file.size)}"
}

/** FR-013 in Adobe Scan's list style. Also the selection-mode row (FR-019): a check replaces the star. */
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
    showMenu: Boolean = true,
    /** Replaces the date, e.g. "2 minutes ago" on Recent. */
    time: String? = null,
    modifier: Modifier = Modifier,
) {
    val preview = rememberPreview(item.file)
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (selected) BrandRed.copy(alpha = 0.06f) else Color.Transparent)
                .combinedClickable(onLongClick = onLongPress, onClick = onOpen)
                .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                FileTypeIcon(item.file.type, iconSize = 38.dp)
                if (preview?.locked == true) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = stringResource(R.string.cd_password_protected),
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
                            .padding(2.dp)
                            .size(12.dp),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
            ) {
                Text(
                    text = item.file.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = metaLine(item.file, time),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            RowActions(item, selectionMode, selected, showMenu, onToggleFavourite, onMenu)
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 1.dp,
            modifier = Modifier.padding(start = 68.dp, end = 16.dp),
        )
    }
}

/** The grid card: a large page preview with the name under it, as in Adobe Scan's grid view. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileCard(
    item: LibraryFile,
    onOpen: () -> Unit,
    onToggleFavourite: () -> Unit,
    onMenu: () -> Unit,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongPress: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val preview = rememberPreview(item.file)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) BrandRed.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) BrandRed else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = modifier,
    ) {
        Column(Modifier.combinedClickable(onLongClick = onLongPress, onClick = onOpen)) {
            Box {
                DocThumb(
                    item.file,
                    preview,
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.78f)
                        .padding(8.dp),
                )
                if (selectionMode) {
                    Icon(
                        imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (selected) BrandRed else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                            .background(MaterialTheme.colorScheme.surface, CircleShape),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 10.dp, bottom = 8.dp),
                ) {
                    Text(
                        item.file.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        formatModified(item.file.modified),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!selectionMode) {
                    IconButton(onClick = onMenu) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cd_more))
                    }
                }
            }
        }
    }
}

@Composable
private fun RowActions(
    item: LibraryFile,
    selectionMode: Boolean,
    selected: Boolean,
    showMenu: Boolean,
    onToggleFavourite: () -> Unit,
    onMenu: () -> Unit,
) {
    if (selectionMode) {
        Icon(
            imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) BrandRed else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(end = 16.dp)
                .size(24.dp),
        )
        return
    }
    IconButton(onClick = onToggleFavourite) {
        Icon(
            imageVector = if (item.favourite) Icons.Filled.Star else Icons.Outlined.StarBorder,
            contentDescription = stringResource(
                if (item.favourite) R.string.cd_unfavourite else R.string.cd_favourite,
            ),
            tint = if (item.favourite) BrandRed else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (showMenu) {
        IconButton(onClick = onMenu) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.cd_more),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
