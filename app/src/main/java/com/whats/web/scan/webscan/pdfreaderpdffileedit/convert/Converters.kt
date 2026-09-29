package com.whats.web.scan.webscan.pdfreaderpdffileedit.convert

import android.content.Context
import android.graphics.Bitmap
import android.print.PdfPrintBridge
import android.print.PrintAttributes
import android.webkit.WebView
import android.webkit.WebViewClient
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.DocFile
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.OutputFolder
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ocr.TextRecogniser
import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.DocxBuilder
import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.WordBlock
import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.WordRun
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.PdfAccess
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.PdfRenderSession
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.text.PageText
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.text.WordLayout
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** The PDF needs a password; the tools ask the user to remove it first (⋮ → Remove password). */
class PasswordProtectedException : Exception()

/** Opens a PDF for rendering, or explains why it cannot. */
private suspend fun PdfAccess.session(file: DocFile): PdfRenderSession =
    when (val result = open(file.uri)) {
        is PdfAccess.OpenResult.Success -> result.session
        PdfAccess.OpenResult.PasswordRequired, PdfAccess.OpenResult.WrongPassword -> throw PasswordProtectedException()
        is PdfAccess.OpenResult.Failed -> throw result.cause
    }

/**
 * Step 12c, PDF to Word. Pages with text become headings, paragraphs and tables ([WordLayout]); scanned
 * pages become the page picture followed by the text ML Kit reads off it, so the Word file is editable
 * either way.
 */
@Singleton
class PdfToWord @Inject constructor(
    private val access: PdfAccess,
    private val ocr: TextRecogniser,
) {
    suspend fun convert(file: DocFile, out: OutputStream, progress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val session = access.session(file)
        try {
            val sizes = session.pageSizes()
            val local = access.localCopy(file.uri)
            val blocks = mutableListOf<WordBlock>()
            PageText(local, null, access.scratchDir).use { text ->
                sizes.forEachIndexed { index, size ->
                    ensureActive()
                    if (index > 0) blocks += WordBlock.PageBreak
                    val lines = text.lines(index)
                    if (lines.isNotEmpty()) {
                        blocks += WordLayout.blocks(lines, size.widthPoints.toFloat(), size.heightPoints.toFloat())
                    } else {
                        blocks += scannedPage(session, index, size.widthPoints.toFloat(), size.heightPoints.toFloat())
                    }
                    progress((index + 1f) / sizes.size)
                }
            }
            val first = sizes.firstOrNull()
            DocxBuilder.write(
                blocks,
                file.name.substringBeforeLast('.'),
                first?.widthPoints?.toFloat() ?: A4_WIDTH,
                first?.heightPoints?.toFloat() ?: A4_HEIGHT,
                out,
            )
        } finally {
            session.close()
        }
    }

    private suspend fun scannedPage(session: PdfRenderSession, index: Int, widthPt: Float, heightPt: Float): List<WordBlock> {
        val bitmap = session.renderPage(index, SCAN_WIDTH_PX)
        val jpeg = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }.toByteArray()
        val words = runCatching { ocr.read(bitmap) }.getOrDefault("")
        bitmap.recycle()
        val paragraphs = words.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
            .map { WordBlock.Paragraph(listOf(WordRun(it))) }
        return listOf(WordBlock.Picture(jpeg, png = false, widthPt = widthPt, heightPt = heightPt)) + paragraphs
    }

    private companion object {
        const val SCAN_WIDTH_PX = 1400
        const val JPEG_QUALITY = 80
        const val A4_WIDTH = 595f
        const val A4_HEIGHT = 842f
    }
}

/** Step 12c, PDF to Image: every page as a JPEG in Pictures/PDF Reader, sharp enough to print. */
@Singleton
class PdfToImages @Inject constructor(
    private val access: PdfAccess,
    private val output: OutputFolder,
) {
    suspend fun convert(file: DocFile, progress: (Float) -> Unit): List<OutputFolder.Output> = withContext(Dispatchers.IO) {
        val session = access.session(file)
        try {
            val sizes = session.pageSizes()
            val base = file.name.substringBeforeLast('.')
            sizes.mapIndexed { index, size ->
                ensureActive()
                // About 150 dpi, capped so a poster-sized page does not run the phone out of memory.
                val width = (size.widthPoints * DPI_SCALE).toInt().coerceIn(MIN_PX, MAX_PX)
                val bitmap = session.renderPage(index, width)
                val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }.toByteArray()
                bitmap.recycle()
                val saved = output.writeImage("${base}_page_${index + 1}", bytes)
                progress((index + 1f) / sizes.size)
                saved
            }
        } finally {
            session.close()
        }
    }

    private companion object {
        const val DPI_SCALE = 150f / 72f
        const val MIN_PX = 600
        const val MAX_PX = 2400
        const val JPEG_QUALITY = 90
    }
}

/**
 * Step 12c, Word / Excel / PowerPoint / text to PDF: the viewer's HTML, restyled for paper
 * ([com.whats.web.scan.webscan.pdfreaderpdffileedit.office.PrintCss]), printed by an off-screen WebView
 * straight into a file — Chromium's own paginator, so text stays text and tables break between rows.
 */
@Singleton
class OfficeToPdf @Inject constructor(@ApplicationContext private val context: Context) {

    /** True when [target] now holds the PDF. False (never an exception) when this phone cannot print that way. */
    suspend fun print(html: String, baseUrl: String?, landscape: Boolean, name: String, target: File): Boolean =
        withContext(Dispatchers.Main) {
            val webView = WebView(context)
            try {
                webView.settings.javaScriptEnabled = false
                webView.settings.allowFileAccess = true
                webView.layout(0, 0, LAYOUT_WIDTH, LAYOUT_HEIGHT)
                val loaded = withTimeoutOrNull(LOAD_TIMEOUT_MS) {
                    suspendCancellableCoroutine { continuation ->
                        webView.webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                if (continuation.isActive) continuation.resume(Unit)
                            }
                        }
                        webView.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
                    }
                }
                if (loaded == null) return@withContext false
                // Pictures decode just after the page reports finished.
                delay(SETTLE_MS)
                val attributes = PrintAttributes.Builder()
                    .setMediaSize(if (landscape) PrintAttributes.MediaSize.ISO_A4.asLandscape() else PrintAttributes.MediaSize.ISO_A4)
                    .setResolution(PrintAttributes.Resolution("pdf", "pdf", DPI, DPI))
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .build()
                val adapter = webView.createPrintDocumentAdapter(name)
                withTimeoutOrNull(PRINT_TIMEOUT_MS) {
                    suspendCancellableCoroutine { continuation ->
                        PdfPrintBridge.write(adapter, attributes, target) { ok ->
                            if (continuation.isActive) continuation.resume(ok)
                        }
                    }
                } == true && target.length() > 0
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // IllegalAccessError and friends if a future Android closes the print bridge.
                false
            } finally {
                webView.destroy()
            }
        }

    private companion object {
        const val LAYOUT_WIDTH = 1080
        const val LAYOUT_HEIGHT = 1920
        const val LOAD_TIMEOUT_MS = 30_000L
        const val PRINT_TIMEOUT_MS = 120_000L
        const val SETTLE_MS = 400L
        const val DPI = 600
    }
}
