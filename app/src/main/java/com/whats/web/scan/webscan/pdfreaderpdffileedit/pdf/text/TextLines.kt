package com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.text

import kotlin.math.max
import kotlin.math.min

/** The three families the editor can write with PDF's built-in fonts. */
enum class EditFont { SANS, SERIF, MONO }

/**
 * A run of words on one line that belong together — a sentence, or one cell of a table — and what the PDF
 * editor offers to retype as one box. Positions are fractions of the page like [PdfWord]'s.
 */
data class TextLine(
    val text: String,
    val pageIndex: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    /** Points; 0 when the PDF did not say. */
    val fontSize: Float,
    val fontName: String,
) {
    val bold: Boolean
        get() = BOLD_HINTS.any { fontName.contains(it, ignoreCase = true) }

    val italic: Boolean
        get() = fontName.contains("Italic", ignoreCase = true) || fontName.contains("Oblique", ignoreCase = true)

    /** The closest built-in family to the font the PDF used. */
    val family: EditFont
        get() = when {
            MONO_HINTS.any { fontName.contains(it, ignoreCase = true) } -> EditFont.MONO
            SERIF_HINTS.any { fontName.contains(it, ignoreCase = true) } -> EditFont.SERIF
            else -> EditFont.SANS
        }

    fun contains(x: Float, y: Float, slop: Float = 0f): Boolean =
        x >= left - slop && x <= right + slop && y >= top - slop && y <= bottom + slop

    private companion object {
        val BOLD_HINTS = listOf("Bold", "Black", "Heavy", "Semibold", "Demi")
        val MONO_HINTS = listOf("Courier", "Mono", "Consolas", "Menlo")
        val SERIF_HINTS = listOf("Times", "Serif", "Georgia", "Cambria", "Garamond", "Book Antiqua", "Palatino")
    }
}

/**
 * Groups a page's words into [TextLine]s: words join when they sit on the same line and the gap between
 * them is no wider than about one letter height. A wider gap starts a new box, so the columns of a table
 * ("Bus Fare" … "374.00") stay separate, which is what the user expects to tap and retype.
 */
object TextLines {

    /** [pageWidth] and [pageHeight] are in points; they turn fractions into distances that can be compared. */
    fun group(words: List<PdfWord>, pageWidth: Float, pageHeight: Float): List<TextLine> {
        if (words.isEmpty() || pageWidth <= 0f || pageHeight <= 0f) return emptyList()
        val lines = mutableListOf<MutableList<PdfWord>>()
        for (word in words) {
            val line = lines.lastOrNull()
            if (line != null && belongs(line.last(), word, pageWidth, pageHeight)) line += word else lines += mutableListOf(word)
        }
        return lines.map { merge(it) }
    }

    private fun belongs(previous: PdfWord, next: PdfWord, pageWidth: Float, pageHeight: Float): Boolean {
        if (previous.pageIndex != next.pageIndex) return false
        val prevHeight = (previous.bottom - previous.top) * pageHeight
        val nextHeight = (next.bottom - next.top) * pageHeight
        val height = max(1f, min(prevHeight, nextHeight))
        val overlap = (min(previous.bottom, next.bottom) - max(previous.top, next.top)) * pageHeight
        if (overlap < height * SAME_LINE_OVERLAP) return false
        val gap = (next.left - previous.right) * pageWidth
        return gap >= -height * BACKWARD_SLOP && gap <= max(prevHeight, nextHeight) * WORD_GAP
    }

    private fun merge(words: List<PdfWord>): TextLine {
        val sizes = words.map { it.fontSize }.filter { it > 0f }.sorted()
        return TextLine(
            text = words.joinToString(" ") { it.text },
            pageIndex = words.first().pageIndex,
            left = words.minOf { it.left },
            top = words.minOf { it.top },
            right = words.maxOf { it.right },
            bottom = words.maxOf { it.bottom },
            fontSize = sizes.getOrElse(sizes.size / 2) { 0f },
            fontName = words.groupingBy { it.fontName }.eachCount().maxByOrNull { it.value }?.key.orEmpty(),
        )
    }

    /** The line under a tap at ([x], [y]), with a little slack for fingers; null over blank paper. */
    fun at(lines: List<TextLine>, x: Float, y: Float, slop: Float = 0.01f): TextLine? =
        lines.filter { it.contains(x, y, slop) }
            .minByOrNull { (it.right - it.left) * (it.bottom - it.top) }

    private const val SAME_LINE_OVERLAP = 0.5f
    private const val WORD_GAP = 0.9f
    private const val BACKWARD_SLOP = 0.3f
}
