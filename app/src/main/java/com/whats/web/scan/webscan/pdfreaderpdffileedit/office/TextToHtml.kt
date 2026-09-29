package com.whats.web.scan.webscan.pdfreaderpdffileedit.office

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Plain text and CSV, the two non-Office formats every document-reader app on Play lists next to Word and
 * Excel. Both become one HTML page for the same WebView as the Office viewer: TXT keeps its line breaks in a
 * wrapped monospace-free block, CSV becomes the spreadsheet grid `XlsxToHtml` uses.
 */
object TextToHtml {

    /** Bigger files are cut here with a notice; a phone cannot usefully scroll further anyway. */
    const val MAX_CHARS = 2_000_000
    const val MAX_ROWS = XlsxToHtml.MAX_ROWS

    /**
     * UTF-8 when the bytes are valid UTF-8 (with or without a BOM), UTF-16 when a UTF-16 BOM says so,
     * otherwise Windows-1252, which is what Notepad and Excel write for "ANSI" text in most of the world.
     */
    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }
        val start = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) 3 else 0
        val strict = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            strict.decode(ByteBuffer.wrap(bytes, start, bytes.size - start)).toString()
        } catch (_: CharacterCodingException) {
            String(bytes, Charset.forName("windows-1252"))
        }
    }

    fun plain(text: String, truncatedNotice: String): String {
        val cut = text.length > MAX_CHARS
        val body = StringBuilder("<div class=\"plain\">")
            .append(OoxmlZip.escape(if (cut) text.substring(0, MAX_CHARS) else text))
            .append("</div>")
        if (cut) body.append("<div class=\"notice\">").append(OoxmlZip.escape(truncatedNotice)).append("</div>")
        return HtmlPage.wrap(body.toString())
    }

    fun csv(text: String, rowCapNotice: String): String {
        val rows = parseCsv(text, delimiterOf(text), MAX_ROWS + 1)
        val truncated = rows.size > MAX_ROWS
        val grid = XlsxToHtml.grid(rows.take(MAX_ROWS))
        val body = StringBuilder(grid)
        if (truncated) body.append("<div class=\"notice\">").append(OoxmlZip.escape(rowCapNotice)).append("</div>")
        return HtmlPage.wrap(body.toString())
    }

    /** Comma, semicolon (European Excel) or tab — whichever the first line has most of. */
    fun delimiterOf(text: String): Char {
        val firstLine = text.lineSequence().firstOrNull().orEmpty()
        return listOf(',', ';', '\t').maxBy { d -> firstLine.count { it == d } }
            .takeIf { d -> firstLine.contains(d) } ?: ','
    }

    /** RFC 4180: quoted fields may hold the delimiter, line breaks and doubled quotes. */
    fun parseCsv(text: String, delimiter: Char = ',', maxRows: Int = Int.MAX_VALUE): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length && rows.size < maxRows) {
            val c = text[i]
            if (quoted) {
                when {
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
                    c == '"' -> quoted = false
                    else -> field.append(c)
                }
            } else {
                when (c) {
                    '"' -> if (field.isEmpty()) quoted = true else field.append(c)
                    delimiter -> { row += field.toString(); field.setLength(0) }
                    '\r' -> Unit
                    '\n' -> { row.add(field.toString()); field.setLength(0); rows.add(row); row = mutableListOf() }
                    else -> field.append(c)
                }
            }
            i++
        }
        if (rows.size < maxRows && (field.isNotEmpty() || row.isNotEmpty())) {
            row += field.toString()
            rows.add(row)
        }
        return rows
    }
}
