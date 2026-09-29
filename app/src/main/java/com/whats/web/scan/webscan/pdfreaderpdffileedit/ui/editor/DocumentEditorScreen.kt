package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whats.web.scan.webscan.pdfreaderpdffileedit.R
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.FileNames
import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.EditorActions
import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.EditorActions.LineStyle

/**
 * Write a new document (saved as Word, PDF or text) or edit a .txt file. The formatting bar inserts
 * simple marks (# heading, **bold**, _italic_, - bullet, 1. list) that the Word and PDF exports turn
 * into real formatting; text files are edited as plain text, without the bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentEditorScreen(
    key: String?,
    onBack: () -> Unit,
    onSaved: (android.net.Uri, String?) -> Unit,
    viewModel: DocumentEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var loaded by rememberSaveable { mutableStateOf(key == null) }
    var confirmLeave by remember { mutableStateOf(false) }
    var saveAs by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val isNew = key == null
    val changed = value.text != state.initialText

    LaunchedEffect(key) { viewModel.load(key) }
    LaunchedEffect(state.loading, state.initialText) {
        if (!loaded && !state.loading && state.file != null) {
            value = TextFieldValue(state.initialText)
            loaded = true
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(state.saved) { state.saved?.let { onSaved(it.uri, state.savedMime) } }
    LaunchedEffect(state.error) {
        if (state.error) {
            snackbar.showSnackbar(context.getString(R.string.editor_save_failed))
            viewModel.messageShown()
        }
    }

    fun leave() {
        if (changed && value.text.isNotBlank()) confirmLeave = true else onBack()
    }
    BackHandler { leave() }

    fun apply(edit: EditorActions.Edit) {
        value = TextFieldValue(edit.text, TextRange(edit.start, edit.end))
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.file?.name ?: stringResource(R.string.editor_new_title), maxLines = 1)
                        Text(
                            pluralStringResource(R.plurals.word_count, EditorActions.wordCount(value.text), EditorActions.wordCount(value.text)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { leave() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { if (isNew) saveAs = true else viewModel.saveText(value.text) },
                        enabled = value.text.isNotBlank() && !state.saving && (isNew || changed),
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.action_save))
                    }
                },
            )
        },
        bottomBar = {
            if (isNew) {
                Surface(shadowElevation = 6.dp) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    ) {
                        FormatButton(stringResource(R.string.format_heading), textIcon = "H1") {
                            apply(EditorActions.toggleLine(value.text, value.selection.min, value.selection.max, LineStyle.HEADING1))
                        }
                        FormatButton(stringResource(R.string.format_subheading), textIcon = "H2") {
                            apply(EditorActions.toggleLine(value.text, value.selection.min, value.selection.max, LineStyle.HEADING2))
                        }
                        FormatIcon(Icons.Filled.FormatBold, stringResource(R.string.format_bold)) {
                            apply(EditorActions.wrap(value.text, value.selection.min, value.selection.max, "**"))
                        }
                        FormatIcon(Icons.Filled.FormatItalic, stringResource(R.string.format_italic)) {
                            apply(EditorActions.wrap(value.text, value.selection.min, value.selection.max, "_"))
                        }
                        FormatIcon(Icons.AutoMirrored.Filled.FormatListBulleted, stringResource(R.string.format_bullets)) {
                            apply(EditorActions.toggleLine(value.text, value.selection.min, value.selection.max, LineStyle.BULLET))
                        }
                        FormatIcon(Icons.Filled.FormatListNumbered, stringResource(R.string.format_numbers)) {
                            apply(EditorActions.toggleLine(value.text, value.selection.min, value.selection.max, LineStyle.NUMBERED))
                        }
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
            if (state.loading || state.saving) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
                return@Box
            }
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(focus)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                decorationBox = { field ->
                    if (value.text.isEmpty()) {
                        Text(
                            stringResource(if (isNew) R.string.editor_placeholder else R.string.editor_placeholder_text),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    field()
                },
            )
        }
    }

    if (saveAs) {
        SaveDocumentDialog(
            suggested = viewModel.suggestedName(),
            onSave = { name, format ->
                saveAs = false
                viewModel.saveNew(value.text, name, format)
            },
            onDismiss = { saveAs = false },
        )
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.sign_leave_title)) },
            text = { Text(stringResource(R.string.editor_leave_body)) },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false; onBack() }) {
                    Text(stringResource(R.string.action_discard), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.sign_keep_editing)) }
            },
        )
    }
}

@Composable
private fun FormatIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription = label) }
}

@Composable
private fun FormatButton(label: String, textIcon: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }) {
        Text(textIcon, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 2.dp))
    }
}

/** File name plus format: Word (default, keeps formatting), PDF (for sharing), or plain text. */
@Composable
private fun SaveDocumentDialog(suggested: String, onSave: (String, SaveFormat) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(TextFieldValue(suggested, TextRange(0, suggested.length))) }
    var format by remember { mutableStateOf(SaveFormat.WORD) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_save_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.save_as_name)) },
                    suffix = { Text(".${format.extension}") },
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                listOf(
                    SaveFormat.WORD to R.string.format_word,
                    SaveFormat.PDF to R.string.format_pdf,
                    SaveFormat.TEXT to R.string.format_text,
                ).forEach { (option, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = format == option, role = Role.RadioButton) { format = option },
                    ) {
                        RadioButton(selected = format == option, onClick = null)
                        Text(stringResource(label), modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 12.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(FileNames.sanitize(name.text, suggested), format) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
