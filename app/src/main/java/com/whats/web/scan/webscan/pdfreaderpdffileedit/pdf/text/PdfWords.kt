package com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.text

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File

/**
 * FR-032: one word on a page, and where it sits, as fractions of the page (0..1 from the top left).
 *
 * Fractions rather than points, so a highlight drawn over a page rendered at one size still lands on the
 * same words when the page is rendered at another — the same rule the signature placements follow.
 */
data class PdfWord(
    val text: String,
    val pageIndex: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    /** True when this word falls inside the box the finger dragged over. */
    fun intersects(otherLeft: Float, otherTop: Float, otherRight: Float, otherBottom: Float): Boolean =
        left < otherRight && right > otherLeft && top < otherBottom && bottom > otherTop
}

/**
 * FR-032: reads the words off a page (ported from pdfscanner `core/pdf/text/PdfWords.kt`).
 *
 * reads the words off a page with their positions.
 *
 * the text index's text index gives the words on a page but not where they are,  `PDFTextStripper` reports a [TextPosition] per glyph; they are grouped back
 * into words here, because a highlight over "invoice" should cover the word, not seven glyph boxes.
 */
object PdfWords {

    /**
     * Every word on [pageIndex] of [file], opening and closing the document here.
     *
     * The overload that takes a [PDDocument] is for callers already inside this module; everyone else
     * uses this one, so PdfBox types stay in `pdf/`.
     */
    fun onPage(file: File, pageIndex: Int, password: String? = null): List<PdfWord> {
        val document = if (password == null) PDDocument.load(file) else PDDocument.load(file, password)
        return document.use { onPage(it, pageIndex) }
    }

    /** Every word on [pageIndex] (zero-based). Empty for a page with no text layer, e.g. a pure scan. */
    fun onPage(document: PDDocument, pageIndex: Int): List<PdfWord> {
        if (pageIndex !in 0 until document.numberOfPages) return emptyList()
        val page = document.getPage(pageIndex)
        val width = page.mediaBox.width
        val height = page.mediaBox.height
        if (width <= 0f || height <= 0f) return emptyList()

        val words = mutableListOf<PdfWord>()
        val stripper = object : PDFTextStripper() {
            override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
                words += wordsIn(text, textPositions, pageIndex, width, height)
                super.writeString(text, textPositions)
            }
        }
        stripper.startPage = pageIndex + 1
        stripper.endPage = pageIndex + 1
        stripper.getText(document)
        return words
    }

    /**
     * Groups the glyphs of one line back into words.
     *
     * The stripper hands over a line at a time with a position per glyph; a space in the text is what
     * separates words, so the glyphs are walked alongside the string.
     */
    private fun wordsIn(
        line: String,
        positions: List<TextPosition>,
        pageIndex: Int,
        pageWidth: Float,
        pageHeight: Float,
    ): List<PdfWord> {
        val words = mutableListOf<PdfWord>()
        var current = StringBuilder()
        var glyphs = mutableListOf<TextPosition>()

        fun flush() {
            val text = current.toString().trim()
            if (text.isNotEmpty() && glyphs.isNotEmpty()) {
                words += box(text, glyphs, pageIndex, pageWidth, pageHeight)
            }
            current = StringBuilder()
            glyphs = mutableListOf()
        }

        line.forEachIndexed { index, character ->
            val position = positions.getOrNull(index)
            if (character.isWhitespace()) {
                flush()
            } else {
                current.append(character)
                position?.let { glyphs += it }
            }
        }
        flush()
        return words
    }

    private fun box(
        text: String,
        glyphs: List<TextPosition>,
        pageIndex: Int,
        pageWidth: Float,
        pageHeight: Float,
    ): PdfWord {
        // yDirAdj already counts down from the top of the page, which is the direction fractions use.
        val left = glyphs.minOf { it.xDirAdj }
        val right = glyphs.maxOf { it.xDirAdj + it.widthDirAdj }
        val bottom = glyphs.maxOf { it.yDirAdj }
        val top = glyphs.minOf { it.yDirAdj - it.heightDir }
        return PdfWord(
            text = text,
            pageIndex = pageIndex,
            left = (left / pageWidth).coerceIn(0f, 1f),
            top = (top / pageHeight).coerceIn(0f, 1f),
            right = (right / pageWidth).coerceIn(0f, 1f),
            bottom = (bottom / pageHeight).coerceIn(0f, 1f),
        )
    }
}
