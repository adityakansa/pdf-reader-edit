package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.home

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.whats.web.scan.webscan.pdfreaderpdffileedit.R
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.BinEntry
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.DocType
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.RecycleBin
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.components.FileTypeIcon
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.components.formatModified
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.shell.ScreenTitle
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.shell.TitleBar
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.BrandRed
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecycleBinViewModel @Inject constructor(private val bin: RecycleBin) : ViewModel() {
    val entries = bin.entries

    fun load() = viewModelScope.launch { bin.load() }

    fun restore(entry: BinEntry, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        onDone(bin.restore(entry) != null)
    }

    fun deleteForever(entry: BinEntry) = viewModelScope.launch { bin.deleteForever(entry) }

    fun empty() = viewModelScope.launch { bin.empty() }
}

/** Step 12a. Deleted files for 30 days: restore one, delete one for good, or empty the bin. */
@Composable
fun RecycleBinScreen(onBack: () -> Unit, viewModel: RecycleBinViewModel = hiltViewModel()) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<BinEntry?>(null) }

    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TitleBar(title = { ScreenTitle(stringResource(R.string.tool_recycle_bin)) }, onBack = onBack) {
                if (entries.isNotEmpty()) {
                    TextButton(onClick = { confirmEmpty = true }) {
                        Text(stringResource(R.string.bin_empty_action), color = BrandRed, fontWeight = FontWeight.Bold)
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
            if (entries.isEmpty()) {
                Column(
                    Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SoftCircleIcon(Icons.Filled.RestoreFromTrash, BrandRed, Modifier.size(140.dp))
                    Text(
                        stringResource(R.string.bin_empty_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 20.dp),
                    )
                    Text(
                        stringResource(R.string.bin_empty_body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                return@Box
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Text(
                        stringResource(R.string.bin_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
                items(entries, key = { it.id }) { entry ->
                    BinRow(
                        entry = entry,
                        onRestore = {
                            viewModel.restore(entry) { ok ->
                                scope.launch {
                                    snackbar.showSnackbar(
                                        if (ok) context.getString(R.string.bin_restored, entry.name)
                                        else context.getString(R.string.bin_restore_failed),
                                    )
                                }
                            }
                        },
                        onDelete = { confirmDelete = entry },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    if (confirmEmpty) {
        ConfirmDialog(
            title = stringResource(R.string.bin_empty_confirm_title),
            body = stringResource(R.string.bin_empty_confirm_body),
            confirm = stringResource(R.string.bin_empty_action),
            onConfirm = { confirmEmpty = false; viewModel.empty() },
            onDismiss = { confirmEmpty = false },
        )
    }
    confirmDelete?.let { entry ->
        ConfirmDialog(
            title = stringResource(R.string.bin_delete_title),
            body = stringResource(R.string.bin_delete_body, entry.name),
            confirm = stringResource(R.string.action_delete),
            onConfirm = { confirmDelete = null; viewModel.deleteForever(entry) },
            onDismiss = { confirmDelete = null },
        )
    }
}

@Composable
private fun BinRow(entry: BinEntry, onRestore: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        ) {
            FileTypeIcon(entry.type ?: DocType.PDF, iconSize = 38.dp)
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
            ) {
                Text(
                    entry.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val days = entry.daysLeft()
                Text(
                    "${formatModified(entry.deletedAt)}   ${Formatter.formatShortFileSize(context, entry.size)}   " +
                        pluralStringResource(R.plurals.bin_days_left, days, days),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRestore) {
                Icon(Icons.Filled.Restore, contentDescription = stringResource(R.string.bin_restore), tint = BrandRed)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.DeleteForever, contentDescription = stringResource(R.string.bin_delete_title))
            }
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(start = 68.dp, end = 16.dp),
        )
    }
}

@Composable
private fun ConfirmDialog(title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = BrandRed)) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
