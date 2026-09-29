package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whats.web.scan.webscan.pdfreaderpdffileedit.R
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ads.BannerAd
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.DocFile
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.DocType
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.mimeType
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.components.FileRow
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.components.Intents
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.components.TypeChips
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.settings.SettingsContent
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.shell.AiMode
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.shell.BottomBar
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.shell.HomeTab
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.shell.HomeTopBar
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.BrandRed
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.CrownGold
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.ai.AiAssistantDialog
import kotlinx.coroutines.launch

/** FR-003 / FR-012 / FR-013 / FR-019. The four tabs plus everything that hangs off the library rows. */
@Composable
fun HomeScreen(
    onOpenFile: (DocFile) -> Unit,
    onSearch: () -> Unit,
    onPaywall: () -> Unit,
    onImageToPdf: () -> Unit,
    onScan: () -> Unit,
    onAi: (AiMode) -> Unit,
    onStorageAccess: () -> Unit,
    onNotices: () -> Unit,
    canShowAds: Boolean,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var showSort by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var showAi by remember { mutableStateOf(false) }
    var menuFile by remember { mutableStateOf<DocFile?>(null) }
    var infoFile by remember { mutableStateOf<DocFile?>(null) }
    var deleteTargets by remember { mutableStateOf<List<DocFile>>(emptyList()) }
    var sortOrder by remember { mutableStateOf(state.sort) }

    LaunchedEffect(Unit) {
        sortOrder = viewModel.currentSort()
        viewModel.refresh()
    }

    // FR-019: Back leaves selection first; from another tab it returns to Document before leaving the app.
    BackHandler(enabled = state.selectionMode || state.tab != HomeTab.DOCUMENT) {
        if (state.selectionMode) viewModel.clearSelection() else viewModel.selectTab(HomeTab.DOCUMENT)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (state.tab != HomeTab.SETTING) {
                HomeTopBar(
                    isPro = state.isPro,
                    selectionMode = state.selectionMode,
                    selectionCount = state.selected.size,
                    allSelected = state.files.isNotEmpty() && state.selected.size == state.files.size,
                    onSelectAll = viewModel::toggleSelectAll,
                    onSearch = onSearch,
                    onPro = onPaywall,
                    onSort = { showSort = true },
                    onToggleSelection = { viewModel.startSelection() },
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
                    onDeleteSelected = { deleteTargets = viewModel.selectedFiles() },
                    onExitSelection = viewModel::clearSelection,
                )
            }
        },
        bottomBar = {
            Column {
                BannerAd(visible = canShowAds && !state.isPro)
                BottomBar(
                    selected = state.tab,
                    onSelect = viewModel::selectTab,
                    onCreate = { showCreate = true },
                )
            }
        },
        floatingActionButton = {
            if (state.tab == HomeTab.DOCUMENT && !state.selectionMode) {
                Box {
                    ExtendedFloatingActionButton(
                        onClick = { showAi = true },
                        containerColor = BrandRed,
                        contentColor = Color.White,
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                        Text(
                            stringResource(R.string.ai_assistant),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    if (!state.isPro) {
                        Icon(
                            Icons.Filled.WorkspacePremium,
                            contentDescription = null,
                            tint = CrownGold,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .background(BrandRed, androidx.compose.foundation.shape.CircleShape)
                                .size(18.dp),
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
            when (state.tab) {
                HomeTab.SETTING -> SettingsContent(
                    onPaywall = onPaywall,
                    onNotices = onNotices,
                )

                else -> LibraryList(
                    state = state,
                    onFilter = viewModel::setFilter,
                    onOpen = { file ->
                        if (state.selectionMode) viewModel.toggleSelected(file.key) else {
                            viewModel.markOpened(file.key)
                            onOpenFile(file)
                        }
                    },
                    onLongPress = { file -> viewModel.startSelection(file.key) },
                    onToggleFavourite = { viewModel.toggleFavourite(it.key) },
                    onMenu = { menuFile = it },
                    onStorageAccess = onStorageAccess,
                    onScan = onScan,
                    onImageToPdf = onImageToPdf,
                )
            }
        }
    }

    if (showSort) {
        SortSheet(
            current = sortOrder,
            onPick = {
                sortOrder = it
                viewModel.setSort(it)
            },
            onDismiss = { showSort = false },
        )
    }
    if (showCreate) {
        CreatePdfSheet(
            onImageToPdf = { showCreate = false; onImageToPdf() },
            onScan = { showCreate = false; onScan() },
            onDismiss = { showCreate = false },
        )
    }
    if (showAi) {
        AiAssistantDialog(
            onTranslate = { showAi = false; onAi(AiMode.TRANSLATE) },
            onSummary = { showAi = false; onAi(AiMode.SUMMARY) },
            onDismiss = { showAi = false },
        )
    }
    menuFile?.let { file ->
        FileMenuSheet(
            file = file,
            onShare = {
                menuFile = null
                Intents.shareFile(context, viewModel.shareableUri(file), file.mimeType)
            },
            onDelete = { menuFile = null; deleteTargets = listOf(file) },
            onInfo = { menuFile = null; infoFile = file },
            onDismiss = { menuFile = null },
        )
    }
    infoFile?.let { file ->
        val pages by produceState<Int?>(null, file.key) { value = viewModel.pageCount(file) }
        FileInfoDialog(
            file = file,
            location = viewModel.locationOf(file),
            pages = pages,
            onDismiss = { infoFile = null },
        )
    }
    if (deleteTargets.isNotEmpty()) {
        val targets = deleteTargets
        ConfirmDeleteDialog(
            count = targets.size,
            onConfirm = {
                deleteTargets = emptyList()
                viewModel.delete(targets) { ok ->
                    if (!ok) {
                        scope.launch { snackbar.showSnackbar(context.getString(R.string.delete_failed)) }
                    }
                }
            },
            onDismiss = { deleteTargets = emptyList() },
        )
    }
}

@Composable
private fun LibraryList(
    state: HomeUiState,
    onFilter: (DocType?) -> Unit,
    onOpen: (DocFile) -> Unit,
    onLongPress: (DocFile) -> Unit,
    onToggleFavourite: (DocFile) -> Unit,
    onMenu: (DocFile) -> Unit,
    onStorageAccess: () -> Unit,
    onScan: () -> Unit,
    onImageToPdf: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TypeChips(selected = state.filter, onSelect = onFilter)
        if (state.files.isEmpty()) {
            EmptyState(state, onStorageAccess, onScan, onImageToPdf)
            return@Column
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            items(state.files, key = { it.file.key }) { item ->
                FileRow(
                    item = item,
                    onOpen = { onOpen(item.file) },
                    onToggleFavourite = { onToggleFavourite(item.file) },
                    onMenu = { onMenu(item.file) },
                    selectionMode = state.selectionMode,
                    selected = item.file.key in state.selected,
                    onLongPress = { onLongPress(item.file) },
                )
            }
        }
    }
}

/**
 * What an empty list says. Adobe Acrobat and Adobe Scan never leave a blank screen: they say why it is
 * empty and offer the next step, so this does the same per tab.
 */
@Composable
private fun EmptyState(
    state: HomeUiState,
    onStorageAccess: () -> Unit,
    onScan: () -> Unit,
    onImageToPdf: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (state.scanning && state.tab == HomeTab.DOCUMENT) {
                CircularProgressIndicator(color = BrandRed)
                Text(
                    stringResource(R.string.library_scanning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
                return@Column
            }
            val (icon, title, body) = when (state.tab) {
                HomeTab.RECENT -> Triple(Icons.Filled.History, R.string.empty_recent_title, R.string.empty_recent)
                HomeTab.FAVOURITE -> Triple(Icons.Outlined.StarBorder, R.string.empty_favourite_title, R.string.empty_favourite)
                else -> Triple(Icons.Outlined.Description, R.string.empty_documents, R.string.empty_documents_body)
            }
            Icon(
                icon,
                contentDescription = null,
                tint = BrandRed,
                modifier = Modifier
                    .size(72.dp)
                    .background(BrandRed.copy(alpha = 0.08f), CircleShape)
                    .padding(16.dp),
            )
            Text(
                stringResource(title),
                style = MaterialTheme.typography.titleMedium,
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
            if (state.tab == HomeTab.DOCUMENT) {
                if (!state.hasStorageAccess) {
                    Button(
                        onClick = onStorageAccess,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                        modifier = Modifier.padding(top = 20.dp),
                    ) { Text(stringResource(R.string.storage_grant)) }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    OutlinedButton(onClick = onScan) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.scan_document), modifier = Modifier.padding(start = 8.dp))
                    }
                    OutlinedButton(onClick = onImageToPdf) {
                        Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.image_to_pdf), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}
