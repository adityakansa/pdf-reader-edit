package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.home

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.FormatShapes
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whats.web.scan.webscan.pdfreaderpdffileedit.R
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.components.FileTypeIcon
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.BrandRed
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.CtaOrange
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.CtaRed

val Tool.icon: ImageVector
    get() = when (this) {
        Tool.IMAGE_TO_PDF -> Icons.Filled.Image
        Tool.SCAN_TO_PDF -> Icons.Filled.DocumentScanner
        Tool.CREATE_PDF -> Icons.AutoMirrored.Filled.NoteAdd
        Tool.PDF_TO_WORD -> Icons.Filled.PictureAsPdf
        Tool.WORD_TO_PDF -> Icons.Filled.Description
        Tool.PDF_TO_IMAGE -> Icons.Filled.PictureAsPdf
        Tool.PPT_TO_PDF -> Icons.Filled.Slideshow
        Tool.EXCEL_TO_PDF -> Icons.Filled.TableChart
        Tool.EDIT_TEXT -> Icons.Filled.FormatShapes
        Tool.ANNOTATE -> Icons.Filled.BorderColor
        Tool.ADD_TEXT -> Icons.Filled.TextFields
        Tool.FILL_SIGN -> Icons.Filled.Draw
        Tool.MERGE_PDF -> Icons.Filled.MergeType
        Tool.SPLIT_PDF -> Icons.AutoMirrored.Filled.CallSplit
        Tool.MANAGE_PAGES -> Icons.Filled.Dashboard
        Tool.COMPRESS_PDF -> Icons.Filled.Compress
        Tool.PROTECT_PDF -> Icons.Filled.Lock
        Tool.RECYCLE_BIN -> Icons.Filled.Delete
        Tool.AI_TRANSLATE -> Icons.Filled.Translate
        Tool.AI_SUMMARY -> Icons.Filled.AutoAwesome
        Tool.AI_EXTRACT -> Icons.Filled.FindInPage
    }

/**
 * Home, as One Read and Document Reader lay it out: the default-reader banner, the "All Files" cards
 * with live counts, then every tool as a tile grid under "Create & Convert", "Edit & Manage" and "AI tools".
 * One lazy grid, so the whole page scrolls as one and only the visible rows are composed.
 */
@Composable
fun Dashboard(
    state: HomeUiState,
    onSetDefault: () -> Unit,
    onDismissBanner: () -> Unit,
    onStorageAccess: () -> Unit,
    onCategory: (LibraryCategory) -> Unit,
    onTool: (Tool) -> Unit,
) {
    val context = LocalContext.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(TOOL_COLUMNS),
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.showDefaultBanner) {
            fullWidth("banner") { DefaultReaderBanner(onSetDefault, onDismissBanner) }
        }
        if (!state.hasStorageAccess) {
            fullWidth("access") { StorageAccessCard(onStorageAccess) }
        }
        fullWidth("files") { SectionTitle(stringResource(R.string.section_all_files)) }
        items(
            LibraryCategory.entries,
            key = { "card-${it.name}" },
            span = { GridItemSpan(TOOL_COLUMNS / 2) },
        ) { category ->
            val count = state.counts[category] ?: 0
            CategoryCard(
                category = category,
                subtitle = if (category == LibraryCategory.FOLDERS) {
                    Formatter.formatShortFileSize(context, state.totalSize)
                } else {
                    count.toString()
                },
                onClick = { onCategory(category) },
            )
        }
        ToolSection.entries.forEach { section ->
            val tools = Tool.entries.filter { it.section == section && it in readyTools }
            if (tools.isEmpty()) return@forEach
            fullWidth("section-${section.name}") { SectionTitle(stringResource(section.title)) }
            items(tools, key = { "tool-${it.name}" }) { tool -> ToolTile(tool, onClick = { onTool(tool) }) }
        }
    }
}

private fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 20.dp, bottom = 10.dp),
    )
}

/** "Set as default reader for instant reading. [Set] ✕" — gone for good once closed or once we are the default. */
@Composable
private fun DefaultReaderBanner(onSet: () -> Unit, onDismiss: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Icon(
            Icons.Filled.AutoStories,
            contentDescription = null,
            tint = BrandRed,
            modifier = Modifier.size(24.dp),
        )
        Text(
            stringResource(R.string.default_banner),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
        )
        Button(
            onClick = onSet,
            colors = ButtonDefaults.buttonColors(containerColor = CtaRed),
            contentPadding = PaddingValues(horizontal = 14.dp),
            modifier = Modifier.height(34.dp),
        ) { Text(stringResource(R.string.action_set), fontWeight = FontWeight.Bold) }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_close_banner))
        }
    }
}

@Composable
private fun StorageAccessCard(onAllow: () -> Unit) {
    Column(
        Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BrandRed.copy(alpha = 0.07f))
            .padding(14.dp),
    ) {
        Text(
            stringResource(R.string.access_card_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(R.string.access_card_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )
        Button(onClick = onAllow, colors = ButtonDefaults.buttonColors(containerColor = BrandRed)) {
            Text(stringResource(R.string.storage_grant))
        }
    }
}

/** One "All Files" card: the family icon, its name and how many there are, on a tint of its colour. */
@Composable
private fun CategoryCard(category: LibraryCategory, subtitle: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(bottom = 10.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(category.color.copy(alpha = 0.10f))
            .border(1.dp, category.color.copy(alpha = 0.16f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        CategoryIcon(category)
        Column(Modifier.padding(start = 12.dp)) {
            Text(
                stringResource(category.label),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CategoryIcon(category: LibraryCategory) {
    val type = category.type
    if (type != null) {
        FileTypeIcon(type, iconSize = 32.dp)
        return
    }
    val icon = when (category) {
        LibraryCategory.FOLDERS -> Icons.Filled.FolderCopy
        LibraryCategory.FAVOURITES -> Icons.Filled.Star
        else -> Icons.Filled.Folder
    }
    Box(
        Modifier
            .size(32.dp)
            .background(category.color, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

/**
 * A tool: a soft tile in the tool's colour holding its glyph, with the output format as a small corner
 * badge ("W", "PDF", "JPG") the way converter apps mark them, and the name under it.
 */
@Composable
private fun ToolTile(tool: Tool, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Box(Modifier.size(56.dp)) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(52.dp)
                    .background(tool.color.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(tool.icon, contentDescription = null, tint = tool.color, modifier = Modifier.size(28.dp))
            }
            tool.badge?.let { badge ->
                Text(
                    badge,
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 2.dp, y = 2.dp)
                        .background(badgeColor(badge), RoundedCornerShape(4.dp))
                        .padding(horizontal = 3.dp, vertical = 1.dp),
                )
            }
        }
        Text(
            stringResource(tool.label),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            minLines = 2,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

private fun badgeColor(badge: String): Color = when (badge) {
    "W" -> com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.TypeWord
    "JPG" -> com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.ToolBlue
    else -> CtaOrange
}

private const val TOOL_COLUMNS = 4

/** The rounded dot used by the empty recycle bin and elsewhere. */
@Composable
fun SoftCircleIcon(icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Icon(
        icon,
        contentDescription = null,
        tint = tint,
        modifier = modifier
            .size(96.dp)
            .background(tint.copy(alpha = 0.10f), CircleShape)
            .padding(24.dp),
    )
}
