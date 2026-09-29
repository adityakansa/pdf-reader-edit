package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.shell

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WorkspacePremium
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
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.whats.web.scan.webscan.pdfreaderpdffileedit.R
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.BrandRed
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.CrownGold

/** FR-004. The library top bar; in selection mode (FR-019) it turns into the count + bulk actions. */
@Composable
fun HomeTopBar(
    isPro: Boolean,
    selectionMode: Boolean,
    selectionCount: Int,
    allSelected: Boolean,
    onSelectAll: () -> Unit,
    canMerge: Boolean,
    onMergeSelected: () -> Unit,
    onSearch: () -> Unit,
    onPro: () -> Unit,
    onSort: () -> Unit,
    onToggleSelection: () -> Unit,
    onShareSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onExitSelection: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
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
                return@Row
            }

            IconButton(onClick = onSearch) {
                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.cd_search))
            }
            if (!isPro) {
                IconButton(onClick = onPro) {
                    Icon(
                        Icons.Filled.WorkspacePremium,
                        contentDescription = stringResource(R.string.cd_go_pro),
                        tint = CrownGold,
                    )
                }
            }
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                        append(stringResource(R.string.title_pdf))
                    }
                    append(" ")
                    withStyle(SpanStyle(color = BrandRed)) { append(stringResource(R.string.title_reader)) }
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
            IconButton(onClick = onSort) {
                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.cd_sort))
            }
            IconButton(onClick = onToggleSelection) {
                Icon(Icons.Filled.EditNote, contentDescription = stringResource(R.string.cd_select))
            }
        }
    }
}
