package com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.text

import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.Closeable
import java.io.File

/**
 * The PDF editor's view of the text on each page: one document held open while the editor is, so tapping
 * page after page does not re-read the whole file. Lines are cached per page.
 */
class PageText(file: File, password: String?, scratch: File) : Closeable {
    private val document: PDDocument = run {
        scratch.mkdirs()
        val memory = MemoryUsageSetting.setupMixed(MAIN_MEMORY_BYTES).setTempDir(scratch)
        if (password == null) PDDocument.load(file, memory) else PDDocument.load(file, password, memory)
    }
    private val cache = HashMap<Int, List<TextLine>>()

    @Synchronized
    fun lines(pageIndex: Int): List<TextLine> = cache.getOrPut(pageIndex) {
        if (pageIndex !in 0 until document.numberOfPages) return@getOrPut emptyList()
        val box = document.getPage(pageIndex).mediaBox
        runCatching { TextLines.group(PdfWords.onPage(document, pageIndex), box.width, box.height) }
            .getOrDefault(emptyList())
    }

    @Synchronized
    override fun close() = document.close()

    private companion object {
        const val MAIN_MEMORY_BYTES = 8L * 1024 * 1024
    }
}
