package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.shell

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Search
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
    selectionCount: Int,
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
            if (selectionCount > 0) {
                IconButton(onClick = onExitSelection) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_back))
                }
                Text(
                    text = stringResource(R.string.selected_count, selectionCount),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onShareSelected) {
                    Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share))
                }
                IconButton(onClick = onDeleteSelected) {
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
