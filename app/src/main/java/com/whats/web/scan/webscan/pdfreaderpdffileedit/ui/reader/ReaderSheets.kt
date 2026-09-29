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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.whats.web.scan.webscan.pdfreaderpdffileedit.R
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

private const val THUMB_WIDTH = 200
