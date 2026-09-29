package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.DocType
import com.whats.web.scan.webscan.pdfreaderpdffileedit.search.ContentHit
import com.whats.web.scan.webscan.pdfreaderpdffileedit.search.DocumentText
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.components.DocThumb
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.components.LocalThumbnails
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.BrandRed
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
    /** Set when picking a file for a tool: only that family is listed and content search is hidden. */
    onlyType: DocType? = null,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val results by viewModel.results.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val focus = androidx.compose.runtime.remember { FocusRequester() }
    val insideFiles by viewModel.insideFiles.collectAsStateWithLifecycle()
    val content by viewModel.content.collectAsStateWithLifecycle()
    val pdfOnly = onlyType != null
    androidx.compose.runtime.LaunchedEffect(onlyType) { viewModel.setOnlyType(onlyType) }
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
            if (!pdfOnly) {
                // Names or contents: the "search text in all documents" document-reader apps offer.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    FilterChip(
                        selected = !insideFiles,
                        onClick = { viewModel.setInsideFiles(false) },
                        label = { Text(stringResource(R.string.search_mode_names)) },
                    )
                    FilterChip(
                        selected = insideFiles,
                        onClick = { viewModel.setInsideFiles(true) },
                        label = { Text(stringResource(R.string.search_mode_content)) },
                        leadingIcon = { Icon(Icons.Filled.ManageSearch, contentDescription = null) },
                    )
                }
            }
            if (insideFiles && !pdfOnly) {
                ContentResults(
                    query = query,
                    state = content,
                    onOpen = { hit ->
                        viewModel.prepareOpen(hit)
                        onOpenFile(hit.file)
                    },
                )
                return@Column
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

@Composable
private fun ContentResults(query: String, state: SearchViewModel.ContentState, onOpen: (ContentHit) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        if (state.running) {
            LinearProgressIndicator(
                progress = { if (state.total == 0) 0f else state.done.toFloat() / state.total },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
        val message = when {
            query.trim().length < 2 -> stringResource(R.string.search_content_hint)
            state.running -> stringResource(R.string.search_content_progress, state.done, state.total)
            state.hits.isEmpty() -> stringResource(R.string.search_content_none, query)
            else -> pluralStringResource(R.plurals.search_content_found, state.hits.size, state.hits.size)
        }
        Text(
            message,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.fillMaxSize()) {
            items(state.hits, key = { it.file.key }) { hit ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem()
                        .clickable { onOpen(hit) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    DocThumb(hit.file, LocalThumbnails.current?.cached(hit.file), Modifier.size(40.dp, 52.dp))
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(
                            hit.file.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            buildString {
                                if (hit.file.type == DocType.PDF) {
                                    append(stringResource(R.string.page_number, hit.page + 1)).append(" • ")
                                }
                                append(pluralStringResource(R.plurals.match_count, hit.matches, hit.matches))
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = BrandRed,
                        )
                        hit.snippets.forEach { snippet ->
                            Text(
                                marked(snippet),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

/** Snippet text with the match (between the marker characters) in bold. */
private fun marked(snippet: String): AnnotatedString = buildAnnotatedString {
    var bold = false
    snippet.forEach { c ->
        when (c) {
            DocumentText.MARK_START -> bold = true
            DocumentText.MARK_END -> bold = false
            else -> if (bold) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.Unspecified)) { append(c) }
            } else {
                append(c)
            }
        }
    }
}
