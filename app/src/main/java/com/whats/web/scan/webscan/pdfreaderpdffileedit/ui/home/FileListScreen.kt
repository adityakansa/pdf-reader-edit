package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whats.web.scan.webscan.pdfreaderpdffileedit.R
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ads.BannerAd
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.DocFile
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.shell.ScreenTitle
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.shell.TitleBar

/**
 * Home → a card: "PDF files", "Word files", "All files", "Directories", "Favorites". The list with
 * select, sort and search in its bar, as One Read's type lists. With [mergePicking] it opens straight in
 * selection mode for "Merge PDF".
 */
@Composable
fun FileListScreen(
    category: LibraryCategory,
    mergePicking: Boolean,
    onBack: () -> Unit,
    onOpenFile: (DocFile) -> Unit,
    onSearch: () -> Unit,
    onStorageAccess: () -> Unit,
    onFileTool: (Tool, DocFile) -> Unit,
    canShowAds: Boolean,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val actions = rememberFileActions()
    var showSort by remember { mutableStateOf(false) }
    var sortOrder by remember { mutableStateOf(state.sort) }

    LaunchedEffect(category, mergePicking) {
        viewModel.showCategory(category)
        sortOrder = viewModel.currentSort()
        if (mergePicking) {
            viewModel.startMergePicking()
            snackbar.showSnackbar(context.getString(R.string.merge_hint))
        }
    }

    BackHandler(enabled = state.selectionMode || state.openFolder != null) {
        when {
            state.selectionMode && mergePicking -> onBack()
            state.selectionMode -> viewModel.clearSelection()
            else -> viewModel.openFolder(null)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (state.selectionMode) {
                LibrarySelectionBar(state, viewModel, actions)
            } else {
                TitleBar(title = { ScreenTitle(stringResource(category.listTitle)) }, onBack = onBack) {
                    if (state.folders == null) {
                        IconButton(onClick = { viewModel.startSelection() }, enabled = state.files.isNotEmpty()) {
                            Icon(Icons.Outlined.CheckBox, contentDescription = stringResource(R.string.cd_select))
                        }
                        IconButton(onClick = { showSort = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.cd_sort))
                        }
                    }
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.cd_search))
                    }
                }
            }
        },
        bottomBar = { BannerAd(visible = canShowAds && !state.isPro) },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(Modifier.fillMaxSize()) {
                LibraryList(
                    state = state,
                    viewModel = viewModel,
                    actions = actions,
                    onOpenFile = onOpenFile,
                    onStorageAccess = onStorageAccess,
                    empty = if (category == LibraryCategory.FAVOURITES) EmptyKind.FAVOURITES else EmptyKind.DOCUMENTS,
                )
            }
        }
    }

    if (showSort) {
        SortSheet(
            view = state.view,
            onView = viewModel::setView,
            current = sortOrder,
            onPick = {
                sortOrder = it
                viewModel.setSort(it)
            },
            onDismiss = { showSort = false },
        )
    }
    FileActionsHost(actions, viewModel, snackbar, onTool = onFileTool)
}
