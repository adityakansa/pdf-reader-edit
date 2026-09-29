package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.reader

import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Pageview
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.HorizontalDivider
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.components.Printing
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.MarkupKind
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.HighlightYellow
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
import androidx.compose.material.icons.filled.TextSnippet
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
    onExtractText: (String) -> Unit,
    onSign: (String) -> Unit,
    onPaywall: () -> Unit,
    /** Home → Annotate: open with the Annotate tools already showing. */
    startAnnotating: Boolean = false,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var goToOpen by remember { mutableStateOf(false) }
    var bookmarksOpen by remember { mutableStateOf(false) }
    var pagesOpen by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }

    // Back closes whatever mode is open before it leaves the document, as Acrobat does.
    BackHandler(enabled = state.searching || state.highlightMode) {
        if (state.searching) viewModel.setSearching(false) else viewModel.setHighlightMode(false)
    }
    LaunchedEffect(state.searching) { if (state.searching) runCatching { searchFocus.requestFocus() } }

    var annotateStarted by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(key) {
        viewModel.load(key)
        // Once only, so turning the phone does not reopen tools the user already closed.
        if (startAnnotating && !annotateStarted) {
            annotateStarted = true
            viewModel.setHighlightMode(true)
        }
    }
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
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(searchFocus),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.reader_search_hint)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { viewModel.nextMatch() }),
                    )
                    if (state.query.isNotBlank()) {
                        Text(
                            text = if (state.matches.isEmpty()) {
                                stringResource(R.string.reader_no_matches)
                            } else {
                                "${state.matchIndex + 1}/${state.matches.size}"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    IconButton(onClick = viewModel::previousMatch, enabled = state.matches.isNotEmpty()) {
                        Icon(
                            Icons.Filled.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.cd_previous_match),
                        )
                    }
                    IconButton(onClick = viewModel::nextMatch, enabled = state.matches.isNotEmpty()) {
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.cd_next_match),
                        )
                    }
                    IconButton(onClick = { viewModel.setSearching(false) }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_close_search))
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
                if (state.pages.isNotEmpty()) {
                    val marked = state.currentPage in state.bookmarks
                    IconButton(onClick = viewModel::toggleBookmark) {
                        Icon(
                            if (marked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = stringResource(if (marked) R.string.cd_remove_bookmark else R.string.cd_add_bookmark),
                            tint = if (marked) BrandRed else MaterialTheme.colorScheme.onSurface,
                        )
                    }
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
                            text = { Text(stringResource(R.string.reader_pages)) },
                            leadingIcon = { Icon(Icons.Filled.GridView, contentDescription = null) },
                            onClick = { menuOpen = false; pagesOpen = true },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.reader_bookmarks)) },
                            leadingIcon = { Icon(Icons.Filled.Bookmarks, contentDescription = null) },
                            onClick = { menuOpen = false; bookmarksOpen = true },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.reader_go_to_page)) },
                            leadingIcon = { Icon(Icons.Filled.Pageview, contentDescription = null) },
                            onClick = { menuOpen = false; goToOpen = true },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_print)) },
                            leadingIcon = { Icon(Icons.Filled.Print, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                viewModel.printableUri()?.let { uri ->
                                    Printing.printPdf(context, uri, state.file?.name ?: "Document")
                                }
                            },
                        )
                        HorizontalDivider()
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
                            text = { Text(stringResource(R.string.action_extract_text_long)) },
                            leadingIcon = { Icon(Icons.Filled.TextSnippet, contentDescription = null) },
                            onClick = { menuOpen = false; onExtractText(key) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_annotate)) },
                            leadingIcon = { Icon(Icons.Filled.BorderColor, contentDescription = null) },
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

                state.failed -> Column(
                    Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp),
                    )
                    Text(
                        stringResource(R.string.reader_open_failed),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        stringResource(R.string.reader_open_failed_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    OutlinedButton(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
                        Text(stringResource(R.string.action_go_back))
                    }
                }

                else -> Column(Modifier.fillMaxSize()) {
                    PdfPages(
                        pages = state.pages,
                        render = viewModel::render,
                        onPageShown = viewModel::onPageShown,
                        highlights = state.pendingHighlights,
                        highlightMode = state.highlightMode,
                        tool = state.markupTool,
                        penColor = state.penColor,
                        onInk = viewModel::addInk,
                        onHighlight = viewModel::highlight,
                        jumpTo = state.jumpTo,
                        onJumped = viewModel::onJumped,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (state.pages.isNotEmpty() && !state.loading && !state.highlightMode) {
                Text(
                    text = stringResource(R.string.reader_page_of, state.currentPage + 1, state.pages.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        .clickable(onClickLabel = stringResource(R.string.reader_go_to_page)) { goToOpen = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            if (state.highlightMode) {
                // Without a hint and a tool bar the mode looks like the normal reader and people do not know what to do.
                Text(
                    stringResource(
                        if (state.markupTool == MarkupKind.INK) R.string.annotate_hint_pen else R.string.reader_highlight_hint,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Black,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(8.dp)
                        .background(HighlightYellow, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
                AnnotateBar(
                    tool = state.markupTool,
                    penColor = state.penColor,
                    canUndo = state.pendingHighlights.isNotEmpty(),
                    onTool = viewModel::setMarkupTool,
                    onPenColor = viewModel::setPenColor,
                    onUndo = viewModel::undoMarkup,
                    modifier = Modifier.align(Alignment.BottomCenter),
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
                        // A document password is a secret: masked by default, like every other password field.
                        visualTransformation = if (showPassword) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { viewModel.load(key, password) }),
                        isError = state.wrongPassword,
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = stringResource(
                                        if (showPassword) R.string.cd_hide_password else R.string.cd_show_password,
                                    ),
                                )
                            }
                        },
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
                TextButton(onClick = { viewModel.load(key, password) }, enabled = password.isNotEmpty()) {
                    Text(stringResource(R.string.action_open), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = onBack) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (bookmarksOpen) {
        BookmarksDialog(
            bookmarks = state.bookmarks.sorted(),
            onGo = { page -> bookmarksOpen = false; viewModel.goToPage(page) },
            onDismiss = { bookmarksOpen = false },
        )
    }
    if (pagesOpen) {
        PageGridSheet(
            pageCount = state.pages.size,
            current = state.currentPage,
            bookmarks = state.bookmarks,
            render = viewModel::render,
            onGo = { page -> pagesOpen = false; viewModel.goToPage(page) },
            onDismiss = { pagesOpen = false },
        )
    }
    if (goToOpen) {
        GoToPageDialog(
            pageCount = state.pages.size,
            onGo = { page -> goToOpen = false; viewModel.goToPage(page - 1) },
            onDismiss = { goToOpen = false },
        )
    }
}

/** Acrobat's "Go to page": type a number, jump there. */
@Composable
private fun GoToPageDialog(pageCount: Int, onGo: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val page = text.toIntOrNull()?.takeIf { it in 1..pageCount }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_go_to_page)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { value -> text = value.filter(Char::isDigit).take(6) },
                singleLine = true,
                label = { Text(stringResource(R.string.reader_page_range, pageCount)) },
                isError = text.isNotEmpty() && page == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { page?.let(onGo) }),
            )
        },
        confirmButton = {
            TextButton(onClick = { page?.let(onGo) }, enabled = page != null) {
                Text(stringResource(R.string.action_go))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
