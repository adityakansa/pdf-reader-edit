package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.reader

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.whats.web.scan.webscan.pdfreaderpdffileedit.R
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.MarkupKind
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.BrandRed

/** The bookmarked pages of this PDF; tap one to jump there. */
@Composable
fun BookmarksDialog(bookmarks: List<Int>, onGo: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_bookmarks)) },
        text = {
            if (bookmarks.isEmpty()) {
                Text(stringResource(R.string.bookmarks_empty))
            } else {
                LazyColumn(Modifier.height(320.dp)) {
                    items(bookmarks) { page ->
                        Text(
                            stringResource(R.string.page_number, page + 1),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onGo(page) }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

/** Every page as a thumbnail, the current one outlined, bookmarked ones marked; tap to jump. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageGridSheet(
    pageCount: Int,
    current: Int,
    bookmarks: Set<Int>,
    render: suspend (Int, Int) -> Bitmap?,
    onGo: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val grid = rememberLazyGridState(initialFirstVisibleItemIndex = current.coerceIn(0, (pageCount - 1).coerceAtLeast(0)))
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text(
            stringResource(R.string.reader_pages),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(96.dp),
            state = grid,
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items((0 until pageCount).toList(), key = { it }) { index ->
                val bitmap by produceState<Bitmap?>(null, index) { value = render(index, THUMB_WIDTH) }
                val isCurrent = index == current
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.72f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White)
                            .border(
                                if (isCurrent) 3.dp else 1.dp,
                                if (isCurrent) BrandRed else MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(6.dp),
                            )
                            .clickable { onGo(index) },
                    ) {
                        bitmap?.let {
                            Image(it.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                        }
                        if (index in bookmarks) {
                            Icon(
                                Icons.Filled.Bookmark,
                                contentDescription = null,
                                tint = BrandRed,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(2.dp)
                                    .size(18.dp),
                            )
                        }
                    }
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/** Annotate mode's tool bar: Highlight, Underline, Strike, Pen (with colours), Undo. */
@Composable
fun AnnotateBar(
    tool: MarkupKind,
    penColor: Int,
    canUndo: Boolean,
    onTool: (MarkupKind) -> Unit,
    onPenColor: (Int) -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            if (tool == MarkupKind.INK) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                ) {
                    ReaderViewModel.PEN_COLORS.forEach { argb ->
                        val selected = argb == penColor
                        Box(
                            Modifier
                                .padding(horizontal = 8.dp)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .border(if (selected) 3.dp else 1.dp, if (selected) BrandRed else Color.LightGray, CircleShape)
                                .clickable(role = Role.RadioButton) { onPenColor(argb) },
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                AnnotateTool(Icons.Filled.BorderColor, stringResource(R.string.action_highlight), tool == MarkupKind.HIGHLIGHT) {
                    onTool(MarkupKind.HIGHLIGHT)
                }
                AnnotateTool(Icons.Filled.FormatUnderlined, stringResource(R.string.annotate_underline), tool == MarkupKind.UNDERLINE) {
                    onTool(MarkupKind.UNDERLINE)
                }
                AnnotateTool(Icons.Filled.FormatStrikethrough, stringResource(R.string.annotate_strike), tool == MarkupKind.STRIKEOUT) {
                    onTool(MarkupKind.STRIKEOUT)
                }
                AnnotateTool(Icons.Filled.Gesture, stringResource(R.string.annotate_pen), tool == MarkupKind.INK) {
                    onTool(MarkupKind.INK)
                }
                AnnotateTool(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.action_undo), false, enabled = canUndo, onClick = onUndo)
            }
        }
    }
}

@Composable
private fun AnnotateTool(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        selected -> BrandRed
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) BrandRed.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(enabled = enabled, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

private const val THUMB_WIDTH = 200
