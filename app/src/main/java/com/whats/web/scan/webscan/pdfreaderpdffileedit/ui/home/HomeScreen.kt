package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (state.tab != HomeTab.SETTING) {
                HomeTopBar(
                    isPro = state.isPro,
                    selectionCount = state.selected.size,
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
        FileInfoDialog(
            file = file,
            location = viewModel.locationOf(file),
            pages = null,
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
) {
    Column(Modifier.fillMaxSize()) {
        TypeChips(selected = state.filter, onSelect = onFilter)
        if (state.files.isEmpty()) {
            EmptyState(state, onStorageAccess)
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

@Composable
private fun EmptyState(state: HomeUiState, onStorageAccess: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(
                    when (state.tab) {
                        HomeTab.RECENT -> R.string.empty_recent
                        HomeTab.FAVOURITE -> R.string.empty_favourite
                        else -> R.string.empty_documents
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!state.hasStorageAccess && state.tab == HomeTab.DOCUMENT) {
                androidx.compose.material3.TextButton(onClick = onStorageAccess) {
                    Text(stringResource(R.string.storage_grant))
                }
            }
        }
    }
}
