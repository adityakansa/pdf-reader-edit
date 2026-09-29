package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.DocFile
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.DocType
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.isLegacyOffice
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.ai.AiFilePicker
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.ai.AiResultScreen
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.ai.AiRunScreen
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.ai.SelectPageScreen
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.home.FileListScreen
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.home.HomeScreen
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.home.LibraryCategory
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.home.RecycleBinScreen
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.home.Tool
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.pdfeditor.AfterSave
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.convert.ConvertScreen
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.pdfeditor.EditorTool
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.pdfeditor.PdfEditorScreen
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.home.StorageAccessScreen
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.imagetopdf.ImageToPdfScreen
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.scan.CameraScreen
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.scan.review.PageReviewScreen
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.paywall.PaywallScreen
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.reader.OfficeReaderScreen
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.reader.PdfReaderScreen
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.search.SearchScreen
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.sign.PlaceOnPdfScreen
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.settings.NoticesScreen
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.tools.OrganizePagesScreen
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.editor.DocumentEditorScreen

@Composable
fun AppNavHost(
    viewModel: ShellViewModel,
    navController: NavHostController = rememberNavController(),
) {
    val canShowAds by viewModel.canShowAds.collectAsStateWithLifecycle()

    fun openFile(file: DocFile) {
        val route = if (file.type == DocType.PDF && !file.isLegacyOffice) {
            Routes.Reader(file.key)
        } else {
            Routes.OfficeReader(file.key)
        }
        navController.navigate(route)
    }

    /** Home → a tool tile. Tools that work on a file ask for one first. */
    fun startTool(tool: Tool) {
        when (tool) {
            Tool.IMAGE_TO_PDF -> navController.navigate(Routes.ImageToPdf)
            Tool.SCAN_TO_PDF -> navController.navigate(Routes.Scan)
            Tool.CREATE_PDF -> navController.navigate(Routes.Editor())
            Tool.MERGE_PDF -> navController.navigate(Routes.FileList(LibraryCategory.PDF.name, merge = true))
            Tool.RECYCLE_BIN -> navController.navigate(Routes.RecycleBin)
            Tool.AI_TRANSLATE -> navController.navigate(Routes.AiFilePicker(AiMode.TRANSLATE.name))
            Tool.AI_SUMMARY -> navController.navigate(Routes.AiFilePicker(AiMode.SUMMARY.name))
            Tool.AI_EXTRACT -> navController.navigate(Routes.AiFilePicker(AiMode.EXTRACT_TEXT.name))
            else -> navController.navigate(Routes.ToolPicker(tool.name))
        }
    }

    /** A tool on a chosen file, from the picker or a file's ⋮ menu. */
    fun runTool(tool: Tool, file: DocFile) {
        when (tool) {
            Tool.ANNOTATE -> navController.navigate(Routes.Reader(file.key, annotate = true))
            Tool.EDIT_TEXT -> navController.navigate(Routes.PdfEditor(file.key))
            Tool.ADD_TEXT -> navController.navigate(Routes.PdfEditor(file.key, EditorTool.ADD_TEXT.name))
            Tool.FILL_SIGN ->
                navController.navigate(if (viewModel.isPro.value) Routes.PlaceOnPdf(file.key) else Routes.Paywall)
            Tool.SPLIT_PDF, Tool.MANAGE_PAGES -> navController.navigate(Routes.OrganizePages(file.key))
            Tool.PDF_TO_WORD, Tool.PDF_TO_IMAGE, Tool.WORD_TO_PDF, Tool.PPT_TO_PDF, Tool.EXCEL_TO_PDF, Tool.COMPRESS_PDF ->
                navController.navigate(Routes.Convert(file.key, tool.name))
            else -> startTool(tool)
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.incomingFiles.collect { file -> openFile(file) }
    }

    // Edge-to-edge is on (and enforced from Android 15), but the app's bars are custom rows, not Material
    // app bars, so nothing else reserves room for the status and navigation bars. Padding the whole host
    // once keeps every icon clear of the clock, the cut-out and the gesture bar; it also consumes the insets,
    // so no screen pads them twice. The camera keeps a black frame instead of the app background.
    val backStackEntry by navController.currentBackStackEntryAsState()
    val onCamera = backStackEntry?.destination?.hasRoute(Routes.Scan::class) == true
    Box(
        Modifier
            .fillMaxSize()
            .background(if (onCamera) Color.Black else MaterialTheme.colorScheme.surface)
            .safeDrawingPadding(),
    ) {
        NavHost(navController = navController, startDestination = Routes.Home) {
            composable<Routes.Home> {
                HomeScreen(
                    onOpenFile = ::openFile,
                    onSearch = { navController.navigate(Routes.Search) },
                    onPaywall = { navController.navigate(Routes.Paywall) },
                    onCategory = { category -> navController.navigate(Routes.FileList(category.name)) },
                    onTool = ::startTool,
                    onFileTool = ::runTool,
                    onStorageAccess = { navController.navigate(Routes.StorageAccess) },
                    onNotices = { navController.navigate(Routes.Notices) },
                    canShowAds = canShowAds,
                )
            }
            composable<Routes.FileList> { entry ->
                val route = entry.toRoute<Routes.FileList>()
                FileListScreen(
                    category = LibraryCategory.valueOf(route.category),
                    mergePicking = route.merge,
                    onBack = navController::popBackStack,
                    onOpenFile = ::openFile,
                    onSearch = { navController.navigate(Routes.Search) },
                    onStorageAccess = { navController.navigate(Routes.StorageAccess) },
                    onFileTool = ::runTool,
                    canShowAds = canShowAds,
                )
            }
            composable<Routes.ToolPicker> { entry ->
                val tool = Tool.valueOf(entry.toRoute<Routes.ToolPicker>().tool)
                AiFilePicker(
                    onBack = navController::popBackStack,
                    onPick = { file ->
                        // The picker leaves the stack, so Back from the tool returns to Home.
                        navController.popBackStack()
                        runTool(tool, file)
                    },
                    type = tool.input ?: DocType.PDF,
                )
            }
            composable<Routes.PdfEditor> { entry ->
                val route = entry.toRoute<Routes.PdfEditor>()
                val context = androidx.compose.ui.platform.LocalContext.current
                PdfEditorScreen(
                    key = route.key,
                    initialTool = route.tool?.let { runCatching { EditorTool.valueOf(it) }.getOrNull() },
                    onBack = navController::popBackStack,
                    onSaved = { saved ->
                        navController.popBackStack()
                        android.widget.Toast.makeText(
                            context,
                            context.getString(com.whats.web.scan.webscan.pdfreaderpdffileedit.R.string.saved_to, saved.name),
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                        val key = saved.key
                        when {
                            saved.then == AfterSave.ANNOTATE && key != null ->
                                navController.navigate(Routes.Reader(key, annotate = true))
                            saved.then == AfterSave.FILL_SIGN && key != null ->
                                navController.navigate(if (viewModel.isPro.value) Routes.PlaceOnPdf(key) else Routes.Paywall)
                            else -> viewModel.open(saved.uri, "application/pdf")
                        }
                    },
                    onAnnotate = { key ->
                        navController.popBackStack()
                        navController.navigate(Routes.Reader(key, annotate = true))
                    },
                    onFillSign = { key ->
                        navController.popBackStack()
                        navController.navigate(if (viewModel.isPro.value) Routes.PlaceOnPdf(key) else Routes.Paywall)
                    },
                )
            }
            composable<Routes.Convert> { entry ->
                val route = entry.toRoute<Routes.Convert>()
                ConvertScreen(
                    key = route.key,
                    tool = Tool.valueOf(route.tool),
                    onBack = navController::popBackStack,
                    onOpen = { uri, mime ->
                        navController.popBackStack()
                        viewModel.open(uri, mime)
                    },
                )
            }
            composable<Routes.RecycleBin> {
                RecycleBinScreen(onBack = navController::popBackStack)
            }
            composable<Routes.Search> {
                SearchScreen(
                    onBack = navController::popBackStack,
                    onOpenFile = ::openFile,
                    canShowAds = canShowAds,
                )
            }
            composable<Routes.StorageAccess> {
                StorageAccessScreen(onDone = { navController.popBackStack() })
            }
            composable<Routes.Paywall> {
                PaywallScreen(onClose = { navController.popBackStack() })
            }
            composable<Routes.Editor> { entry ->
                DocumentEditorScreen(
                    key = entry.toRoute<Routes.Editor>().key,
                    onBack = navController::popBackStack,
                    onSaved = { uri, mime ->
                        // Close the editor, and the now-stale viewer when a text file was edited, then open the result.
                        navController.popBackStack()
                        if (navController.currentBackStackEntry?.destination?.hasRoute(Routes.OfficeReader::class) == true) {
                            navController.popBackStack()
                        }
                        viewModel.open(uri, mime)
                    },
                )
            }
            composable<Routes.OrganizePages> { entry ->
                OrganizePagesScreen(
                    key = entry.toRoute<Routes.OrganizePages>().key,
                    onBack = navController::popBackStack,
                )
            }
            composable<Routes.Notices> {
                NoticesScreen(onBack = navController::popBackStack)
            }
            composable<Routes.ImageToPdf> {
                ImageToPdfScreen(onBack = navController::popBackStack, onSaved = { navController.popBackStack() })
            }
            composable<Routes.Scan> {
                CameraScreen(
                    onBack = navController::popBackStack,
                    onReview = { navController.navigate(Routes.ScanReview) },
                )
            }
            composable<Routes.ScanReview> {
                PageReviewScreen(
                    onBack = { navController.popBackStack(Routes.Home, inclusive = false) },
                    onAddPage = navController::popBackStack,
                    onSaved = { navController.popBackStack(Routes.Home, inclusive = false) },
                )
            }
            composable<Routes.AiFilePicker> { entry ->
                val mode = entry.toRoute<Routes.AiFilePicker>().mode
                AiFilePicker(
                    onBack = navController::popBackStack,
                    onPick = { file -> navController.navigate(Routes.SelectPage(file.key, mode)) },
                )
            }
            composable<Routes.SelectPage> { entry ->
                val route = entry.toRoute<Routes.SelectPage>()
                SelectPageScreen(
                    key = route.key,
                    job = route.mode.toAiJob(),
                    onBack = navController::popBackStack,
                    onContinue = { pages ->
                        navController.navigate(Routes.AiRun(route.key, route.mode, pages.joinToString(",")))
                    },
                    onPaywall = { navController.navigate(Routes.Paywall) },
                    canShowAds = canShowAds,
                )
            }
            composable<Routes.AiRun> { entry ->
                val route = entry.toRoute<Routes.AiRun>()
                AiRunScreen(
                    key = route.key,
                    job = route.mode.toAiJob(),
                    pages = route.pages.split(",").mapNotNull(String::toIntOrNull),
                    onBack = navController::popBackStack,
                    onResult = { navController.navigate(Routes.AiResult) },
                )
            }
            composable<Routes.AiResult> {
                AiResultScreen(onBack = navController::popBackStack)
            }
            composable<Routes.PlaceOnPdf> { entry ->
                PlaceOnPdfScreen(
                    key = entry.toRoute<Routes.PlaceOnPdf>().key,
                    onBack = navController::popBackStack,
                    onSaved = { navController.popBackStack(Routes.Home, inclusive = false) },
                )
            }
            composable<Routes.Reader> { entry ->
                val route = entry.toRoute<Routes.Reader>()
                PdfReaderScreen(
                    key = route.key,
                    startAnnotating = route.annotate,
                    onEdit = { key -> navController.navigate(Routes.PdfEditor(key)) },
                    onConvertToWord = { key -> navController.navigate(Routes.Convert(key, Tool.PDF_TO_WORD.name)) },
                    onBack = navController::popBackStack,
                    onAiTranslate = { key ->
                        navController.navigate(Routes.SelectPage(key, AiMode.TRANSLATE.name))
                    },
                    onAiSummary = { key ->
                        navController.navigate(Routes.SelectPage(key, AiMode.SUMMARY.name))
                    },
                    onExtractText = { key ->
                        navController.navigate(Routes.SelectPage(key, AiMode.EXTRACT_TEXT.name))
                    },
                    onSign = { key -> navController.navigate(Routes.PlaceOnPdf(key)) },
                    onPaywall = { navController.navigate(Routes.Paywall) },
                )
            }
            composable<Routes.OfficeReader> { entry ->
                OfficeReaderScreen(
                    key = entry.toRoute<Routes.OfficeReader>().key,
                    onBack = navController::popBackStack,
                    onEdit = { key -> navController.navigate(Routes.Editor(key)) },
                    onConvertToPdf = { file -> runTool(Tool.WORD_TO_PDF, file) },
                )
            }
        }
    }
}
