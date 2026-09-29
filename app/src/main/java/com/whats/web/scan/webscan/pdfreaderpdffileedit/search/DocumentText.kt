package com.whats.web.scan.webscan.pdfreaderpdffileedit.search

import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.OoxmlZip
import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.TextToHtml
import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.legacy.Cfb
import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.legacy.LegacyDoc
import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.legacy.LegacyPpt
import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.legacy.LegacyXls
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream

/**
 * The searchable text of a document, one string per page (Office and text files count as one page).
 * Android-free apart from PdfBox, so the JVM harness runs it against the real samples.
 */
object DocumentText {

    /** Pages longer than this are cut: search needs the words, not megabytes of spreadsheet. */
    const val MAX_CHARS_PER_FILE = 1_000_000

    fun of(ext: String, input: InputStream, scratch: File): List<String> = when (ext.lowercase()) {
        "pdf" -> pdf(input, scratch)
        "docx", "xlsx", "pptx" -> listOf(ooxml(OoxmlZip.read(input), ext.lowercase()))
        "txt", "csv" -> listOf(cap(TextToHtml.decode(input.readBytes())))
        "doc", "xls", "ppt" -> listOf(cap(legacy(ext.lowercase(), input.readBytes())))
        else -> emptyList()
    }

    fun pdf(input: InputStream, scratch: File): List<String> {
        scratch.mkdirs()
        val memory = MemoryUsageSetting.setupMixed(PDF_MEMORY_BYTES).setTempDir(scratch)
        return PDDocument.load(input, memory).use { doc ->
            val stripper = PDFTextStripper()
            var total = 0
            (1..doc.numberOfPages).map { page ->
                if (total >= MAX_CHARS_PER_FILE) return@map ""
                stripper.startPage = page
                stripper.endPage = page
                val text = stripper.getText(doc).trim()
                total += text.length
                text
            }
        }
    }

    /** Office 97–2003 text through the compound-file readers; unreadable files give no text. */
    fun legacy(ext: String, bytes: ByteArray): String {
        if (!Cfb.isCompoundFile(bytes)) return ""
        return runCatching {
            val cfb = Cfb(bytes)
            when (ext) {
                "doc" -> LegacyDoc.cleanFields(LegacyDoc.text(cfb))
                    .replace('\r', '\n').replace('\u0007', ' ').replace('\u000C', '\n')
                "xls" -> LegacyXls.read(cfb).joinToString("\n") { sheet ->
                    sheet.rows.joinToString("\n") { it.joinToString(" ") }
                }
                "ppt" -> LegacyPpt.read(cfb).joinToString("\n") { slide ->
                    listOfNotNull(slide.title).plus(slide.body).joinToString("\n")
                }
                else -> ""
            }
        }.getOrDefault("")
    }

    /** Word paragraphs, spreadsheet cells and slide text, separated by line breaks. */
    fun ooxml(parts: Map<String, ByteArray>, ext: String): String {
        val names = when (ext) {
            "docx" -> parts.keys.filter { it == "word/document.xml" }
            "pptx" -> parts.keys.filter { it.startsWith("ppt/slides/slide") && it.endsWith(".xml") }
                .sortedBy { it.substringAfterLast("slide").substringBefore(".xml").toIntOrNull() ?: 0 }
            "xlsx" -> listOf("xl/sharedStrings.xml") +
                parts.keys.filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }.sorted()
            else -> emptyList()
        }
        val out = StringBuilder()
        names.forEach { name ->
            val bytes = parts[name] ?: return@forEach
            collect(OoxmlZip.parser(bytes), out)
            if (out.length >= MAX_CHARS_PER_FILE) return cap(out.toString())
        }
        return cap(out.toString().trim())
    }

    /** Text nodes (`w:t`, `a:t`, `t`, numeric `v`), with a line break at each paragraph / row end. */
    private fun collect(parser: XmlPullParser, out: StringBuilder) {
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "w:t", "a:t", "t" -> out.append(parser.nextText())
                    "v" -> out.append(parser.nextText()).append(' ')
                    "w:tab" -> out.append(' ')
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "w:p", "a:p", "si", "row" -> out.append('\n')
                    "c" -> out.append(' ')
                }
            }
        }
    }

    /**
     * Where [query] occurs in [text] (case-insensitive), as short snippets with the match marked by
     * [MARK_START]/[MARK_END] so the screen can bold it without re-searching.
     */
    fun snippets(text: String, query: String, max: Int = 3): List<String> {
        if (query.isBlank()) return emptyList()
        val out = mutableListOf<String>()
        var from = 0
        while (out.size < max) {
            val at = text.indexOf(query, from, ignoreCase = true)
            if (at < 0) break
            val start = (at - CONTEXT).coerceAtLeast(0)
            val end = (at + query.length + CONTEXT).coerceAtMost(text.length)
            val snippet = (if (start > 0) "…" else "") +
                text.substring(start, at) + MARK_START + text.substring(at, at + query.length) + MARK_END +
                text.substring(at + query.length, end) + (if (end < text.length) "…" else "")
            out += snippet.replace(WHITESPACE, " ")
            from = at + query.length
        }
        return out
    }

    fun count(text: String, query: String): Int {
        if (query.isBlank()) return 0
        var n = 0
        var from = 0
        while (true) {
            val at = text.indexOf(query, from, ignoreCase = true)
            if (at < 0) return n
            n++
            from = at + query.length
        }
    }

    private fun cap(text: String) = if (text.length > MAX_CHARS_PER_FILE) text.substring(0, MAX_CHARS_PER_FILE) else text

    const val MARK_START = '\u0002'
    const val MARK_END = '\u0003'
    private const val CONTEXT = 40
    private const val PDF_MEMORY_BYTES = 8L * 1024 * 1024
    private val WHITESPACE = Regex("\\s+")
}
