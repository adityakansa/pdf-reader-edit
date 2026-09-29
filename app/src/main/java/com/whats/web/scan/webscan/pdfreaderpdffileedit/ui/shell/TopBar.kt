package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.shell

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.whats.web.scan.webscan.pdfreaderpdffileedit.R
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.BrandRed
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.CrownGold

/**
 * The plain top bar every library screen shares: an optional back arrow, a title, and icon actions on the
 * right, as in One Read ("PDF files ☐ ⇅ 🔍").
 */
@Composable
fun TitleBar(
    title: @Composable () -> Unit,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(start = if (onBack == null) 16.dp else 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                }
            }
            Row(
                Modifier
                    .weight(1f)
                    .padding(start = if (onBack == null) 0.dp else 4.dp),
            ) { title() }
            actions()
        }
    }
}

/** A plain bold screen title. */
@Composable
fun ScreenTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** "PDF Reader" with the second word in the brand red, as Home shows the app name. */
@Composable
fun BrandTitle() {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                append(stringResource(R.string.title_pdf))
            }
            append(" ")
            withStyle(SpanStyle(color = BrandRed)) { append(stringResource(R.string.title_reader)) }
        },
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.ExtraBold,
    )
}

/** The premium gem; shown to free users only. */
@Composable
fun ProAction(isPro: Boolean, onPro: () -> Unit) {
    if (isPro) return
    IconButton(onClick = onPro) {
        Icon(Icons.Filled.Diamond, contentDescription = stringResource(R.string.cd_go_pro), tint = CrownGold)
    }
}

/** FR-019. The count and bulk actions that replace the title bar while files are being picked. */
@Composable
fun SelectionBar(
    selectionCount: Int,
    allSelected: Boolean,
    onSelectAll: () -> Unit,
    canMerge: Boolean,
    onMergeSelected: () -> Unit,
    onShareSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onExitSelection: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onExitSelection) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_exit_selection))
            }
            Text(
                text = if (selectionCount == 0) {
                    stringResource(R.string.select_files)
                } else {
                    stringResource(R.string.selected_count, selectionCount)
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onSelectAll) {
                Icon(
                    if (allSelected) Icons.Filled.Deselect else Icons.Filled.SelectAll,
                    contentDescription = stringResource(
                        if (allSelected) R.string.cd_deselect_all else R.string.cd_select_all,
                    ),
                )
            }
            if (canMerge) {
                IconButton(onClick = onMergeSelected) {
                    Icon(Icons.Filled.MergeType, contentDescription = stringResource(R.string.action_merge))
                }
            }
            IconButton(onClick = onShareSelected, enabled = selectionCount > 0) {
                Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share))
            }
            IconButton(onClick = onDeleteSelected, enabled = selectionCount > 0) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
            }
        }
    }
}
