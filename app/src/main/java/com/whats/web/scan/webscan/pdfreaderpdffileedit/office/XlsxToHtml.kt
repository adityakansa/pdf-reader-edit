package com.whats.web.scan.webscan.pdfreaderpdffileedit.office

import org.xmlpull.v1.XmlPullParser

/**
 * FR-034. XLSX → HTML: one table per sheet in workbook order, shared and inline strings resolved,
 * numbers shown as they are stored. Formulas are not evaluated — the cached value in the file is what
 * a spreadsheet app last wrote, and that is what is shown.
 */
object XlsxToHtml {

    /** A phone cannot usefully scroll more than this, and the DOM cost grows with every cell. */
    const val MAX_ROWS = 5_000
    const val MAX_COLUMNS = 100

    fun convert(parts: Map<String, ByteArray>, rowCapNotice: String): String {
        val strings = sharedStrings(parts)
        val sheets = sheetOrder(parts).mapNotNull { (name, path) ->
            parts[path]?.let { bytes -> name to sheet(OoxmlZip.parser(bytes), strings) }
        }
        val truncated = sheets.any { it.second.truncated }
        val body = StringBuilder()
        if (sheets.size <= 1) {
            sheets.firstOrNull()?.let { body.append(it.second.html) }
        } else {
            body.append(tabs(sheets.map { it.first to it.second.html }))
        }
        if (truncated) body.append("<div class=\"notice\">").append(OoxmlZip.escape(rowCapNotice)).append("</div>")
        return HtmlPage.wrap(body.toString())
    }

    /**
     * FR-034: one tab per sheet. The viewer runs with JavaScript off, so the tabs are CSS only: a hidden
     * radio button per sheet, a label per tab, and a `:checked ~` rule that shows the matching panel.
     */
    internal fun tabs(sheets: List<Pair<String, String>>): String {
        val out = StringBuilder("<div class=\"sheets\">")
        val rules = StringBuilder("<style>")
        sheets.indices.forEach { i ->
            out.append("<input type=\"radio\" name=\"sheet\" class=\"sheet-radio\" id=\"s$i\"")
            if (i == 0) out.append(" checked")
            out.append(">")
            rules.append("#s$i:checked ~ .sheet-panels .p$i{display:block}")
            rules.append("#s$i:checked ~ .sheet-tabs label[for=s$i]{border-bottom-color:#D32F2F;color:#D32F2F}")
        }
        rules.append("</style>")
        out.append("<div class=\"sheet-tabs\">")
        sheets.forEachIndexed { i, (name, _) ->
            out.append("<label for=\"s$i\">").append(OoxmlZip.escape(name)).append("</label>")
        }
        out.append("</div><div class=\"sheet-panels\">")
        sheets.forEachIndexed { i, (_, html) ->
            out.append("<div class=\"sheet-panel p$i\">").append(html).append("</div>")
        }
        out.append("</div></div>")
        return rules.toString() + out.toString()
    }

    private data class Sheet(val html: String, val truncated: Boolean)

    /** Sheet name → part path, in the order the workbook lists them. */
    private fun sheetOrder(parts: Map<String, ByteArray>): List<Pair<String, String>> {
        val workbook = parts["xl/workbook.xml"] ?: return emptyList()
        val rels = OoxmlZip.relationships(parts, "xl/_rels/workbook.xml.rels", "xl/")
        val sheets = mutableListOf<Pair<String, String>>()
        val parser = OoxmlZip.parser(workbook)
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "sheet") {
                val name = parser.getAttributeValue(null, "name") ?: continue
                val id = parser.getAttributeValue(null, "r:id")
                val path = rels[id] ?: continue
                sheets += name to path
            }
        }
        return sheets
    }

    private fun sharedStrings(parts: Map<String, ByteArray>): List<String> {
        val bytes = parts["xl/sharedStrings.xml"] ?: return emptyList()
        val strings = mutableListOf<String>()
        val current = StringBuilder()
        val parser = OoxmlZip.parser(bytes)
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "si" -> current.setLength(0)
                    "t" -> current.append(parser.nextText())
                }

                XmlPullParser.END_TAG -> if (parser.name == "si") strings += current.toString()
            }
        }
        return strings
    }

    private fun sheet(parser: XmlPullParser, strings: List<String>): Sheet {
        val rows = mutableListOf<List<String>>()
        var truncated = false
        var row: MutableList<String>? = null
        var rowNumber = 0
        var cellType: String? = null
        var cellColumn = -1
        val cellText = StringBuilder()

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> {
                        // Rows can be skipped in the file too (blank rows); `r` says which row this is.
                        val declared = parser.getAttributeValue(null, "r")?.toIntOrNull()
                        val target = (declared ?: (rowNumber + 1)).coerceAtLeast(rowNumber + 1)
                        if (target > MAX_ROWS) {
                            truncated = true
                            row = null
                        } else {
                            while (rows.size < target - 1) rows.add(emptyList())
                            row = mutableListOf()
                        }
                        rowNumber = target
                    }

                    "c" -> {
                        cellType = parser.getAttributeValue(null, "t")
                        cellColumn = columnIndex(parser.getAttributeValue(null, "r")) ?: (row?.size ?: 0)
                        cellText.setLength(0)
                    }

                    // An inline rich-text string has one <t> per run; they join into one cell.
                    "v", "t" -> cellText.append(parser.nextText())
                }

                XmlPullParser.END_TAG -> when (parser.name) {
                    "c" -> row?.let { cells ->
                        if (cellColumn < MAX_COLUMNS) {
                            // Blank cells are left out of the XML, so pad up to this cell's own column.
                            while (cells.size < cellColumn) cells += ""
                            val raw = cellText.toString()
                            cells += when (cellType) {
                                "s" -> strings.getOrElse(raw.trim().toIntOrNull() ?: -1) { "" }
                                null, "n" -> displayNumber(raw)
                                "b" -> if (raw.trim() == "1") "TRUE" else "FALSE"
                                else -> raw
                            }
                        }
                    }

                    "row" -> row?.let { rows.add(it); row = null }
                }
            }
        }
        return Sheet(grid(rows), truncated)
    }

    /**
     * The spreadsheet look people know from Excel: a grey header row of column letters, row numbers down
     * the side, gridlines, numbers right-aligned. Trailing empty rows and columns are trimmed.
     */
    fun grid(rows: List<List<String>>): String {
        val lastRow = rows.indexOfLast { r -> r.any { it.isNotBlank() } }
        if (lastRow < 0) return "<div class=\"notice\">&nbsp;</div>"
        val used = rows.subList(0, lastRow + 1)
        val columns = used.maxOf { r -> r.indexOfLast { it.isNotBlank() } + 1 }.coerceAtLeast(1)
        val out = StringBuilder("<div class=\"grid-wrap\"><table class=\"grid\"><thead><tr><th class=\"corner\"></th>")
        repeat(columns) { out.append("<th>").append(columnName(it)).append("</th>") }
        out.append("</tr></thead><tbody>")
        used.forEachIndexed { index, cells ->
            out.append("<tr><th class=\"rownum\">").append(index + 1).append("</th>")
            repeat(columns) { c ->
                val value = cells.getOrElse(c) { "" }
                val numeric = value.isNotEmpty() && value.toBigDecimalOrNull() != null
                out.append(if (numeric) "<td class=\"num\">" else "<td>").append(OoxmlZip.escape(value)).append("</td>")
            }
            out.append("</tr>")
        }
        return out.append("</tbody></table></div>").toString()
    }

    /** 0 → "A", 25 → "Z", 26 → "AA". */
    fun columnName(index: Int): String {
        var n = index + 1
        val name = StringBuilder()
        while (n > 0) {
            val r = (n - 1) % LETTERS
            name.insert(0, 'A' + r)
            n = (n - 1) / LETTERS
        }
        return name.toString()
    }

    private const val LETTERS = 26

    /**
     * A stored number as Excel shows it by default: at most 15 significant digits, no trailing zeros, no
     * exponent. Files keep binary floating-point values, so `92.4` is often stored as `92.400000000000006`.
     * Anything that is not a plain number is returned as it was.
     */
    internal fun displayNumber(raw: String): String {
        val trimmed = raw.trim()
        val number = trimmed.toBigDecimalOrNull() ?: return raw
        if (number.signum() == 0) return "0"
        return number.round(java.math.MathContext(EXCEL_DIGITS)).stripTrailingZeros().toPlainString()
    }

    private const val EXCEL_DIGITS = 15

    /** "C12" → 2. Null when the reference is missing or malformed. */
    internal fun columnIndex(reference: String?): Int? {
        val letters = reference?.takeWhile { it.isLetter() }?.uppercase() ?: return null
        if (letters.isEmpty()) return null
        return letters.fold(0) { acc, c -> acc * 26 + (c - 'A' + 1) } - 1
    }
}
