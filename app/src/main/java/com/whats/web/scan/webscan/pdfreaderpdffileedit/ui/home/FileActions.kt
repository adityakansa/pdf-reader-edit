package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whats.web.scan.webscan.pdfreaderpdffileedit.R
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.DocFile
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.DocType
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.FolderNames
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.mimeType
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.components.FileCard
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.components.FileRow
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.components.Intents
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.components.LocalThumbnails
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.components.PasswordDialog
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.components.SaveAsDialog
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.components.formatRelative
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.shell.SelectionBar
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.BrandRed
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.FolderYellow
import kotlinx.coroutines.launch

/** The dialogs a row's ⋮ menu and the selection bar can open; one instance per list screen. */
@Stable
class FileActionsState {
    var menuFile by mutableStateOf<DocFile?>(null)
    var infoFile by mutableStateOf<DocFile?>(null)
    var deleteTargets by mutableStateOf<List<DocFile>>(emptyList())
    var mergeName by mutableStateOf<String?>(null)
    var protectFile by mutableStateOf<DocFile?>(null)
    var unlockFile by mutableStateOf<DocFile?>(null)
    var unlockWrong by mutableStateOf(false)
}

/** The selection bar wired to [viewModel], for any list screen in selection mode. */
@Composable
fun LibrarySelectionBar(state: HomeUiState, viewModel: HomeViewModel, actions: FileActionsState) {
    val context = LocalContext.current
    SelectionBar(
        selectionCount = state.selected.size,
        allSelected = state.files.isNotEmpty() && state.selected.size == state.files.size,
        onSelectAll = viewModel::toggleSelectAll,
        canMerge = state.selected.size >= 2 && viewModel.canMergeSelection(),
        onMergeSelected = { actions.mergeName = viewModel.suggestedMergeName() },
        onShareSelected = {
            val files = viewModel.selectedFiles()
            if (files.isNotEmpty()) {
                Intents.shareFiles(
                    context,
                    files.map(viewModel::shareableUri),
                    if (files.distinctBy { it.type }.size == 1) files.first().mimeType else "*/*",
                )
            }
        },
        onDeleteSelected = { actions.deleteTargets = viewModel.selectedFiles() },
        onExitSelection = viewModel::clearSelection,
    )
}

/**
 * Everything that hangs off a library row: the ⋮ sheet, file info, delete to the recycle bin, merge,
 * protect and unlock, and the "Working…" dialog while a PDF tool runs. Shared by Recent and every
 * category list so they behave the same.
 */
@Composable
fun FileActionsHost(
    actions: FileActionsState,
    viewModel: HomeViewModel,
    snackbar: SnackbarHostState,
    onTool: (Tool, DocFile) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val thumbnails = LocalThumbnails.current
    val toolBusy by viewModel.toolBusy.collectAsStateWithLifecycle()

    fun announce(result: HomeViewModel.ToolResult) {
        val message = when (result) {
            is HomeViewModel.ToolResult.Saved -> context.getString(R.string.saved_to, result.name)
            HomeViewModel.ToolResult.WrongPassword -> context.getString(R.string.reader_password_wrong)
            HomeViewModel.ToolResult.Failed -> context.getString(R.string.tool_failed)
        }
        scope.launch { snackbar.showSnackbar(message) }
    }

    actions.menuFile?.let { file ->
        val locked = thumbnails?.cached(file)?.locked == true
        FileMenuSheet(
            file = file,
            tools = toolsFor(file.type),
            onTool = { tool -> actions.menuFile = null; onTool(tool, file) },
            onShare = {
                actions.menuFile = null
                Intents.shareFile(context, viewModel.shareableUri(file), file.mimeType)
            },
            onDelete = { actions.menuFile = null; actions.deleteTargets = listOf(file) },
            onInfo = { actions.menuFile = null; actions.infoFile = file },
            onDismiss = { actions.menuFile = null },
            onOrganize = if (file.type == DocType.PDF) {
                { actions.menuFile = null; onTool(Tool.MANAGE_PAGES, file) }
            } else {
                null
            },
            onProtect = if (file.type == DocType.PDF && !locked) {
                { actions.menuFile = null; actions.protectFile = file }
            } else {
                null
            },
            onUnlock = if (file.type == DocType.PDF && locked) {
                { actions.menuFile = null; actions.unlockWrong = false; actions.unlockFile = file }
            } else {
                null
            },
        )
    }
    actions.infoFile?.let { file ->
        val pages by produceState<Int?>(null, file.key) { value = viewModel.pageCount(file) }
        FileInfoDialog(
            file = file,
            location = viewModel.locationOf(file),
            pages = pages,
            onDismiss = { actions.infoFile = null },
        )
    }
    actions.mergeName?.let { suggested ->
        SaveAsDialog(
            suggested = suggested,
            onSave = { name -> actions.mergeName = null; viewModel.mergeSelected(name, ::announce) },
            onDismiss = { actions.mergeName = null },
        )
    }
    actions.protectFile?.let { file ->
        PasswordDialog(
            title = stringResource(R.string.protect_pdf),
            message = stringResource(R.string.protect_body),
            confirm = true,
            confirmLabel = stringResource(R.string.action_protect),
            onConfirm = { password -> actions.protectFile = null; viewModel.protect(file, password, ::announce) },
            onDismiss = { actions.protectFile = null },
        )
    }
    actions.unlockFile?.let { file ->
        PasswordDialog(
            title = stringResource(R.string.unlock_pdf),
            message = stringResource(R.string.unlock_body),
            confirm = false,
            confirmLabel = stringResource(R.string.action_unlock),
            error = if (actions.unlockWrong) stringResource(R.string.reader_password_wrong) else null,
            onConfirm = { password ->
                viewModel.unlock(file, password) { result ->
                    if (result == HomeViewModel.ToolResult.WrongPassword) {
                        actions.unlockWrong = true
                    } else {
                        actions.unlockFile = null
                        announce(result)
                    }
                }
            },
            onDismiss = { actions.unlockFile = null },
        )
    }
    if (toolBusy) {
        // Merging big files takes a few seconds; a blocking indicator says the tap was taken.
        Dialog(onDismissRequest = {}) {
            Surface(shape = MaterialTheme.shapes.large) {
                Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = BrandRed, modifier = Modifier.size(28.dp))
                    Text(stringResource(R.string.tool_working), modifier = Modifier.padding(start = 16.dp))
                }
            }
        }
    }
    if (actions.deleteTargets.isNotEmpty()) {
        val targets = actions.deleteTargets
        ConfirmDeleteDialog(
            count = targets.size,
            onConfirm = {
                actions.deleteTargets = emptyList()
                viewModel.delete(targets) { ok ->
                    scope.launch {
                        snackbar.showSnackbar(
                            context.getString(if (ok) R.string.moved_to_bin else R.string.delete_failed),
                        )
                    }
                }
            },
            onDismiss = { actions.deleteTargets = emptyList() },
        )
    }
}

/** What a list shows when it has nothing, per screen. */
enum class EmptyKind { DOCUMENTS, RECENT, FAVOURITES }

/**
 * The rows (or preview cards) of a library list. [relativeTime] shows "2 minutes ago" instead of the date,
 * as Recent does.
 */
@Composable
fun LibraryList(
    state: HomeUiState,
    viewModel: HomeViewModel,
    actions: FileActionsState,
    onOpenFile: (DocFile) -> Unit,
    onStorageAccess: () -> Unit,
    empty: EmptyKind,
    relativeTime: Boolean = false,
) {
    fun open(file: DocFile) {
        if (state.selectionMode) {
            viewModel.toggleSelected(file.key)
        } else {
            viewModel.markOpened(file.key)
            onOpenFile(file)
        }
    }
    Column(Modifier.fillMaxSize()) {
        state.folders?.let { folders ->
            FolderList(folders, onOpen = viewModel::openFolder)
            return@Column
        }
        state.openFolder?.let { folder ->
            FolderHeader(folder, count = state.files.size, onBack = { viewModel.openFolder(null) })
        }
        if (state.files.isEmpty()) {
            EmptyState(state, empty, onStorageAccess)
            return@Column
        }
        if (state.grid) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                gridItems(state.files, key = { it.file.key }) { item ->
                    FileCard(
                        item = item,
                        onOpen = { open(item.file) },
                        onToggleFavourite = { viewModel.toggleFavourite(item.file.key) },
                        onMenu = { actions.menuFile = item.file },
                        selectionMode = state.selectionMode,
                        selected = item.file.key in state.selected,
                        onLongPress = { viewModel.startSelection(item.file.key) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(state.files, key = { it.file.key }) { item ->
                    FileRow(
                        item = item,
                        onOpen = { open(item.file) },
                        onToggleFavourite = { viewModel.toggleFavourite(item.file.key) },
                        onMenu = { actions.menuFile = item.file },
                        selectionMode = state.selectionMode,
                        selected = item.file.key in state.selected,
                        onLongPress = { viewModel.startSelection(item.file.key) },
                        time = if (relativeTime && item.lastOpenedAt > 0) formatRelative(item.lastOpenedAt) else null,
                        // Rows slide into place when the sort order, a tab or a delete changes the list.
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

/**
 * What an empty list says. Adobe Acrobat and One Read never leave a blank screen: they say why it is
 * empty and what to do next.
 */
@Composable
private fun EmptyState(state: HomeUiState, kind: EmptyKind, onStorageAccess: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (state.scanning && kind == EmptyKind.DOCUMENTS) {
                CircularProgressIndicator(color = BrandRed)
                Text(
                    stringResource(R.string.library_scanning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
                return@Column
            }
            val (icon, title, body) = when (kind) {
                EmptyKind.RECENT -> Triple(Icons.Filled.History, R.string.empty_recent_title, R.string.empty_recent)
                EmptyKind.FAVOURITES -> Triple(Icons.Outlined.StarBorder, R.string.empty_favourite_title, R.string.empty_favourite)
                EmptyKind.DOCUMENTS -> Triple(Icons.Outlined.Description, R.string.empty_documents, R.string.empty_documents_body)
            }
            SoftCircleIcon(icon, BrandRed)
            Text(
                stringResource(title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                stringResource(body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (kind == EmptyKind.DOCUMENTS && !state.hasStorageAccess) {
                androidx.compose.material3.Button(
                    onClick = onStorageAccess,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = BrandRed),
                    modifier = Modifier.padding(top = 20.dp),
                ) { Text(stringResource(R.string.storage_grant)) }
            }
        }
    }
}

/** The Directories list: every folder that holds documents, biggest first, like a file manager's list. */
@Composable
private fun FolderList(folders: List<FolderItem>, onOpen: (String) -> Unit) {
    if (folders.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.folders_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        items(folders, key = { it.id }) { folder ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItem()
                    .clickable(role = Role.Button) { onOpen(folder.id) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Icon(
                    Icons.Filled.Folder,
                    contentDescription = null,
                    tint = FolderYellow,
                    modifier = Modifier.size(40.dp),
                )
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 14.dp),
                ) {
                    Text(folder.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(
                        folder.path,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    pluralStringResource(R.plurals.file_count, folder.count, folder.count),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FolderHeader(folder: String, count: Int, onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(role = Role.Button, onClick = onBack)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
        Icon(Icons.Filled.Folder, contentDescription = null, tint = FolderYellow, modifier = Modifier.padding(start = 8.dp))
        Column(Modifier.padding(start = 10.dp)) {
            Text(FolderNames.displayName(folder), style = MaterialTheme.typography.titleSmall)
            Text(
                pluralStringResource(R.plurals.file_count, count, count),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Keeps a [FileActionsState] across recompositions. */
@Composable
fun rememberFileActions(): FileActionsState = remember { FileActionsState() }
