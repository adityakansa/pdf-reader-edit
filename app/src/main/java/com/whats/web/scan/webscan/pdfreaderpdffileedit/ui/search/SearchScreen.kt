package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whats.web.scan.webscan.pdfreaderpdffileedit.R
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ads.BannerAd
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.DocFile
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.components.FileRow

/** FR-015 (S02). Name search over the index that is already in memory — no second scan. */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenFile: (DocFile) -> Unit,
    canShowAds: Boolean,
    pdfOnly: Boolean = false,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val results by viewModel.results.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val focus = androidx.compose.runtime.remember { FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(pdfOnly) { viewModel.setPdfOnly(pdfOnly) }
    // The whole point of this screen is typing, so the keyboard opens with it.
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = { BannerAd(visible = canShowAds && !isPro) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                    )
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focus),
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setQuery("") }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.clear),
                                )
                            }
                        }
                    },
                )
            }
            if (results.isEmpty() && query.isNotBlank()) {
                Text(
                    stringResource(R.string.search_no_results, query),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    textAlign = TextAlign.Center,
                )
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(results, key = { it.file.key }) { item ->
                    FileRow(
                        item = item,
                        onOpen = { onOpenFile(item.file) },
                        onToggleFavourite = { viewModel.toggleFavourite(item.file.key) },
                        onMenu = {},
                        showMenu = false,
                    )
                }
            }
        }
    }
}
