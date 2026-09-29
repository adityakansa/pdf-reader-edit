package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whats.web.scan.webscan.pdfreaderpdffileedit.R
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.mimeType
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.components.Intents
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.BrandRed
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.CrownGold

/** FR-030 … FR-032, FR-037. */
@Composable
fun PdfReaderScreen(
    key: String,
    onBack: () -> Unit,
    onAiTranslate: (String) -> Unit,
    onAiSummary: (String) -> Unit,
    onSign: (String) -> Unit,
    onPaywall: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(key) { viewModel.load(key) }
    LaunchedEffect(state.savedTo) {
        state.savedTo?.let {
            android.widget.Toast
                .makeText(context, context.getString(R.string.saved_to, it), android.widget.Toast.LENGTH_LONG)
                .show()
            viewModel.messageShown()
        }
    }

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
                IconButton(onClick = { if (state.highlightMode) viewModel.setHighlightMode(false) else onBack() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                    )
                }
                if (state.searching) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::search,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.cd_search)) },
                    )
                    Text(
                        text = if (state.matches.isEmpty()) "0" else "${state.matchIndex + 1}/${state.matches.size}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    IconButton(onClick = viewModel::previousMatch) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null)
                    }
                    IconButton(onClick = viewModel::nextMatch) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
                    }
                    IconButton(onClick = { viewModel.setSearching(false) }) {
                        Icon(Icons.Filled.Close, contentDescription = null)
                    }
                    return@Row
                }
                Text(
                    text = state.file?.name.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (state.highlightMode) {
                    IconButton(onClick = viewModel::saveHighlights) {
                        Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.action_save))
                    }
                    return@Row
                }
                IconButton(onClick = { viewModel.setSearching(true) }) {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.cd_search))
                }
                IconButton(
                    onClick = {
                        viewModel.shareableUri()?.let { uri ->
                            Intents.shareFile(context, uri, state.file?.mimeType ?: "application/pdf")
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
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cd_more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_ai_translate)) },
                            leadingIcon = { Icon(Icons.Filled.Translate, contentDescription = null) },
                            onClick = { menuOpen = false; onAiTranslate(key) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_ai_summary)) },
                            leadingIcon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
                            onClick = { menuOpen = false; onAiSummary(key) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_highlight)) },
                            onClick = { menuOpen = false; viewModel.setHighlightMode(true) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_edit_sign)) },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            trailingIcon = {
                                if (!state.isPro) {
                                    Icon(
                                        Icons.Filled.WorkspacePremium,
                                        contentDescription = null,
                                        tint = CrownGold,
                                    )
                                }
                            },
                            onClick = {
                                menuOpen = false
                                if (state.isPro) onSign(key) else onPaywall()
                            },
                        )
                    }
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

                state.failed -> Text(
                    stringResource(R.string.reader_open_failed),
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> Column(Modifier.fillMaxSize()) {
                    PdfPages(
                        pages = state.pages,
                        render = viewModel::render,
                        onPageShown = viewModel::onPageShown,
                        highlights = state.pendingHighlights,
                        highlightMode = state.highlightMode,
                        onHighlight = viewModel::highlight,
                        jumpTo = state.jumpTo,
                        onJumped = viewModel::onJumped,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (state.pages.isNotEmpty() && !state.loading) {
                Text(
                    text = stringResource(R.string.reader_page_of, state.currentPage + 1, state.pages.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            RoundedCornerShape(50),
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            if (state.noTextFound) {
                Text(
                    stringResource(R.string.reader_no_text),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(8.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    if (state.passwordRequired) {
        AlertDialog(
            onDismissRequest = onBack,
            title = { Text(stringResource(R.string.reader_password_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.reader_password_hint)) },
                    )
                    if (state.wrongPassword) {
                        Text(
                            stringResource(R.string.reader_password_wrong),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.load(key, password) }) {
                    Text(stringResource(R.string.action_ok), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = onBack) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
