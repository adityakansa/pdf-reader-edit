package com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.text

import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.InputStream

/** One search hit: which page, and where in that page's text. */
data class TextMatch(val pageIndex: Int, val start: Int, val end: Int, val snippet: String)

/**
 * FR-031. The text of an open PDF, page by page, extracted once with PdfBox and searched in memory.
 * Extraction is the slow part, so it runs in the background and the index lives as long as the viewer.
 * Ported from pdfscanner `core/pdf/text/PdfTextIndex.kt`.
 */
class PdfTextIndex(private val pages: List<String>) {

    val pageCount: Int get() = pages.size

    fun textOf(pageIndex: Int): String = pages.getOrElse(pageIndex) { "" }

    val hasAnyText: Boolean get() = pages.any { it.isNotBlank() }

    /** Every occurrence of [query], case-insensitive, in page order. Blank queries match nothing. */
    fun search(query: String, snippetRadius: Int = SNIPPET_RADIUS): List<TextMatch> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        val matches = mutableListOf<TextMatch>()
        pages.forEachIndexed { pageIndex, text ->
            var from = text.indexOf(needle, ignoreCase = true)
            while (from >= 0) {
                val end = from + needle.length
                val snippetStart = (from - snippetRadius).coerceAtLeast(0)
                val snippetEnd = (end + snippetRadius).coerceAtMost(text.length)
                val snippet = text.substring(snippetStart, snippetEnd).replace(WHITESPACE, " ").trim()
                matches += TextMatch(pageIndex, from, end, snippet)
                from = text.indexOf(needle, startIndex = end, ignoreCase = true)
            }
        }
        return matches
    }

    companion object {
        private const val SNIPPET_RADIUS = 40
        private val WHITESPACE = Regex("\\s+")
        private const val MAIN_MEMORY_BYTES = 16L * 1024 * 1024

        fun of(file: File, scratchDirectory: File, password: String? = null): PdfTextIndex =
            file.inputStream().use { of(it, scratchDirectory, password) }

        /** Heavy: call from a background dispatcher. */
        fun of(input: InputStream, scratchDirectory: File, password: String? = null): PdfTextIndex {
            scratchDirectory.mkdirs()
            val memory = MemoryUsageSetting.setupMixed(MAIN_MEMORY_BYTES).setTempDir(scratchDirectory)
            val document = if (password == null) {
                PDDocument.load(input, memory)
            } else {
                PDDocument.load(input, password, memory)
            }
            return document.use { pdf ->
                val stripper = PDFTextStripper()
                PdfTextIndex(
                    (1..pdf.numberOfPages).map { page ->
                        stripper.startPage = page
                        stripper.endPage = page
                        stripper.getText(pdf)
                    },
                )
            }
        }
    }
}
