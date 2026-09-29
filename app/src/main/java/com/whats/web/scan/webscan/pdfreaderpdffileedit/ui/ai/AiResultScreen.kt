package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whats.web.scan.webscan.pdfreaderpdffileedit.R
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ai.AiJob
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.components.Intents

/** FR-066. Selectable output, the AI label Play asks for, and a report path that attaches nothing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiResultScreen(
    onBack: () -> Unit,
    viewModel: AiResultViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(state.job.titleRes),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                TextButton(
                    onClick = {
                        copyToClipboard(context, state.text)
                        android.widget.Toast
                            .makeText(context, R.string.copied, android.widget.Toast.LENGTH_SHORT)
                            .show()
                    },
                ) { Text(stringResource(R.string.action_copy)) }
                TextButton(onClick = { Intents.shareText(context, state.text) }) {
                    Text(stringResource(R.string.action_share))
                }
                TextButton(onClick = viewModel::saveAsPdf, enabled = !state.saving) {
                    Text(stringResource(R.string.action_save_pdf))
                }
                if (state.job.isGenerative) {
                    TextButton(
                        onClick = {
                            Intents.reportAiContent(
                                context,
                                "AI output report — ${state.sourceName}",
                            )
                        },
                    ) { Text(stringResource(R.string.action_report)) }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            if (state.job.isGenerative) {
                Text(
                    stringResource(R.string.ai_disclaimer),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            SelectionContainer {
                Text(state.text, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("AI output", text))
}
