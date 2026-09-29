package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.ai

import androidx.compose.runtime.Composable
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.DocFile
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.search.SearchScreen

/**
 * FR-061. Picking the PDF an AI job runs on. It is the search screen with the list restricted to PDFs —
 * the library list and its search already do exactly this, so there is no second list to maintain.
 */
@Composable
fun AiFilePicker(onBack: () -> Unit, onPick: (DocFile) -> Unit) {
    SearchScreen(
        onBack = onBack,
        onOpenFile = onPick,
        canShowAds = false,
        pdfOnly = true,
    )
}
