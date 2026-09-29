package com.whats.web.scan.webscan.pdfreaderpdffileedit.office.legacy

import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.legacy.Cfb.Companion.le16
import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.legacy.Cfb.Companion.le32
import java.io.IOException
import java.nio.charset.Charset

/**
 * Word 97–2003 (.doc) text: the main document's characters read through the piece table, as
 * paragraphs, page breaks and tables. Formatting is not read (it lives in separate property tables);
 * the viewer shows clean text on a page, which is what "open an old .doc" needs.
 */
object LegacyDoc {

    sealed interface Block {
        data class Paragraph(val text: String) : Block
        data class Table(val rows: List<List<String>>) : Block
        data object PageBreak : Block
    }

    fun read(cfb: Cfb): List<Block> = blocks(text(cfb))

    /** The main-document text with Word's control characters still in it. */
    fun text(cfb: Cfb): String {
        val word = cfb.stream("WordDocument") ?: throw IOException("No WordDocument stream")
        if (le16(word, 0) != WORD_IDENT) throw IOException("Not a Word 97+ document")
        val flags = le16(word, 0x0A)
        if (flags and ENCRYPTED != 0) throw IOException("Encrypted document")
        val table = cfb.stream(if (flags and WHICH_TABLE != 0) "1Table" else "0Table")
            ?: throw IOException("No table stream")

        var pos = FIB_BASE
        val csw = le16(word, pos)
        pos += 2 + csw * 2
        val cslw = le16(word, pos)
        val lwStart = pos + 2
        val ccpText = le32(word, lwStart + 3 * 4)
        pos = lwStart + cslw * 4
        val fcStart = pos + 2
        val fcClx = le32(word, fcStart + CLX_INDEX * 8)
        val lcbClx = le32(word, fcStart + CLX_INDEX * 8 + 4)
        if (fcClx < 0 || lcbClx <= 0 || fcClx + lcbClx > table.size) throw IOException("No piece table")

        // Skip Prc entries (clxt = 1) to reach the Pcdt (clxt = 2).
        var p = fcClx
        val end = fcClx + lcbClx
        while (p < end && table[p].toInt() == 1) p += 3 + le16(table, p + 1)
        if (p >= end || table[p].toInt() != 2) throw IOException("No piece descriptor table")
        val lcb = le32(table, p + 1)
        val plc = p + 5
        val pieces = (lcb - 4) / 12
        val out = StringBuilder()
        val cp1252 = Charset.forName("windows-1252")
        for (i in 0 until pieces) {
            val cpStart = le32(table, plc + i * 4)
            val cpEnd = le32(table, plc + (i + 1) * 4)
            if (cpStart >= ccpText) break
            val count = (minOf(cpEnd, ccpText) - cpStart).coerceAtLeast(0)
            val fcRaw = le32(table, plc + (pieces + 1) * 4 + i * 8 + 2)
            val compressed = fcRaw and COMPRESSED != 0
            if (compressed) {
                val offset = (fcRaw and COMPRESSED.inv()) / 2
                if (offset < 0 || offset + count > word.size) continue
                out.append(String(word, offset, count, cp1252))
            } else {
                if (fcRaw < 0 || fcRaw + count * 2 > word.size) continue
                out.append(String(word, fcRaw, count * 2, Charsets.UTF_16LE))
            }
        }
        return out.toString()
    }

    /** Splits Word's text into paragraphs, tables and page breaks, dropping field codes and objects. */
    fun blocks(raw: String): List<Block> {
        val text = cleanFields(raw)
        val blocks = mutableListOf<Block>()
        var row = mutableListOf<String>()
        var rows = mutableListOf<List<String>>()
        var columns = 0
        var cell = StringBuilder()
        val paragraph = StringBuilder()
        var lastWasCellMark = false

        fun flushTable() {
            if (row.isNotEmpty()) { rows.add(row); row = mutableListOf() }
            if (rows.isNotEmpty()) { blocks.add(Block.Table(rows)); rows = mutableListOf() }
            columns = 0
        }

        for (c in text) {
            when (c) {
                CELL_MARK -> {
                    // A cell mark straight after another one ends the row (Word writes one extra per row),
                    // unless the row is still short of the column count the first row established.
                    if (lastWasCellMark && cell.isEmpty() && paragraph.isEmpty() && (columns == 0 || row.size >= columns)) {
                        if (columns == 0) columns = row.size
                        rows.add(row)
                        row = mutableListOf()
                    } else {
                        cell.append(paragraph)
                        row.add(cell.toString().trim())
                        cell = StringBuilder()
                        paragraph.setLength(0)
                    }
                    lastWasCellMark = true
                    continue
                }
                '\r' -> {
                    if (row.isNotEmpty() || cell.isNotEmpty()) {
                        cell.append(paragraph).append('\n')
                    } else {
                        if (rows.isNotEmpty()) flushTable()
                        blocks.add(Block.Paragraph(paragraph.toString()))
                    }
                    paragraph.setLength(0)
                }
                PAGE_BREAK -> {
                    if (paragraph.isNotEmpty()) { blocks.add(Block.Paragraph(paragraph.toString())); paragraph.setLength(0) }
                    flushTable()
                    blocks.add(Block.PageBreak)
                }
                LINE_BREAK -> paragraph.append('\n')
                NB_HYPHEN -> paragraph.append('-')
                SOFT_HYPHEN, OBJECT, DRAWN, FOOTNOTE -> Unit
                '\t' -> paragraph.append('\t')
                else -> if (c >= ' ' || c == '\n') paragraph.append(c)
            }
            lastWasCellMark = false
        }
        if (paragraph.isNotEmpty()) blocks.add(Block.Paragraph(paragraph.toString()))
        flushTable()
        return blocks
    }

    /** Keeps what a field displays (between 0x14 and 0x15) and drops its code (0x13 … 0x14). */
    internal fun cleanFields(text: String): String {
        val out = StringBuilder(text.length)
        var depth = 0
        val inCode = ArrayDeque<Boolean>()
        for (c in text) {
            when (c) {
                FIELD_BEGIN -> { depth++; inCode.addLast(true) }
                FIELD_SEPARATOR -> if (inCode.isNotEmpty()) { inCode.removeLast(); inCode.addLast(false) }
                FIELD_END -> if (inCode.isNotEmpty()) { inCode.removeLast(); depth-- }
                else -> if (inCode.none { it }) out.append(c)
            }
        }
        return out.toString()
    }

    private const val WORD_IDENT = 0xA5EC
    private const val ENCRYPTED = 0x0100
    private const val WHICH_TABLE = 0x0200
    private const val FIB_BASE = 32
    private const val CLX_INDEX = 33
    private const val COMPRESSED = 0x40000000
    private const val CELL_MARK = '\u0007'
    private const val PAGE_BREAK = '\u000C'
    private const val LINE_BREAK = '\u000B'
    private const val NB_HYPHEN = '\u001E'
    private const val SOFT_HYPHEN = '\u001F'
    private const val OBJECT = '\u0001'
    private const val FOOTNOTE = '\u0002'
    private const val DRAWN = '\u0008'
    private const val FIELD_BEGIN = '\u0013'
    private const val FIELD_SEPARATOR = '\u0014'
    private const val FIELD_END = '\u0015'
}
