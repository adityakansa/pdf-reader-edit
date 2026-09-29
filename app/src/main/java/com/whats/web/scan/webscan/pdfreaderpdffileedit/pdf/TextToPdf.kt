package com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.text.StaticLayout
import android.text.TextPaint
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.OutputFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FR-066. AI output saved as a PDF.
 *
 * This uses the platform `PdfDocument` with a `StaticLayout` rather than PdfBox: PdfBox's standard
 * fonts are WinAnsi only, so a Bengali, Devanagari or CJK translation would come out as boxes. The
 * platform draws with the system fonts and shapes any script the phone can render.
 */
@Singleton
class TextToPdf @Inject constructor(private val outputFolder: OutputFolder) {

    suspend fun save(title: String, text: CharSequence): OutputFolder.Output = withContext(Dispatchers.IO) {
        val document = PdfDocument()
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = BODY_TEXT_SIZE
            color = android.graphics.Color.BLACK
        }
        val usableWidth = (PAGE_WIDTH - 2 * MARGIN).toInt()
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, usableWidth)
            .setLineSpacing(LINE_EXTRA, 1f)
            .build()

        var drawnHeight = 0
        var pageNumber = 1
        val usableHeight = (PAGE_HEIGHT - 2 * MARGIN).toInt()
        while (drawnHeight < layout.height) {
            val page = document.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create(),
            )
            val canvas = page.canvas
            canvas.save()
            canvas.translate(MARGIN, MARGIN - drawnHeight)
            // Clip to the page, then slide the whole layout up by what earlier pages already showed.
            canvas.clipRect(0f, drawnHeight.toFloat(), usableWidth.toFloat(), (drawnHeight + usableHeight).toFloat())
            layout.draw(canvas)
            canvas.restore()
            document.finishPage(page)
            drawnHeight += usableHeight
            pageNumber++
        }

        outputFolder.write(title) { out ->
            document.writeTo(out)
            document.close()
        }
    }

    private companion object {
        /** A4 at 72 dpi, which is what `PdfDocument` pages are measured in. */
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 48f
        const val BODY_TEXT_SIZE = 11f
        const val LINE_EXTRA = 4f
    }
}
