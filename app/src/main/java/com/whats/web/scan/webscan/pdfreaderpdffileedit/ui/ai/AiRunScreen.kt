package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.ai

import android.graphics.Bitmap
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whats.web.scan.webscan.pdfreaderpdffileedit.R
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ai.AiJob
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ai.TargetLanguage

/**
 * FR-064 / FR-069 (S04). The file card, the target language for a translation, and the run itself.
 * Summary has no language, so the same screen shows only the card and the button.
 */
@Composable
fun AiRunScreen(
    key: String,
    job: AiJob,
    pages: List<Int>,
    onBack: () -> Unit,
    onResult: () -> Unit,
    viewModel: AiViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var sheetOpen by remember { mutableStateOf(false) }
    var helpOpen by remember { mutableStateOf(false) }
    var quitOpen by remember { mutableStateOf(false) }
    var pendingDownload by remember { mutableStateOf<TargetLanguage?>(null) }

    LaunchedEffect(key, job, pages) {
        viewModel.open(key, job)
        viewModel.setPages(pages)
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.messageShown()
        }
    }

    fun tryBack() {
        if (state.running) quitOpen = true else onBack()
    }
    BackHandler { tryBack() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { tryBack() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                    )
                }
                Text(
                    stringResource(
                        if (job == AiJob.TRANSLATE) R.string.translate_title else R.string.summary_title,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                if (job == AiJob.TRANSLATE) {
                    IconButton(onClick = { helpOpen = true }) {
                        Icon(Icons.Filled.HelpOutline, contentDescription = null)
                    }
                } else {
                    Box(Modifier.size(48.dp))
                }
            }
        },
        bottomBar = {
            Column(Modifier.padding(16.dp)) {
                if (state.running) {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                    )
                }
                Button(
                    onClick = { viewModel.run(onResult) },
                    enabled = !state.running &&
                        !state.summaryUnsupported &&
                        (job == AiJob.SUMMARY || state.target != null),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (job == AiJob.TRANSLATE) R.string.action_translate else R.string.action_summarize,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            state.file?.let { file ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val bitmap by produceState<Bitmap?>(null, pages) {
                        value = viewModel.thumbnail(pages.firstOrNull() ?: 0)
                    }
                    bitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.height(180.dp),
                        )
                    }
                    Text(
                        "${file.name} - (Page ${pages.joinToString(", ") { page -> (page + 1).toString() }})",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 12.dp),
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "${Formatter.formatShortFileSize(context, file.size)} - " +
                            stringResource(R.string.pages_count, pages.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (state.summaryUnsupported) {
                Text(
                    stringResource(R.string.ai_summary_unsupported),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }

            if (job == AiJob.TRANSLATE) {
                Text(
                    stringResource(R.string.translate_to),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .clickable { sheetOpen = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        state.target?.displayName ?: stringResource(R.string.translate_to),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
                }
            }
        }
    }

    if (sheetOpen) {
        LanguageSheet(
            languages = state.languages,
            downloaded = state.downloaded,
            onPick = { language ->
                sheetOpen = false
                if (language in state.downloaded) viewModel.setTarget(language)
                else pendingDownload = language
            },
            onDismiss = { sheetOpen = false },
        )
    }
    pendingDownload?.let { language ->
        DownloadLanguageDialog(
            language = language,
            onConfirm = { wifiOnly ->
                pendingDownload = null
                viewModel.downloadLanguage(language, wifiOnly)
            },
            onDismiss = { pendingDownload = null },
        )
    }
    if (helpOpen) {
        AlertDialog(
            onDismissRequest = { helpOpen = false },
            title = { Text(stringResource(R.string.translate_help_title)) },
            text = { Text(stringResource(R.string.translate_help_body)) },
            confirmButton = {
                TextButton(onClick = { helpOpen = false }) { Text(stringResource(R.string.action_ok)) }
            },
        )
    }
    if (quitOpen) {
        QuitDialog(
            job = job,
            onQuit = { quitOpen = false; viewModel.cancel(); onBack() },
            onDismiss = { quitOpen = false },
        )
    }
}

/** FR-064 (S04). Seven rows, a mark on the ones already downloaded and the size on the rest. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSheet(
    languages: List<TargetLanguage>,
    downloaded: Set<TargetLanguage>,
    onPick: (TargetLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.translate_to),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            textAlign = TextAlign.Center,
        )
        languages.forEach { language ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPick(language) }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(language.displayName, style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (language in downloaded) "✓" else TargetLanguage.APPROX_MODEL_SIZE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        Box(Modifier.height(24.dp))
    }
}

@Composable
private fun DownloadLanguageDialog(
    language: TargetLanguage,
    onConfirm: (wifiOnly: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var wifiOnly by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.download_language_title, language.displayName)) },
        text = {
            Column {
                Text(
                    stringResource(
                        R.string.download_language_body,
                        language.displayName,
                        TargetLanguage.APPROX_MODEL_SIZE,
                    ),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = wifiOnly, onCheckedChange = { wifiOnly = it })
                    Text(stringResource(R.string.download_wifi_only))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(wifiOnly) }) { Text(stringResource(R.string.action_download)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** FR-067 (S05). */
@Composable
fun QuitDialog(job: AiJob, onQuit: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (job == AiJob.TRANSLATE) R.string.quit_translating else R.string.quit_summarizing,
                ),
            )
        },
        text = { Text(stringResource(R.string.quit_message)) },
        confirmButton = {
            TextButton(onClick = onQuit) {
                Text(
                    stringResource(R.string.action_quit),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(R.string.action_cancel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
