package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.reader

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whats.web.scan.webscan.pdfreaderpdffileedit.R
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.mimeType
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.components.Intents
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.BrandRed

/** FR-033 … FR-037. The converted document in a WebView with JavaScript off. */
@Composable
fun OfficeReaderScreen(
    key: String,
    onBack: () -> Unit,
    viewModel: OfficeReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val rowCapNotice = stringResource(R.string.xlsx_row_cap)
    var webView by remember { mutableStateOf<WebView?>(null) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var matchActive by remember { mutableIntStateOf(0) }
    var matchTotal by remember { mutableIntStateOf(0) }

    LaunchedEffect(key) { viewModel.load(key, rowCapNotice) }
    LaunchedEffect(webView) {
        webView?.setFindListener { active, total, done ->
            if (done) {
                matchActive = active
                matchTotal = total
            }
        }
    }

    fun closeSearch() {
        searching = false
        query = ""
        matchTotal = 0
        webView?.clearMatches()
    }
    BackHandler(enabled = searching) { closeSearch() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                    )
                }
                if (searching) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            webView?.findAllAsync(it)
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.reader_search_hint)) },
                    )
                    if (query.isNotBlank()) {
                        Text(
                            if (matchTotal == 0) {
                                stringResource(R.string.reader_no_matches)
                            } else {
                                "${matchActive + 1}/$matchTotal"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    IconButton(onClick = { webView?.findNext(false) }, enabled = matchTotal > 0) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.cd_previous_match))
                    }
                    IconButton(onClick = { webView?.findNext(true) }, enabled = matchTotal > 0) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.cd_next_match))
                    }
                    IconButton(onClick = { closeSearch() }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_close_search))
                    }
                    return@Row
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = state.file?.name.orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (state.html != null) {
                        Text(
                            stringResource(R.string.reader_simplified),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = { searching = true }) {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.cd_search))
                }
                IconButton(
                    onClick = {
                        viewModel.shareableUri()?.let { uri ->
                            Intents.shareFile(context, uri, state.file?.mimeType ?: "*/*")
                        }
                    },
                ) {
                    Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share))
                }
                IconButton(onClick = viewModel::toggleFavourite) {
                    Icon(
                        if (state.favourite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = stringResource(R.string.cd_favourite),
                        tint = if (state.favourite) BrandRed else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                state.legacy -> Column(
                    Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(R.string.reader_legacy_format),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Button(
                        onClick = {
                            val file = state.file ?: return@Button
                            Intents.openWith(context, viewModel.shareableUri() ?: file.uri, file.mimeType)
                        },
                        modifier = Modifier.padding(top = 16.dp),
                    ) { Text(stringResource(R.string.reader_open_with)) }
                }

                state.failed -> Text(
                    stringResource(R.string.reader_open_failed),
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            // FR-033: no script runs in a document we converted ourselves.
                            settings.javaScriptEnabled = false
                            settings.allowFileAccess = true
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            settings.setSupportZoom(true)
                            webView = this
                        }
                    },
                )
            }
            // Loading here rather than in `update` so a recomposition does not reload the page.
            LaunchedEffect(webView, state.html) {
                val view = webView ?: return@LaunchedEffect
                val html = state.html ?: return@LaunchedEffect
                view.loadDataWithBaseURL(state.baseUrl, html, "text/html", "UTF-8", null)
            }
        }
    }
}
