package com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.text

import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.WordBlock
import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.WordRun
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Step 12c (PDF to Word). Turns a page's [TextLine]s back into what a word processor thinks in: paragraphs
 * (lines that wrap into each other are joined), headings (clearly larger type), centred titles, and tables
 * (two or more rows that each have several cells side by side). Pure, so it is tested on the JVM.
 */
object WordLayout {

    fun blocks(lines: List<TextLine>, pageWidth: Float, pageHeight: Float): List<WordBlock> {
        if (lines.isEmpty()) return emptyList()
        val body = bodySize(lines)
        val rows = rows(lines)
        val out = mutableListOf<WordBlock>()
        var table = mutableListOf<List<String>>()
        var paragraph: Builder? = null
        var previousBottom: Float? = null

        fun flushParagraph() {
            paragraph?.let { out += it.build() }
            paragraph = null
        }
        fun flushTable() {
            when {
                table.size >= 2 -> out += WordBlock.Table(table.map { row -> row + List(table.maxOf { it.size } - row.size) { "" } })
                table.size == 1 -> out += WordBlock.Paragraph(listOf(WordRun(table.single().joinToString("\t"))))
            }
            table = mutableListOf()
        }

        rows.forEach { row ->
            val first = row.first()
            // Word boxes are shorter than the type, so spacing is judged baseline to baseline (a box's bottom
            // is its baseline) against the font size.
            val typeSize = if (first.fontSize > 0f) first.fontSize else max(1f, (first.bottom - first.top) * pageHeight / GLYPH_HEIGHT)
            val leading = previousBottom?.let { (first.bottom - it) * pageHeight } ?: 0f
            previousBottom = row.maxOf { it.bottom }
            if (row.size >= 2) {
                flushParagraph()
                table += row.map { it.text }
                return@forEach
            }
            flushTable()
            val line = first
            val size = if (line.fontSize > 0f) line.fontSize else body
            val heading = when {
                line.text.length > HEADING_MAX_CHARS -> 0
                size >= body * H1_RATIO -> 1
                size >= body * H2_RATIO -> 2
                else -> 0
            }
            val centered = abs((line.left + line.right) / 2f - 0.5f) < CENTER_SLOP && line.left > SIDE_MARGIN && line.right < 1f - SIDE_MARGIN
            val current = paragraph
            val joins = current != null && heading == 0 && current.heading == 0 &&
                abs(current.size - size) <= SIZE_SLOP && current.bold == line.bold &&
                leading <= typeSize * WRAP_LEADING &&
                (abs(current.left - line.left) <= LEFT_SLOP || current.lastRight >= 1f - WIDE_LINE) &&
                current.centered == centered
            if (joins) {
                current!!.append(line)
            } else {
                flushParagraph()
                val spaceBefore = if (leading > typeSize * BIG_LEADING) min(leading - typeSize * NORMAL_LEADING, MAX_SPACE) else 0f
                paragraph = Builder(line, size, heading, centered, spaceBefore, sizeToWrite(size, body, heading))
            }
        }
        flushParagraph()
        flushTable()
        return out
    }

    /** Lines that share a baseline band, left to right; one row per visual line of the page. */
    internal fun rows(lines: List<TextLine>): List<List<TextLine>> {
        val sorted = lines.sortedWith(compareBy<TextLine>({ it.top }, { it.left }))
        val rows = mutableListOf<MutableList<TextLine>>()
        sorted.forEach { line ->
            val row = rows.lastOrNull()
            val anchor = row?.first()
            if (anchor != null && overlap(anchor, line) >= SAME_ROW) row += line else rows += mutableListOf(line)
        }
        return rows.map { row -> row.sortedBy { it.left } }
    }

    private fun overlap(a: TextLine, b: TextLine): Float {
        val shared = min(a.bottom, b.bottom) - max(a.top, b.top)
        val smaller = min(a.bottom - a.top, b.bottom - b.top)
        return if (smaller <= 0f) 0f else shared / smaller
    }

    /** The size most of the text is set in, weighted by length. */
    private fun bodySize(lines: List<TextLine>): Float {
        val sized = lines.filter { it.fontSize > 0f }
        if (sized.isEmpty()) return DEFAULT_SIZE
        return sized.groupBy { (it.fontSize * 2).toInt() / 2f }
            .maxByOrNull { (_, group) -> group.sumOf { it.text.length } }!!.key
    }

    /** Headings take their size from the style; body text keeps a size only when it differs from the default. */
    private fun sizeToWrite(size: Float, body: Float, heading: Int): Float =
        if (heading != 0 || abs(size - DEFAULT_SIZE) < SIZE_SLOP) 0f else (size * 2).toInt() / 2f

    private class Builder(
        first: TextLine,
        val size: Float,
        val heading: Int,
        val centered: Boolean,
        val spaceBefore: Float,
        val sizeToWrite: Float,
    ) {
        val left = first.left
        val bold = first.bold
        var lastRight = first.right
        private val runs = mutableListOf(WordRun(first.text, first.bold, first.italic, sizeToWrite))

        fun append(line: TextLine) {
            val last = runs.last()
            // "infor-" + "mation" joins without the hyphen; any other wrap is a space.
            val text = if (last.text.endsWith("-") && line.text.firstOrNull()?.isLowerCase() == true) {
                runs[runs.lastIndex] = last.copy(text = last.text.dropLast(1))
                line.text
            } else {
                " " + line.text
            }
            if (line.italic == last.italic && line.bold == last.bold) {
                runs[runs.lastIndex] = runs.last().copy(text = runs.last().text + text)
            } else {
                runs += WordRun(text, line.bold, line.italic, sizeToWrite)
            }
            lastRight = line.right
        }

        fun build() = WordBlock.Paragraph(runs.toList(), heading, centered, spaceBefore)
    }

    private const val DEFAULT_SIZE = 11f
    private const val H1_RATIO = 1.6f
    private const val H2_RATIO = 1.25f
    private const val HEADING_MAX_CHARS = 120
    private const val SIZE_SLOP = 0.6f
    private const val WRAP_LEADING = 1.5f
    private const val BIG_LEADING = 1.9f
    private const val NORMAL_LEADING = 1.2f
    private const val GLYPH_HEIGHT = 0.7f
    private const val MAX_SPACE = 24f
    private const val LEFT_SLOP = 0.03f
    private const val WIDE_LINE = 0.2f
    private const val CENTER_SLOP = 0.03f
    private const val SIDE_MARGIN = 0.15f
    private const val SAME_ROW = 0.5f
}
