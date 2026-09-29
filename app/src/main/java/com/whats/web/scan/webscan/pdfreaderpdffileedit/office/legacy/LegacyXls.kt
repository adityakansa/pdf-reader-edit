package com.whats.web.scan.webscan.pdfreaderpdffileedit.office.legacy

import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.XlsxToHtml
import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.legacy.Cfb.Companion.le16
import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.legacy.Cfb.Companion.le32
import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.legacy.Cfb.Companion.le64
import java.io.IOException

/**
 * Excel 97–2003 (.xls, BIFF8) cell values: shared strings, inline labels, numbers (NUMBER, RK, MULRK),
 * booleans and formula results, per sheet in workbook order. Formats and styles are not read; numbers
 * show as Excel shows them by default.
 */
object LegacyXls {

    data class Sheet(val name: String, val rows: List<List<String>>, val truncated: Boolean)

    fun read(cfb: Cfb): List<Sheet> {
        val stream = cfb.stream("Workbook") ?: cfb.stream("Book") ?: throw IOException("No Workbook stream")
        val records = records(stream)
        val strings = sharedStrings(records)
        val sheets = mutableListOf<Pair<String, Int>>()
        records.forEach { r ->
            if (r.type == BOUNDSHEET && r.data.size >= 8 && r.data[5].toInt() == 0) {
                sheets += shortString(r.data, 6) to le32(r.data, 0)
            }
        }
        return sheets.map { (name, offset) -> sheet(name, records.dropWhile { it.offset < offset }, strings) }
    }

    class Record(val type: Int, val offset: Int, val data: ByteArray)

    private fun records(stream: ByteArray): List<Record> {
        val list = mutableListOf<Record>()
        var p = 0
        while (p + 4 <= stream.size) {
            val type = le16(stream, p)
            val length = le16(stream, p + 2)
            if (p + 4 + length > stream.size) break
            list += Record(type, p, stream.copyOfRange(p + 4, p + 4 + length))
            p += 4 + length
        }
        return list
    }

    private fun sheet(name: String, records: List<Record>, strings: List<String>): Sheet {
        val cells = HashMap<Int, MutableMap<Int, String>>()
        var truncated = false
        var pendingFormula: Pair<Int, Int>? = null
        fun put(row: Int, col: Int, value: String) {
            if (row >= XlsxToHtml.MAX_ROWS) { truncated = true; return }
            if (col >= XlsxToHtml.MAX_COLUMNS) return
            cells.getOrPut(row) { HashMap() }[col] = value
        }
        for (r in records.drop(1)) {
            val d = r.data
            when (r.type) {
                EOF -> break
                LABELSST -> if (d.size >= 10) put(le16(d, 0), le16(d, 2), strings.getOrElse(le32(d, 6)) { "" })
                LABEL -> if (d.size >= 9) put(le16(d, 0), le16(d, 2), unicodeString(d, 6).first)
                NUMBER -> if (d.size >= 14) put(le16(d, 0), le16(d, 2), number(java.lang.Double.longBitsToDouble(le64(d, 6))))
                RK -> if (d.size >= 10) put(le16(d, 0), le16(d, 2), number(rk(le32(d, 6))))
                MULRK -> if (d.size >= 6) {
                    val row = le16(d, 0)
                    val first = le16(d, 2)
                    val count = (d.size - 6) / 6
                    for (i in 0 until count) put(row, first + i, number(rk(le32(d, 4 + i * 6 + 2))))
                }
                BOOLERR -> if (d.size >= 8) {
                    val value = d[6].toInt() and 0xFF
                    put(le16(d, 0), le16(d, 2), if (d[7].toInt() == 0) (if (value != 0) "TRUE" else "FALSE") else "#ERR")
                }
                FORMULA -> if (d.size >= 14) {
                    val row = le16(d, 0)
                    val col = le16(d, 2)
                    if ((d[12].toInt() and 0xFF) == 0xFF && (d[13].toInt() and 0xFF) == 0xFF) {
                        when (d[6].toInt()) {
                            0 -> pendingFormula = row to col // the text follows in a STRING record
                            1 -> put(row, col, if (d[8].toInt() != 0) "TRUE" else "FALSE")
                            2 -> put(row, col, "#ERR")
                        }
                    } else {
                        put(row, col, number(java.lang.Double.longBitsToDouble(le64(d, 6))))
                    }
                }
                STRING -> pendingFormula?.let { (row, col) ->
                    put(row, col, unicodeString(d, 0).first)
                    pendingFormula = null
                }
            }
        }
        val lastRow = cells.keys.maxOrNull() ?: -1
        val rows = (0..lastRow).map { r ->
            val row = cells[r] ?: return@map emptyList()
            val last = row.keys.maxOrNull() ?: -1
            (0..last).map { row[it] ?: "" }
        }
        return Sheet(name, rows, truncated)
    }

    /** The SST, reassembled across CONTINUE records (each restarts the character-width flag). */
    private fun sharedStrings(records: List<Record>): List<String> {
        val index = records.indexOfFirst { it.type == SST }
        if (index < 0) return emptyList()
        val parts = mutableListOf(records[index].data)
        var i = index + 1
        while (i < records.size && records[i].type == CONTINUE) parts += records[i++].data
        val reader = FragmentReader(parts)
        reader.skip(4)
        val unique = reader.u32()
        val out = ArrayList<String>(unique.coerceIn(0, MAX_STRINGS))
        repeat(unique.coerceIn(0, MAX_STRINGS)) {
            if (!reader.hasMore()) return out
            out += reader.richString()
        }
        return out
    }

    /** Reads bytes across record fragments; a string's characters may continue in the next fragment. */
    private class FragmentReader(private val parts: List<ByteArray>) {
        private var part = 0
        private var pos = 0

        fun hasMore() = part < parts.size && (pos < parts[part].size || part + 1 < parts.size)

        private fun ensure() {
            while (part < parts.size && pos >= parts[part].size) { part++; pos = 0 }
        }

        fun u8(): Int { ensure(); return if (part < parts.size) parts[part][pos++].toInt() and 0xFF else 0 }
        fun u16() = u8() or (u8() shl 8)
        fun u32() = u16() or (u16() shl 16)
        fun skip(n: Int) { repeat(n) { u8() } }

        fun richString(): String {
            val count = u16()
            val flags = u8()
            var wide = flags and 0x01 != 0
            val runs = if (flags and 0x08 != 0) u16() else 0
            val ext = if (flags and 0x04 != 0) u32() else 0
            val sb = StringBuilder(count)
            var remaining = count
            while (remaining > 0 && part < parts.size) {
                if (pos >= parts[part].size) {
                    part++
                    pos = 0
                    if (part >= parts.size) break
                    // A string that crosses into a CONTINUE record restarts with a fresh width flag.
                    wide = (parts[part][pos++].toInt() and 0x01) != 0
                    continue
                }
                if (wide) {
                    sb.append((u8() or (u8() shl 8)).toChar())
                } else {
                    // "Compressed" characters are UTF-16 with the high byte dropped, i.e. Latin-1.
                    sb.append(Char(u8()))
                }
                remaining--
            }
            skip(runs * 4 + ext)
            return sb.toString()
        }
    }

    /** ShortXLUnicodeString (sheet names): cch u8, flags u8, characters. */
    private fun shortString(d: ByteArray, offset: Int): String {
        val count = d[offset].toInt() and 0xFF
        val wide = d.getOrElse(offset + 1) { 0 }.toInt() and 0x01 != 0
        val start = offset + 2
        return if (wide) String(d, start, minOf(count * 2, d.size - start), Charsets.UTF_16LE)
        else String(d, start, minOf(count, d.size - start), Charsets.ISO_8859_1)
    }

    /** XLUnicodeString: cch u16, flags u8, characters; returns the text and the bytes used. */
    private fun unicodeString(d: ByteArray, offset: Int): Pair<String, Int> {
        val count = le16(d, offset)
        val wide = d.getOrElse(offset + 2) { 0 }.toInt() and 0x01 != 0
        val start = offset + 3
        val bytes = if (wide) count * 2 else count
        val length = minOf(bytes, (d.size - start).coerceAtLeast(0))
        val text = if (wide) String(d, start, length, Charsets.UTF_16LE) else String(d, start, length, Charsets.ISO_8859_1)
        return text to 3 + bytes
    }

    /** An RK number: 30 bits of an integer or the top of a double, optionally divided by 100. */
    internal fun rk(value: Int): Double {
        val base = if (value and 0x02 != 0) (value shr 2).toDouble()
        else java.lang.Double.longBitsToDouble((value.toLong() and 0xFFFFFFFCL) shl 32)
        return if (value and 0x01 != 0) base / 100.0 else base
    }

    internal fun number(value: Double): String = when {
        value.isNaN() || value.isInfinite() -> "#NUM!"
        else -> XlsxToHtml.displayNumber(value.toBigDecimal().toPlainString())
    }

    private const val BOUNDSHEET = 0x0085
    private const val SST = 0x00FC
    private const val CONTINUE = 0x003C
    private const val EOF = 0x000A
    private const val LABELSST = 0x00FD
    private const val LABEL = 0x0204
    private const val NUMBER = 0x0203
    private const val RK = 0x027E
    private const val MULRK = 0x00BD
    private const val BOOLERR = 0x0205
    private const val FORMULA = 0x0006
    private const val STRING = 0x0207
    private const val MAX_STRINGS = 2_000_000
}
