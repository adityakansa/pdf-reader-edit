package com.whats.web.scan.webscan.pdfreaderpdffileedit.office.legacy

import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.HtmlPage
import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.OoxmlZip
import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.XlsxToHtml
import java.io.IOException

/**
 * .doc / .xls / .ppt (Office 97–2003) shown in the same viewer as their modern versions: Word text on
 * page cards, Excel sheets in the spreadsheet grid with tabs, PowerPoint slides as slide cards.
 * Throws [IOException] for anything it cannot read (encrypted, Word 6/95, not a compound file), and the
 * viewer then falls back to "open with another app".
 */
object LegacyToHtml {

    fun convert(ext: String, bytes: ByteArray, rowCapNotice: String): String {
        if (!Cfb.isCompoundFile(bytes)) throw IOException("Not an Office 97–2003 file")
        val cfb = Cfb(bytes)
        return when (ext.lowercase()) {
            "doc" -> doc(LegacyDoc.read(cfb))
            "xls" -> xls(LegacyXls.read(cfb), rowCapNotice)
            "ppt" -> ppt(LegacyPpt.read(cfb))
            else -> throw IOException("Unsupported $ext")
        }
    }

    fun doc(blocks: List<LegacyDoc.Block>): String {
        val pages = mutableListOf(StringBuilder())
        blocks.forEach { block ->
            val page = pages.last()
            when (block) {
                LegacyDoc.Block.PageBreak -> if (page.isNotEmpty()) pages += StringBuilder()
                is LegacyDoc.Block.Paragraph -> page.append("<p>").append(inline(block.text).ifBlank { "&nbsp;" }).append("</p>")
                is LegacyDoc.Block.Table -> {
                    page.append("<table class=\"doc-table\">")
                    block.rows.forEach { row ->
                        page.append("<tr>")
                        row.forEach { cell -> page.append("<td>").append(inline(cell.trim())).append("</td>") }
                        page.append("</tr>")
                    }
                    page.append("</table>")
                }
            }
        }
        val html = pages.filter { it.isNotBlank() }.joinToString("") { "<div class=\"doc-page\">$it</div>" }
        return HtmlPage.wrap(html, bodyClass = "desk")
    }

    fun xls(sheets: List<LegacyXls.Sheet>, rowCapNotice: String): String {
        val body = StringBuilder()
        if (sheets.size <= 1) sheets.firstOrNull()?.let { body.append(XlsxToHtml.grid(it.rows)) }
        else body.append(XlsxToHtml.tabs(sheets.map { it.name to XlsxToHtml.grid(it.rows) }))
        if (sheets.any { it.truncated }) body.append("<div class=\"notice\">").append(OoxmlZip.escape(rowCapNotice)).append("</div>")
        return HtmlPage.wrap(body.toString())
    }

    fun ppt(slides: List<LegacyPpt.Slide>): String {
        val body = StringBuilder()
        slides.forEachIndexed { index, slide ->
            body.append("<div class=\"slide-number\">").append(index + 1).append(" / ").append(slides.size).append("</div>")
            body.append("<div class=\"deck-slide\" style=\"padding-top:75%\"><div class=\"legacy-slide\">")
            slide.title?.let { body.append("<div class=\"legacy-title\">").append(OoxmlZip.escape(it)).append("</div>") }
            if (slide.body.isNotEmpty()) {
                body.append("<ul class=\"legacy-body\">")
                slide.body.forEach { body.append("<li>").append(OoxmlZip.escape(it)).append("</li>") }
                body.append("</ul>")
            }
            body.append("</div></div>")
        }
        return HtmlPage.wrap(body.toString(), bodyClass = "deck")
    }

    private fun inline(text: String): String =
        OoxmlZip.escape(text).replace("\n", "<br>").replace("\t", "&emsp;")
}
