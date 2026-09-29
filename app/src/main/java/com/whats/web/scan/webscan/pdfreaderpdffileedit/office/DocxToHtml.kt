package com.whats.web.scan.webscan.pdfreaderpdffileedit.office

import org.xmlpull.v1.XmlPullParser

/**
 * FR-033. DOCX → HTML that looks like the document, not like a web page: white page cards on a grey
 * desk (Word's print layout on a phone), split where Word itself last broke pages, with the formatting
 * people notice — style-based headings and titles, fonts, sizes, colours, highlight, bold / italic /
 * underline / strike, super- and subscript, alignment, indents, paragraph spacing, empty lines,
 * numbered and bulleted lists with their real markers and nesting, tables with shading and merged
 * columns, and images at their stored size.
 *
 * What is not attempted: floating text boxes, headers and footers, footnotes, columns, tracked changes.
 */
object DocxToHtml {

    fun convert(parts: Map<String, ByteArray>, media: Map<String, String>): String {
        val body = parts["word/document.xml"] ?: return HtmlPage.wrap("", bodyClass = "desk")
        val rels = OoxmlZip.relationships(parts, "word/_rels/document.xml.rels", "word/")
        val styles = parts["word/styles.xml"]?.let { Styles.parse(OoxmlZip.parser(it)) } ?: Styles.EMPTY
        val numbering = parts["word/numbering.xml"]?.let { Numbering.parse(OoxmlZip.parser(it)) } ?: Numbering.EMPTY
        val html = Writer(rels, media, styles, numbering).convert(OoxmlZip.parser(body))
        return HtmlPage.wrap(html, bodyClass = "desk")
    }

    /** Character formatting, from a style or a run's own `w:rPr`. Null means "not set here". */
    internal data class RunFormat(
        val bold: Boolean? = null,
        val italic: Boolean? = null,
        val underline: Boolean? = null,
        val strike: Boolean? = null,
        val colorHex: String? = null,
        val halfPoints: Int? = null,
        val font: String? = null,
        val highlight: String? = null,
        val vertAlign: String? = null,
    ) {
        /** [other]'s values win where it sets them — a run over its paragraph style, a style over its parent. */
        fun overlay(other: RunFormat) = RunFormat(
            other.bold ?: bold, other.italic ?: italic, other.underline ?: underline, other.strike ?: strike,
            other.colorHex ?: colorHex, other.halfPoints ?: halfPoints, other.font ?: font,
            other.highlight ?: highlight, other.vertAlign ?: vertAlign,
        )

        fun css(): String = buildString {
            if (bold == true) append("font-weight:bold;")
            if (italic == true) append("font-style:italic;")
            val decorations = listOfNotNull(
                "underline".takeIf { underline == true },
                "line-through".takeIf { strike == true },
            )
            if (decorations.isNotEmpty()) append("text-decoration:").append(decorations.joinToString(" ")).append(';')
            colorHex?.let { append("color:#").append(it).append(';') }
            halfPoints?.let { append("font-size:").append(ptToPx(it / 2f)).append("px;") }
            font?.let { append("font-family:'").append(it.replace("'", "")).append("',sans-serif;") }
            highlight?.let { append("background:").append(it).append(';') }
            when (vertAlign) {
                "superscript" -> append("vertical-align:super;font-size:smaller;")
                "subscript" -> append("vertical-align:sub;font-size:smaller;")
            }
        }
    }

    /** Paragraph style: display name, parent, run formatting, alignment. */
    internal data class Style(
        val name: String?,
        val basedOn: String?,
        val run: RunFormat,
        val align: String?,
        /** Word's "List Number"/"List Bullet" styles carry their numbering here, not on the paragraph. */
        val numId: String? = null,
        val ilvl: Int? = null,
    )

    internal class Styles(private val byId: Map<String, Style>, val defaultRun: RunFormat) {

        fun name(id: String?): String? = id?.let { byId[it]?.name ?: it }

        /** Run formatting of [id] after following `basedOn` (a few levels are enough in practice). */
        fun run(id: String?): RunFormat = chain(id).fold(RunFormat()) { acc, style -> acc.overlay(style.run) }

        fun align(id: String?): String? = chain(id).mapNotNull { it.align }.lastOrNull()

        /** The list a style puts its paragraphs in, if any, and at which level. */
        fun numbering(id: String?): Pair<String, Int>? {
            val styles = chain(id)
            val numId = styles.mapNotNull { it.numId }.lastOrNull() ?: return null
            return numId to (styles.mapNotNull { it.ilvl }.lastOrNull() ?: 0)
        }

        private fun chain(id: String?): List<Style> {
            val list = mutableListOf<Style>()
            var current = id
            while (current != null && list.size < MAX_DEPTH) {
                val style = byId[current] ?: break
                list.add(0, style)
                current = style.basedOn
            }
            return list
        }

        companion object {
            private const val MAX_DEPTH = 8
            val EMPTY = Styles(emptyMap(), RunFormat())

            fun parse(parser: XmlPullParser): Styles {
                val map = mutableMapOf<String, Style>()
                var defaults = RunFormat()
                var inDefaults = false
                var id: String? = null
                var name: String? = null
                var basedOn: String? = null
                var align: String? = null
                var numId: String? = null
                var ilvl: Int? = null
                var run = RunFormat()
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    when (parser.eventType) {
                        XmlPullParser.START_TAG -> when (parser.name) {
                            "w:rPrDefault" -> inDefaults = true
                            "w:style" -> {
                                id = parser.getAttributeValue(null, "w:styleId")
                                name = null
                                basedOn = null
                                align = null
                                numId = null
                                ilvl = null
                                run = RunFormat()
                            }
                            "w:numId" -> numId = parser.getAttributeValue(null, "w:val")
                            "w:ilvl" -> ilvl = parser.getAttributeValue(null, "w:val")?.toIntOrNull()
                            "w:name" -> name = parser.getAttributeValue(null, "w:val")
                            "w:basedOn" -> basedOn = parser.getAttributeValue(null, "w:val")
                            "w:jc" -> align = parser.getAttributeValue(null, "w:val")
                            else -> {
                                if (inDefaults) defaults = readRunProperty(parser, defaults)
                                else run = readRunProperty(parser, run)
                            }
                        }
                        XmlPullParser.END_TAG -> when (parser.name) {
                            "w:rPrDefault" -> inDefaults = false
                            "w:style" -> id?.let { map[it] = Style(name, basedOn, run, align, numId, ilvl) }
                        }
                    }
                }
                return Styles(map, defaults)
            }
        }
    }

    /** List definitions: numId → level → (format, text, start). */
    internal class Numbering(private val levels: Map<String, Map<Int, Level>>) {
        data class Level(val format: String, val text: String, val start: Int)

        fun level(numId: String, ilvl: Int): Level? = levels[numId]?.get(ilvl)

        companion object {
            val EMPTY = Numbering(emptyMap())

            fun parse(parser: XmlPullParser): Numbering {
                val abstracts = mutableMapOf<String, MutableMap<Int, Level>>()
                val nums = mutableMapOf<String, String>()
                var abstractId: String? = null
                var ilvl = -1
                var format = "decimal"
                var text = "%1."
                var start = 1
                var numId: String? = null
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    when (parser.eventType) {
                        XmlPullParser.START_TAG -> when (parser.name) {
                            "w:abstractNum" -> abstractId = parser.getAttributeValue(null, "w:abstractNumId")
                            "w:lvl" -> {
                                ilvl = parser.getAttributeValue(null, "w:ilvl")?.toIntOrNull() ?: -1
                                format = "decimal"
                                text = "%${ilvl + 1}."
                                start = 1
                            }
                            "w:numFmt" -> if (ilvl >= 0) format = parser.getAttributeValue(null, "w:val") ?: format
                            "w:lvlText" -> if (ilvl >= 0) text = parser.getAttributeValue(null, "w:val") ?: text
                            "w:start" -> if (ilvl >= 0) start = parser.getAttributeValue(null, "w:val")?.toIntOrNull() ?: 1
                            "w:num" -> numId = parser.getAttributeValue(null, "w:numId")
                            "w:abstractNumId" -> {
                                val id = numId
                                val value = parser.getAttributeValue(null, "w:val")
                                if (id != null && value != null) nums[id] = value
                            }
                        }
                        XmlPullParser.END_TAG -> when (parser.name) {
                            "w:lvl" -> {
                                val a = abstractId
                                if (a != null && ilvl >= 0) {
                                    abstracts.getOrPut(a) { mutableMapOf() }[ilvl] = Level(format, text, start)
                                }
                                ilvl = -1
                            }
                            "w:abstractNum" -> abstractId = null
                            "w:num" -> numId = null
                        }
                    }
                }
                return Numbering(nums.mapValues { (_, abstract) -> abstracts[abstract].orEmpty() })
            }
        }
    }

    private class Run(val html: String, val pageBreak: Boolean)

    private class Writer(
        private val rels: Map<String, String>,
        private val media: Map<String, String>,
        private val styles: Styles,
        private val numbering: Numbering,
    ) {
        private val pages = mutableListOf<String>()
        private var out = StringBuilder()

        /** numId → counter per level, so "1. 2. 3." continues across paragraphs and restarts below. */
        private val counters = mutableMapOf<String, IntArray>()

        fun convert(parser: XmlPullParser): String {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                when (parser.name) {
                    "w:p" -> paragraph(parser)
                    "w:tbl" -> table(parser)
                }
            }
            newPage()
            val base = styles.defaultRun.css()
            return pages.joinToString("") { "<div class=\"doc-page\" style=\"$base\">$it</div>" }
        }

        private fun newPage() {
            if (out.isNotBlank()) pages.add(out.toString())
            out = StringBuilder()
        }

        private fun paragraph(parser: XmlPullParser) {
            val p = ParagraphProps()
            var inPPr = false
            val content = StringBuilder()
            var depth = 1
            while (depth > 0 && parser.next() != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> {
                        depth++
                        when (parser.name) {
                            "w:pPr" -> inPPr = true
                            "w:pStyle" -> p.styleId = parser.getAttributeValue(null, "w:val")
                            "w:jc" -> if (inPPr) p.align = parser.getAttributeValue(null, "w:val")
                            "w:ind" -> if (inPPr) {
                                p.indentTwips = (
                                    parser.getAttributeValue(null, "w:left")
                                        ?: parser.getAttributeValue(null, "w:start")
                                    )?.toIntOrNull() ?: 0
                                p.firstLineTwips = parser.getAttributeValue(null, "w:firstLine")?.toIntOrNull()
                                    ?: -(parser.getAttributeValue(null, "w:hanging")?.toIntOrNull() ?: 0)
                            }
                            "w:spacing" -> if (inPPr) {
                                p.beforeTwips = parser.getAttributeValue(null, "w:before")?.toIntOrNull()
                                p.afterTwips = parser.getAttributeValue(null, "w:after")?.toIntOrNull()
                            }
                            "w:numId" -> p.numId = parser.getAttributeValue(null, "w:val")
                            "w:ilvl" -> p.ilvl = parser.getAttributeValue(null, "w:val")?.toIntOrNull() ?: 0
                            "w:pageBreakBefore" -> p.breakBefore = parser.getAttributeValue(null, "w:val") != "0"
                            "w:lastRenderedPageBreak" -> if (content.isBlank()) p.breakBefore = true else p.breakAfter = true
                            "w:r" -> {
                                val run = textRun(parser, styles.run(p.styleId))
                                if (run.pageBreak) {
                                    p.hasBreakRun = true
                                    if (content.isBlank()) p.breakBefore = true else p.breakAfter = true
                                }
                                content.append(run.html)
                                depth--
                            }
                            "w:tbl" -> {
                                // A table nested in a paragraph is rare; write it where it sits.
                                table(parser)
                                depth--
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        depth--
                        if (parser.name == "w:pPr") inPPr = false
                        if (parser.name == "w:p") break
                    }
                }
            }
            if (p.breakBefore) newPage()
            // A paragraph that only holds a page break is the break itself, not an empty line on the next page.
            if (!(p.hasBreakRun && content.isBlank())) write(p, content.toString())
            if (p.breakAfter) newPage()
        }

        private class ParagraphProps {
            var styleId: String? = null
            var align: String? = null
            var indentTwips = 0
            var firstLineTwips = 0
            var beforeTwips: Int? = null
            var afterTwips: Int? = null
            var numId: String? = null
            var ilvl = 0
            var breakBefore = false
            var breakAfter = false
            var hasBreakRun = false
        }

        private fun write(p: ParagraphProps, content: String) {
            val styleName = styles.name(p.styleId).orEmpty()
            val heading = (HEADING.find(styleName) ?: HEADING.find(p.styleId.orEmpty()))
                ?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..6 }
            val isTitle = styleName.equals("Title", ignoreCase = true)
            val css = StringBuilder()
            // Normal text inherits the style through its runs; headings and titles also carry it on the block.
            if (heading != null || isTitle) css.append(styles.run(p.styleId).css())
            cssAlign(p.align ?: styles.align(p.styleId))?.let { css.append("text-align:").append(it).append(';') }
            p.beforeTwips?.let { css.append("margin-top:").append(twipsToPx(it)).append("px;") }
            p.afterTwips?.let { css.append("margin-bottom:").append(twipsToPx(it)).append("px;") }

            val styleList = styles.numbering(p.styleId)
            val numId = p.numId ?: styleList?.first
            val ilvl = if (p.numId != null) p.ilvl else styleList?.second ?: p.ilvl
            val marker = listMarker(numId, ilvl, styleName)
            if (marker != null) {
                val left = twipsToPx(p.indentTwips).takeIf { it > 0 } ?: (LIST_INDENT_PX * (ilvl + 1))
                css.append("padding-left:").append(left).append("px;")
                out.append("<p class=\"li\" style=\"").append(css).append("\"><span class=\"marker\" style=\"width:")
                    .append(LIST_INDENT_PX).append("px;margin-left:-").append(LIST_INDENT_PX).append("px\">")
                    .append(OoxmlZip.escape(marker)).append("</span>").append(content.ifEmpty { "&nbsp;" })
                    .append("</p>")
                return
            }
            if (p.indentTwips > 0) css.append("margin-left:").append(twipsToPx(p.indentTwips)).append("px;")
            if (p.firstLineTwips != 0) css.append("text-indent:").append(twipsToPx(p.firstLineTwips)).append("px;")
            val (open, close) = when {
                isTitle -> "h1 class=\"title\"" to "h1"
                heading != null -> "h$heading" to "h$heading"
                else -> "p" to "p"
            }
            // Empty paragraphs are how Word users space things out; dropping them squashes the page.
            out.append('<').append(open).append(" style=\"").append(css).append("\">")
                .append(content.ifBlank { "&nbsp;" }).append("</").append(close).append('>')
        }

        /** "1.", "a)", "iv.", "•" … for this paragraph, advancing the list's counters. */
        private fun listMarker(numId: String?, ilvl: Int, styleName: String): String? {
            if (numId == null || numId == "0") {
                return if (styleName.startsWith("List Bullet", true) || styleName.startsWith("List Number", true)) {
                    "•"
                } else {
                    null
                }
            }
            val level = numbering.level(numId, ilvl)
            val counts = counters.getOrPut(numId) { IntArray(MAX_LEVELS) }
            val index = ilvl.coerceIn(0, MAX_LEVELS - 1)
            if (counts[index] == 0) counts[index] = (level?.start ?: 1) - 1
            counts[index]++
            for (deeper in index + 1 until MAX_LEVELS) counts[deeper] = 0
            if (level == null || level.format == "bullet") return BULLETS[index % BULLETS.size]
            if (level.format == "none") return ""
            // lvlText is a template like "%1.%2." — each %n is that level's counter in its own format.
            return LEVEL_REF.replace(level.text) { match ->
                val ref = match.groupValues[1].toInt() - 1
                val refFormat = numbering.level(numId, ref)?.format ?: level.format
                formatNumber(counts.getOrElse(ref) { 1 }.coerceAtLeast(1), refFormat)
            }
        }

        /** One run with its formatting; an inline image is emitted where it sits. */
        private fun textRun(parser: XmlPullParser, paragraphFormat: RunFormat): Run {
            var format = RunFormat()
            var runStyle: String? = null
            val text = StringBuilder()
            var pageBreak = false
            var depth = 1
            var imageWidthPx: Int? = null
            while (depth > 0 && parser.next() != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> {
                        depth++
                        when (parser.name) {
                            "w:rStyle" -> runStyle = parser.getAttributeValue(null, "w:val")
                            "w:t" -> {
                                text.append(OoxmlZip.escape(parser.nextText()))
                                depth--
                            }
                            "w:br" -> {
                                if (parser.getAttributeValue(null, "w:type") == "page") pageBreak = true
                                else text.append("<br>")
                            }
                            "w:cr" -> text.append("<br>")
                            "w:tab" -> text.append("&emsp;")
                            "w:noBreakHyphen" -> text.append("&#8209;")
                            "wp:extent" -> imageWidthPx = parser.getAttributeValue(null, "cx")?.toLongOrNull()
                                ?.let { (it / EMU_PER_PX).toInt() }
                            "a:blip" -> imageTag(parser.getAttributeValue(null, "r:embed"), imageWidthPx)
                                ?.let { text.append(it) }
                            "v:imagedata" -> imageTag(parser.getAttributeValue(null, "r:id"), null)
                                ?.let { text.append(it) }
                            else -> format = readRunProperty(parser, format)
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        depth--
                        if (parser.name == "w:r") break
                    }
                }
            }
            val html = text.toString()
            if (html.isEmpty()) return Run("", pageBreak)
            val css = paragraphFormat.overlay(styles.run(runStyle)).overlay(format).css()
            return Run(if (css.isEmpty()) html else "<span style=\"$css\">$html</span>", pageBreak)
        }

        private fun imageTag(relId: String?, widthPx: Int?): String? {
            val part = rels[relId ?: return null] ?: return null
            val file = media[part] ?: return null
            val width = widthPx?.let { " style=\"width:${it}px\"" }.orEmpty()
            return "<img src=\"$file\"$width>"
        }

        private fun table(parser: XmlPullParser) {
            out.append("<table class=\"doc-table\">")
            val cell = CellState()
            var depth = 1
            while (depth > 0 && parser.next() != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> {
                        depth++
                        when (parser.name) {
                            "w:tr" -> out.append("<tr>")
                            "w:tc" -> cell.reset()
                            "w:gridSpan" -> cell.span = parser.getAttributeValue(null, "w:val")?.toIntOrNull() ?: 1
                            "w:shd" -> if (cell.inCell && !cell.open) {
                                cell.fill = hexColor(parser.getAttributeValue(null, "w:fill"))
                            }
                            "w:p" -> {
                                openCell(cell)
                                paragraph(parser)
                                depth--
                            }
                            "w:tbl" -> {
                                openCell(cell)
                                table(parser)
                                depth--
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        depth--
                        when (parser.name) {
                            "w:tr" -> out.append("</tr>")
                            "w:tc" -> {
                                openCell(cell)
                                out.append("</td>")
                                cell.inCell = false
                            }
                            "w:tbl" -> depth = 0
                        }
                    }
                }
            }
            out.append("</table>")
        }

        private class CellState {
            var inCell = false
            var open = false
            var span = 1
            var fill: String? = null

            fun reset() {
                inCell = true
                open = false
                span = 1
                fill = null
            }
        }

        /** Cell properties come before the cell's paragraphs, so the `<td>` is written at the first one. */
        private fun openCell(cell: CellState) {
            if (!cell.inCell || cell.open) return
            out.append("<td")
            if (cell.span > 1) out.append(" colspan=\"").append(cell.span).append('"')
            cell.fill?.let { out.append(" style=\"background:#").append(it).append('"') }
            out.append('>')
            cell.open = true
        }
    }

    /** Reads one `w:rPr` child into [format]; anything else leaves it unchanged. */
    internal fun readRunProperty(parser: XmlPullParser, format: RunFormat): RunFormat {
        val value = parser.getAttributeValue(null, "w:val")
        val on = value == null || (value != "0" && value != "false" && value != "none")
        return when (parser.name) {
            "w:b" -> format.copy(bold = on)
            "w:i" -> format.copy(italic = on)
            "w:u" -> format.copy(underline = on)
            "w:strike", "w:dstrike" -> format.copy(strike = on)
            "w:color" -> format.copy(colorHex = hexColor(value) ?: format.colorHex)
            "w:sz" -> format.copy(halfPoints = value?.toIntOrNull() ?: format.halfPoints)
            "w:rFonts" -> format.copy(font = parser.getAttributeValue(null, "w:ascii") ?: format.font)
            "w:highlight" -> format.copy(highlight = HIGHLIGHTS[value] ?: format.highlight)
            "w:vertAlign" -> format.copy(vertAlign = value)
            else -> format
        }
    }

    /** "FF0000" stays, "auto" and anything malformed become null. */
    internal fun hexColor(value: String?): String? =
        value?.takeIf { it.length == 6 && it.all { c -> c.isDigit() || c.lowercaseChar() in 'a'..'f' } }

    internal fun cssAlign(jc: String?): String? = when (jc) {
        "center" -> "center"
        "right", "end" -> "right"
        "both", "distribute" -> "justify"
        "left", "start" -> "left"
        else -> null
    }

    internal fun formatNumber(n: Int, format: String): String = when (format) {
        "lowerLetter" -> letters(n).lowercase()
        "upperLetter" -> letters(n)
        "lowerRoman" -> roman(n).lowercase()
        "upperRoman" -> roman(n)
        "decimalZero" -> n.toString().padStart(2, '0')
        else -> n.toString()
    }

    private fun letters(n: Int): String {
        var value = n
        val out = StringBuilder()
        while (value > 0) {
            val r = (value - 1) % 26
            out.insert(0, 'A' + r)
            value = (value - 1) / 26
        }
        return out.toString()
    }

    internal fun roman(n: Int): String {
        if (n <= 0 || n >= 4000) return n.toString()
        var value = n
        val out = StringBuilder()
        ROMAN.forEach { (amount, symbol) ->
            while (value >= amount) {
                out.append(symbol)
                value -= amount
            }
        }
        return out.toString()
    }

    /** 1 pt = 4/3 CSS px; 1 twip = 1/20 pt. */
    internal fun ptToPx(points: Float): String = "%.1f".format(java.util.Locale.US, points * 4f / 3f)

    internal fun twipsToPx(twips: Int): Int = Math.round(twips / 15f)

    private val HEADING = Regex("(?i)heading\\s*(\\d)")
    private val LEVEL_REF = Regex("%(\\d)")
    private const val MAX_LEVELS = 9
    private const val LIST_INDENT_PX = 24
    private const val EMU_PER_PX = 9525L
    private val BULLETS = listOf("•", "◦", "▪")
    private val ROMAN = listOf(
        1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC",
        50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I",
    )
    private val HIGHLIGHTS = mapOf(
        "yellow" to "#FFFF00", "green" to "#00FF00", "cyan" to "#00FFFF", "magenta" to "#FF00FF",
        "blue" to "#0000FF", "red" to "#FF0000", "darkBlue" to "#000080", "darkCyan" to "#008080",
        "darkGreen" to "#008000", "darkMagenta" to "#800080", "darkRed" to "#800000", "darkYellow" to "#808000",
        "darkGray" to "#808080", "lightGray" to "#C0C0C0", "black" to "#000000", "white" to "#FFFFFF",
    )
}
