package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.home

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whats.web.scan.webscan.pdfreaderpdffileedit.R
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ads.BannerAd
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.DocFile
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.DocType
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.settings.SettingsContent
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.shell.BottomBar
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.shell.BrandTitle
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.shell.HomeTab
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.shell.ProAction
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.shell.ScreenTitle
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.shell.TitleBar
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.BrandRed

/**
 * FR-003 / FR-012 / FR-013 / FR-019, laid out in Step 12a like One Read: Home (file cards + tools),
 * Recent (with type tabs) and Settings, behind a three-tab bar.
 */
@Composable
fun HomeScreen(
    onOpenFile: (DocFile) -> Unit,
    onSearch: () -> Unit,
    onPaywall: () -> Unit,
    onCategory: (LibraryCategory) -> Unit,
    onTool: (Tool) -> Unit,
    onFileTool: (Tool, DocFile) -> Unit,
    onStorageAccess: () -> Unit,
    onNotices: () -> Unit,
    canShowAds: Boolean,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val actions = rememberFileActions()

    LaunchedEffect(Unit) { viewModel.refresh() }

    // FR-019: Back leaves selection first; from another tab it returns to Home before leaving the app.
    BackHandler(enabled = state.selectionMode || state.tab != HomeTab.HOME) {
        if (state.selectionMode) viewModel.clearSelection() else viewModel.selectTab(HomeTab.HOME)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            when {
                state.selectionMode -> LibrarySelectionBar(state, viewModel, actions)
                state.tab == HomeTab.HOME -> TitleBar(title = { BrandTitle() }) {
                    ProAction(state.isPro, onPaywall)
                    IconButton(onClick = { onCategory(LibraryCategory.ALL) }) {
                        Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = stringResource(R.string.list_all_files))
                    }
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.cd_search))
                    }
                }
                state.tab == HomeTab.RECENT -> TitleBar(title = { ScreenTitle(stringResource(R.string.tab_recent)) }) {
                    ProAction(state.isPro, onPaywall)
                    IconButton(onClick = { viewModel.startSelection() }, enabled = state.files.isNotEmpty()) {
                        Icon(Icons.Outlined.CheckBox, contentDescription = stringResource(R.string.cd_select))
                    }
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.cd_search))
                    }
                }
                else -> Unit // Settings brings its own title.
            }
        },
        bottomBar = {
            Column {
                if (!state.selectionMode) {
                    BottomBar(selected = state.tab, onSelect = viewModel::selectTab)
                }
                BannerAd(visible = canShowAds && !state.isPro)
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (state.tab) {
                HomeTab.HOME -> Dashboard(
                    state = state,
                    onSetDefault = { openDefaultAppSettings(context) },
                    onDismissBanner = viewModel::dismissDefaultBanner,
                    onStorageAccess = onStorageAccess,
                    onCategory = onCategory,
                    onTool = onTool,
                )

                HomeTab.RECENT -> Column(Modifier.fillMaxSize()) {
                    TypeTabs(selected = state.filter, onSelect = viewModel::setFilter)
                    LibraryList(
                        state = state,
                        viewModel = viewModel,
                        actions = actions,
                        onOpenFile = onOpenFile,
                        onStorageAccess = onStorageAccess,
                        empty = EmptyKind.RECENT,
                        relativeTime = true,
                    )
                }

                HomeTab.SETTING -> SettingsContent(
                    onPaywall = onPaywall,
                    onNotices = onNotices,
                    onFileManager = { onCategory(LibraryCategory.FOLDERS) },
                    onRecycleBin = { onTool(Tool.RECYCLE_BIN) },
                )
            }
        }
    }
    FileActionsHost(actions, viewModel, snackbar, onTool = onFileTool)

    val ratingDue by viewModel.ratingDue.collectAsStateWithLifecycle()
    var showRating by remember { mutableStateOf(false) }
    LaunchedEffect(ratingDue, state.tab) {
        // Never over a fresh start: only after the user has come back to Home for a moment.
        if (ratingDue && state.tab == HomeTab.HOME) {
            kotlinx.coroutines.delay(RATING_DELAY_MS)
            showRating = true
        }
    }
    if (showRating) {
        RatingSheet(
            onGood = {
                showRating = false
                viewModel.ratingAnswered()
                (context as? android.app.Activity)?.let { activity ->
                    val manager = com.google.android.play.core.review.ReviewManagerFactory.create(activity)
                    manager.requestReviewFlow().addOnCompleteListener { task ->
                        if (task.isSuccessful) manager.launchReviewFlow(activity, task.result)
                    }
                }
            },
            onNotReally = {
                showRating = false
                viewModel.ratingAnswered()
                com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.components.Intents.email(
                    context,
                    com.whats.web.scan.webscan.pdfreaderpdffileedit.BuildConfig.SUPPORT_EMAIL,
                    context.getString(R.string.mail_feedback_subject),
                )
            },
            onDismiss = {
                showRating = false
                viewModel.ratingAnswered()
            },
        )
    }
}

private const val RATING_DELAY_MS = 1_500L

/** "All  PDF  Word  Excel  PPT  TXT" with a red underline under the chosen one, as One Read's Recent tab. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypeTabs(selected: DocType?, onSelect: (DocType?) -> Unit) {
    val options: List<Pair<DocType?, Int>> = listOf(
        null to R.string.category_all,
        DocType.PDF to R.string.category_pdf,
        DocType.WORD to R.string.category_word,
        DocType.EXCEL to R.string.category_excel,
        DocType.PPT to R.string.category_ppt,
        DocType.TEXT to R.string.category_txt,
    )
    val index = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    PrimaryScrollableTabRow(
        selectedTabIndex = index,
        containerColor = MaterialTheme.colorScheme.surface,
        edgePadding = 8.dp,
        divider = {},
        indicator = {
            TabRowDefaults.PrimaryIndicator(
                modifier = Modifier.tabIndicatorOffset(index, matchContentSize = true),
                color = BrandRed,
                width = 24.dp,
            )
        },
    ) {
        options.forEachIndexed { i, (type, label) ->
            val isSelected = i == index
            Tab(
                selected = isSelected,
                onClick = { onSelect(type) },
                selectedContentColor = BrandRed,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                text = {
                    Text(
                        stringResource(label),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                },
            )
        }
    }
}

/** FR-021: where the phone lets the user make this app the one that opens PDFs. */
fun openDefaultAppSettings(context: android.content.Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, Uri.parse("package:${context.packageName}"))
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    }
    runCatching { context.startActivity(intent) }
}
