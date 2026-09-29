package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.home

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.whats.web.scan.webscan.pdfreaderpdffileedit.R
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.DocFile
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.prefs.LibraryView
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.prefs.SortField
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.prefs.SortOrder
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.components.formatModified
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.BrandRed

/** FR-014 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortSheet(
    view: LibraryView,
    onView: (LibraryView) -> Unit,
    current: SortOrder,
    onPick: (SortOrder) -> Unit,
    onDismiss: () -> Unit,
) {
    var order by remember { mutableStateOf(current) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            // View first: list or page previews, as Adobe Scan offers on "All scans".
            Text(
                stringResource(R.string.view_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                FilterChip(
                    selected = view == LibraryView.LIST,
                    onClick = { onView(LibraryView.LIST) },
                    label = { Text(stringResource(R.string.view_list)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = null) },
                )
                FilterChip(
                    selected = view == LibraryView.GRID,
                    onClick = { onView(LibraryView.GRID) },
                    label = { Text(stringResource(R.string.view_grid)) },
                    leadingIcon = { Icon(Icons.Filled.GridView, contentDescription = null) },
                )
                FilterChip(
                    selected = view == LibraryView.FOLDERS,
                    onClick = { onView(LibraryView.FOLDERS) },
                    label = { Text(stringResource(R.string.view_folders)) },
                    leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                )
            }
            Text(
                stringResource(R.string.sort_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            listOf(
                SortField.NAME to R.string.sort_name,
                SortField.DATE to R.string.sort_date,
                SortField.SIZE to R.string.sort_size,
            ).forEach { (field, label) ->
                SheetRadio(stringResource(label), order.field == field) {
                    order = order.copy(field = field)
                    onPick(order)
                }
            }
            listOf(true to R.string.sort_ascending, false to R.string.sort_descending).forEach { (asc, label) ->
                SheetRadio(stringResource(label), order.ascending == asc) {
                    order = order.copy(ascending = asc)
                    onPick(order)
                }
            }
        }
    }
}

@Composable
private fun SheetRadio(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
    }
}

/** FR-040 (S11) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePdfSheet(onImageToPdf: () -> Unit, onScan: () -> Unit, onMerge: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 40.dp)) {
            Text(
                stringResource(R.string.create_pdf_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onImageToPdf, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        stringResource(R.string.image_to_pdf),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                OutlinedButton(onClick = onScan, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        stringResource(R.string.scan_document),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            OutlinedButton(
                onClick = onMerge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                Icon(Icons.Filled.MergeType, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.merge_pdfs), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** FR-018 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileMenuSheet(
    file: DocFile,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onInfo: () -> Unit,
    onDismiss: () -> Unit,
    onOrganize: (() -> Unit)? = null,
    onProtect: (() -> Unit)? = null,
    onUnlock: (() -> Unit)? = null,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 32.dp)) {
            Text(
                file.name,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            MenuRow(Icons.Filled.Share, stringResource(R.string.action_share), onShare)
            onOrganize?.let { MenuRow(Icons.Filled.Dashboard, stringResource(R.string.organize_pages), it) }
            onProtect?.let { MenuRow(Icons.Filled.Lock, stringResource(R.string.protect_pdf), it) }
            onUnlock?.let { MenuRow(Icons.Filled.LockOpen, stringResource(R.string.unlock_pdf), it) }
            if (!file.isSample) {
                MenuRow(Icons.Filled.Delete, stringResource(R.string.action_delete), onDelete)
            }
            MenuRow(Icons.Filled.Info, stringResource(R.string.action_file_info), onInfo)
        }
    }
}

@Composable
private fun MenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = false, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
fun ConfirmDeleteDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_title)) },
        text = {
            Text(
                if (count > 1) stringResource(R.string.delete_message_many, count)
                else stringResource(R.string.delete_message),
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = BrandRed),
            ) { Text(stringResource(R.string.action_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** FR-018 File info. `pages` is null for anything that is not a PDF. */
@Composable
fun FileInfoDialog(file: DocFile, location: String, pages: Int?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.file_info_title)) },
        text = {
            Column {
                InfoLine(stringResource(R.string.file_info_name), file.name)
                InfoLine(stringResource(R.string.file_info_location), location)
                InfoLine(
                    stringResource(R.string.file_info_size),
                    Formatter.formatShortFileSize(context, file.size),
                )
                InfoLine(stringResource(R.string.file_info_modified), formatModified(file.modified))
                if (pages != null) InfoLine(stringResource(R.string.file_info_pages), pages.toString())
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Text(
            "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
