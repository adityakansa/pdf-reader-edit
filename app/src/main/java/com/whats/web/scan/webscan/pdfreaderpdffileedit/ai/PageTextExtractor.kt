package com.whats.web.scan.webscan.pdfreaderpdffileedit.ai

import android.net.Uri
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ocr.TextRecogniser
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.PdfAccess
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.text.PdfTextIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FR-063. The text of the pages the user picked: PdfBox first, and when a page has almost none — a
 * scan — the page is rendered and read with on-device OCR instead. Nothing leaves the phone.
 */
@Singleton
class PageTextExtractor @Inject constructor(
    private val access: PdfAccess,
    private val recogniser: TextRecogniser,
) {
    suspend fun extract(uri: Uri, pages: List<Int>): Map<Int, String> = withContext(Dispatchers.IO) {
        val index = runCatching {
            access.openStream(uri).use { PdfTextIndex.of(it, access.scratchDir) }
        }.getOrNull()

        val needsOcr = mutableListOf<Int>()
        val result = pages.associateWith { page ->
            val text = index?.textOf(page).orEmpty()
            if (text.count { !it.isWhitespace() } >= MIN_CHARS) text else {
                needsOcr += page
                ""
            }
        }.toMutableMap()

        if (needsOcr.isNotEmpty()) {
            val opened = access.open(uri)
            if (opened is PdfAccess.OpenResult.Success) {
                opened.session.use { session ->
                    needsOcr.forEach { page ->
                        val recognised = runCatching {
                            val bitmap = session.renderPage(page, OCR_WIDTH_PIXELS)
                            recogniser.read(bitmap).also { bitmap.recycle() }
                        }.getOrDefault("")
                        result[page] = recognised
                    }
                }
            }
        }
        result
    }

    private companion object {
        /** Below this a page is a picture of a page, not a page with a text layer. */
        const val MIN_CHARS = 20

        /** Wide enough that 9 pt body text is legible to the recogniser. */
        const val OCR_WIDTH_PIXELS = 1_600
    }
}
