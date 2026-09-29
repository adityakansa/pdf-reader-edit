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
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.home.HomeScreen
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
                    onImageToPdf = { navController.navigate(Routes.ImageToPdf) },
                    onScan = { navController.navigate(Routes.Scan) },
                    onAi = { mode -> navController.navigate(Routes.AiFilePicker(mode.name)) },
                    onStorageAccess = { navController.navigate(Routes.StorageAccess) },
                    onNotices = { navController.navigate(Routes.Notices) },
                    canShowAds = canShowAds,
                )
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
                PdfReaderScreen(
                    key = entry.toRoute<Routes.Reader>().key,
                    onBack = navController::popBackStack,
                    onAiTranslate = { key ->
                        navController.navigate(Routes.SelectPage(key, AiMode.TRANSLATE.name))
                    },
                    onAiSummary = { key ->
                        navController.navigate(Routes.SelectPage(key, AiMode.SUMMARY.name))
                    },
                    onSign = { key -> navController.navigate(Routes.PlaceOnPdf(key)) },
                    onPaywall = { navController.navigate(Routes.Paywall) },
                )
            }
            composable<Routes.OfficeReader> { entry ->
                OfficeReaderScreen(
                    key = entry.toRoute<Routes.OfficeReader>().key,
                    onBack = navController::popBackStack,
                )
            }
        }
    }
}
