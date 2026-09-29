package com.whats.web.scan.webscan.pdfreaderpdffileedit.office

import org.xmlpull.v1.XmlPullParser

/**
 * FR-035. PPTX → HTML slides that look like slides: each one keeps the presentation's aspect ratio, and
 * every text box, shape and picture is placed at its real position and size (as a percentage of the
 * slide, so it scales to any phone). Placeholders without their own position inherit it from the slide
 * layout, then the master. Text keeps size (scaled with the slide via container units), bold / italic /
 * underline, colour (including theme colours), alignment and bullets; shapes keep solid fills, outlines
 * and ellipses; slide backgrounds keep a solid colour.
 *
 * Not attempted: gradients, shadows, rotation, charts, SmartArt, animations, embedded video.
 */
object PptxToHtml {

    private const val DEFAULT_WIDTH_EMU = 12_192_000L
    private const val DEFAULT_HEIGHT_EMU = 6_858_000L
    private const val EMU_PER_POINT = 12_700f

    fun convert(parts: Map<String, ByteArray>, media: Map<String, String>): String {
        val (slideWidth, slideHeight) = slideSize(parts)
        val theme = parts.keys.firstOrNull { it.startsWith("ppt/theme/theme") }?.let { Theme.parse(OoxmlZip.parser(parts.getValue(it))) }
            ?: Theme.DEFAULT
        val slides = parts.keys
            .filter { it.startsWith("ppt/slides/slide") && it.endsWith(".xml") }
            .sortedBy { it.substringAfterLast("slide").substringBefore(".xml").toIntOrNull() ?: 0 }

        val context = Context(slideWidth, slideHeight, theme, media)
        val body = StringBuilder()
        slides.forEachIndexed { index, path ->
            val rels = relsOf(parts, path)
            val layoutPath = rels.values.firstOrNull { it.contains("slideLayouts/") }
            val layoutRels = layoutPath?.let { relsOf(parts, it) }.orEmpty()
            val masterPath = layoutRels.values.firstOrNull { it.contains("slideMasters/") }
            val inherited = Placeholders()
            masterPath?.let { parts[it] }?.let { inherited.readFrom(OoxmlZip.parser(it), slideWidth, slideHeight) }
            layoutPath?.let { parts[it] }?.let { inherited.readFrom(OoxmlZip.parser(it), slideWidth, slideHeight) }

            val slide = SlideWriter(context, rels, inherited).write(OoxmlZip.parser(parts.getValue(path)))
            val ratio = "%.4f".format(java.util.Locale.US, slideHeight.toFloat() / slideWidth * 100f)
            body.append("<div class=\"slide-number\">").append(index + 1).append(" / ").append(slides.size).append("</div>")
            body.append("<div class=\"deck-slide\" style=\"padding-top:").append(ratio).append("%;")
                .append(slide.background?.let { "background:#$it;" }.orEmpty()).append("\">")
                .append(slide.html).append("</div>")
        }
        return HtmlPage.wrap(body.toString(), bodyClass = "deck")
    }

    private fun relsOf(parts: Map<String, ByteArray>, path: String): Map<String, String> {
        val folder = path.substringBeforeLast('/') + "/"
        return OoxmlZip.relationships(parts, "${folder}_rels/${path.substringAfterLast('/')}.rels", folder)
    }

    private fun slideSize(parts: Map<String, ByteArray>): Pair<Long, Long> {
        val bytes = parts["ppt/presentation.xml"] ?: return DEFAULT_WIDTH_EMU to DEFAULT_HEIGHT_EMU
        val parser = OoxmlZip.parser(bytes)
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "p:sldSz") {
                val cx = parser.getAttributeValue(null, "cx")?.toLongOrNull()
                val cy = parser.getAttributeValue(null, "cy")?.toLongOrNull()
                if (cx != null && cy != null && cx > 0 && cy > 0) return cx to cy
            }
        }
        return DEFAULT_WIDTH_EMU to DEFAULT_HEIGHT_EMU
    }

    private class Context(val width: Long, val height: Long, val theme: Theme, val media: Map<String, String>) {
        val widthPoints: Float get() = width / EMU_PER_POINT
    }

    /** A rectangle in slide EMUs. */
    internal data class Box(val x: Long, val y: Long, val cx: Long, val cy: Long)

    /** Theme colour names → hex, for `a:schemeClr`. */
    internal class Theme(private val colors: Map<String, String>) {
        fun color(name: String?): String? = when (name) {
            "tx1" -> colors["dk1"]
            "bg1" -> colors["lt1"]
            "tx2" -> colors["dk2"]
            "bg2" -> colors["lt2"]
            null -> null
            else -> colors[name]
        }

        companion object {
            val DEFAULT = Theme(mapOf("dk1" to "000000", "lt1" to "FFFFFF", "dk2" to "44546A", "lt2" to "E7E6E6", "accent1" to "4472C4"))

            fun parse(parser: XmlPullParser): Theme {
                val map = mutableMapOf<String, String>()
                var current: String? = null
                var inScheme = false
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    when (parser.eventType) {
                        XmlPullParser.START_TAG -> when {
                            parser.name == "a:clrScheme" -> inScheme = true
                            inScheme && parser.name.startsWith("a:") && parser.name.substring(2) in SCHEME_SLOTS ->
                                current = parser.name.substring(2)
                            inScheme && parser.name == "a:srgbClr" -> current?.let { map[it] = parser.getAttributeValue(null, "val") ?: "000000" }
                            inScheme && parser.name == "a:sysClr" ->
                                current?.let { map[it] = parser.getAttributeValue(null, "lastClr") ?: "000000" }
                        }
                        XmlPullParser.END_TAG -> if (parser.name == "a:clrScheme") return Theme(map)
                    }
                }
                return if (map.isEmpty()) DEFAULT else Theme(map)
            }

            private val SCHEME_SLOTS = setOf(
                "dk1", "lt1", "dk2", "lt2", "accent1", "accent2", "accent3", "accent4", "accent5", "accent6", "hlink", "folHlink",
            )
        }
    }

    /** What a slide inherits for a placeholder: where it sits and how its text looks by default. */
    private data class Inherited(val box: Box?, val align: String?, val anchor: String?, val size: Float?) {
        fun overlay(other: Inherited) = Inherited(
            other.box ?: box, other.align ?: align, other.anchor ?: anchor, other.size ?: size,
        )
    }

    /** Placeholder defaults from the master and layout, by placeholder type and by index. */
    private class Placeholders {
        private val byType = mutableMapOf<String, Inherited>()
        private val byIndex = mutableMapOf<String, Inherited>()

        /** The master's `p:txStyles`: default title and body text. */
        private var titleStyle = Inherited(null, null, null, null)
        private var bodyStyle = Inherited(null, null, null, null)

        fun find(type: String?, idx: String?): Inherited {
            val base = if (normalise(type) == "title") titleStyle else bodyStyle
            val specific = idx?.let { byIndex[it] } ?: byType[normalise(type)]
                ?: if (type == null && idx != null) byType["body"] else null
            return specific?.let { base.overlay(it) } ?: base
        }

        fun readFrom(parser: XmlPullParser, width: Long, height: Long) {
            var type: String? = null
            var idx: String? = null
            var inShape = false
            var box: Box? = null
            var off: Pair<Long, Long>? = null
            var align: String? = null
            var anchor: String? = null
            var size: Float? = null
            var inLevel1 = false
            var txStyle: String? = null
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "p:titleStyle", "p:bodyStyle" -> {
                            txStyle = parser.name
                            align = null
                            size = null
                        }
                        "a:lvl1pPr" -> {
                            inLevel1 = true
                            if (inShape || txStyle != null) align = parser.getAttributeValue(null, "algn") ?: align
                        }
                        "a:defRPr" -> if (inLevel1) {
                            size = parser.getAttributeValue(null, "sz")?.toFloatOrNull()?.div(100f) ?: size
                        }
                        "a:bodyPr" -> if (inShape) anchor = parser.getAttributeValue(null, "anchor") ?: anchor
                        "p:sp" -> {
                            inShape = true
                            type = null
                            idx = null
                            box = null
                            off = null
                            align = null
                            anchor = null
                            size = null
                        }
                        "p:ph" -> if (inShape) {
                            type = parser.getAttributeValue(null, "type") ?: "body"
                            idx = parser.getAttributeValue(null, "idx")
                        }
                        "a:off" -> if (inShape && box == null) {
                            off = (parser.getAttributeValue(null, "x")?.toLongOrNull() ?: 0L) to
                                (parser.getAttributeValue(null, "y")?.toLongOrNull() ?: 0L)
                        }
                        "a:ext" -> if (inShape && box == null) {
                            val o = off
                            val cx = parser.getAttributeValue(null, "cx")?.toLongOrNull()
                            val cy = parser.getAttributeValue(null, "cy")?.toLongOrNull()
                            if (o != null && cx != null && cy != null) box = Box(o.first, o.second, cx, cy)
                        }
                    }
                    XmlPullParser.END_TAG -> when (parser.name) {
                        "a:lvl1pPr" -> inLevel1 = false
                        "p:titleStyle" -> {
                            titleStyle = titleStyle.overlay(Inherited(null, align, null, size))
                            txStyle = null
                        }
                        "p:bodyStyle" -> {
                            bodyStyle = bodyStyle.overlay(Inherited(null, align, null, size))
                            txStyle = null
                        }
                        "p:sp" -> {
                            val t = type
                            if (inShape && t != null) {
                                val info = Inherited(box, align, anchor, size)
                                val key = normalise(t)
                                byType[key] = byType[key]?.overlay(info) ?: info
                                idx?.let { i -> byIndex[i] = byIndex[i]?.overlay(info) ?: info }
                            }
                            inShape = false
                        }
                    }
                }
            }
            if (byType.isEmpty()) {
                // No layout at all: the usual Office title/body areas.
                byType["title"] = Inherited(Box(width / 12, height / 20, width * 10 / 12, height / 5), null, null, null)
                byType["body"] = Inherited(Box(width / 12, height * 3 / 10, width * 10 / 12, height * 6 / 10), null, null, null)
            }
        }

        private fun normalise(type: String?) = when (type) {
            "ctrTitle" -> "title"
            "subTitle" -> "body"
            null -> "body"
            else -> type
        }
    }

    private class SlideResult(val html: String, val background: String?)

    private class SlideWriter(
        private val context: Context,
        private val rels: Map<String, String>,
        private val inherited: Placeholders,
    ) {
        private val out = StringBuilder()
        private var background: String? = null

        fun write(parser: XmlPullParser): SlideResult {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                when (parser.name) {
                    "p:bg" -> background = readBackground(parser)
                    "p:sp" -> shape(parser)
                    "p:pic" -> picture(parser)
                }
            }
            return SlideResult(out.toString(), background)
        }

        private fun readBackground(parser: XmlPullParser): String? {
            var depth = 1
            var color: String? = null
            while (depth > 0 && parser.next() != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> {
                        depth++
                        if (color == null) color = colorOf(parser)
                    }
                    XmlPullParser.END_TAG -> depth--
                }
            }
            return color
        }

        /** A shape: its box, fill and outline, and its text body (paragraphs of runs). */
        private fun shape(parser: XmlPullParser) {
            var depth = 1
            var phType: String? = null
            var phIdx: String? = null
            var isPlaceholder = false
            var off: Pair<Long, Long>? = null
            var box: Box? = null
            var inSpPr = false
            var inLine = false
            var inText = false
            var fill: String? = null
            var line: String? = null
            var ellipse = false
            var anchor: String? = null
            val text = StringBuilder()
            var paragraph = StringBuilder()
            var paragraphAlign: String? = null
            var bullet: String? = null
            var run: RunStyle? = null
            var inRPr = false
            var defaultSize: Float? = null
            var fontScale = 1f
            // Known once p:ph is read, which comes before the text: the layout/master defaults for it.
            var shapeDefaults = Inherited(null, null, null, null)
            while (depth > 0 && parser.next() != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> {
                        depth++
                        when (parser.name) {
                            "p:ph" -> {
                                isPlaceholder = true
                                phType = parser.getAttributeValue(null, "type")
                                phIdx = parser.getAttributeValue(null, "idx")
                                shapeDefaults = inherited.find(phType, phIdx)
                            }
                            "p:spPr" -> inSpPr = true
                            "a:off" -> if (inSpPr && box == null) {
                                off = (parser.getAttributeValue(null, "x")?.toLongOrNull() ?: 0L) to
                                    (parser.getAttributeValue(null, "y")?.toLongOrNull() ?: 0L)
                            }
                            "a:ext" -> if (inSpPr && box == null) {
                                val o = off
                                val cx = parser.getAttributeValue(null, "cx")?.toLongOrNull()
                                val cy = parser.getAttributeValue(null, "cy")?.toLongOrNull()
                                if (o != null && cx != null && cy != null) box = Box(o.first, o.second, cx, cy)
                            }
                            "a:prstGeom" -> if (parser.getAttributeValue(null, "prst") == "ellipse") ellipse = true
                            "a:ln" -> if (inSpPr) inLine = true
                            "a:noFill" -> if (inSpPr && !inLine) fill = null
                            "p:txBody" -> inText = true
                            "a:bodyPr" -> anchor = parser.getAttributeValue(null, "anchor") ?: anchor
                            // "Shrink text on overflow": PowerPoint stores the scale it applied, in 1/1000 %.
                            "a:normAutofit" -> fontScale = parser.getAttributeValue(null, "fontScale")
                                ?.toFloatOrNull()?.div(100_000f) ?: fontScale
                            "a:p" -> {
                                paragraph = StringBuilder()
                                paragraphAlign = null
                                bullet = null
                            }
                            "a:pPr" -> paragraphAlign = parser.getAttributeValue(null, "algn")
                            "a:buChar" -> bullet = parser.getAttributeValue(null, "char") ?: "•"
                            "a:buAutoNum" -> bullet = "•"
                            "a:r", "a:fld" -> run = RunStyle()
                            "a:rPr", "a:endParaRPr" -> {
                                inRPr = parser.name == "a:rPr"
                                val size = parser.getAttributeValue(null, "sz")?.toFloatOrNull()?.div(100f)
                                if (inRPr) {
                                    run = (run ?: RunStyle()).copy(
                                        size = size,
                                        bold = parser.getAttributeValue(null, "b") == "1",
                                        italic = parser.getAttributeValue(null, "i") == "1",
                                        underline = parser.getAttributeValue(null, "u").let { it != null && it != "none" },
                                    )
                                } else if (size != null) {
                                    defaultSize = size
                                }
                            }
                            "a:t" -> {
                                val style = run ?: RunStyle()
                                paragraph.append(style.span(OoxmlZip.escape(parser.nextText()), context))
                                depth--
                            }
                            "a:br" -> paragraph.append("<br>")
                            else -> {
                                val color = colorOf(parser)
                                if (color != null) {
                                    when {
                                        inRPr -> run = (run ?: RunStyle()).copy(color = color)
                                        inLine -> line = color
                                        inSpPr && !inText -> fill = color
                                    }
                                }
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        depth--
                        when (parser.name) {
                            "p:spPr" -> inSpPr = false
                            "a:ln" -> inLine = false
                            "a:rPr" -> inRPr = false
                            "a:r", "a:fld" -> run = null
                            "a:p" -> {
                                val align = when (paragraphAlign ?: shapeDefaults.align) {
                                    "ctr" -> "center"
                                    "r" -> "right"
                                    "just" -> "justify"
                                    else -> null
                                }
                                text.append("<p").append(align?.let { " style=\"text-align:$it\"" }.orEmpty()).append('>')
                                if (bullet != null && paragraph.isNotEmpty()) {
                                    text.append("<span class=\"bullet\">").append(OoxmlZip.escape(bullet ?: "•")).append("</span>")
                                }
                                text.append(if (paragraph.isEmpty()) "&nbsp;" else paragraph).append("</p>")
                            }
                            "p:sp" -> depth = 0
                        }
                    }
                }
            }
            val place = box ?: if (isPlaceholder) shapeDefaults.box else null
            place ?: return
            val isTitle = phType == "title" || phType == "ctrTitle"
            val baseSize = defaultSize ?: shapeDefaults.size ?: when {
                isTitle -> 40f
                isPlaceholder -> 24f
                else -> 18f
            }
            val justify = when (anchor ?: shapeDefaults.anchor ?: if (isTitle) "ctr" else "t") {
                "ctr" -> "center"
                "b" -> "flex-end"
                else -> "flex-start"
            }
            out.append("<div class=\"shape\" style=\"").append(position(place))
                .append("justify-content:").append(justify).append(';')
                .append("font-size:").append(fontSize(baseSize * fontScale)).append(';')
                .append("--scale:").append(fontScale).append(';')
            if (isTitle) out.append("font-weight:600;")
            fill?.let { out.append("background:#").append(it).append(';') }
            line?.let { out.append("border:0.3cqw solid #").append(it).append(';') }
            if (ellipse) out.append("border-radius:50%;")
            out.append("\">").append(text).append("</div>")
        }

        private fun picture(parser: XmlPullParser) {
            var depth = 1
            var off: Pair<Long, Long>? = null
            var box: Box? = null
            var file: String? = null
            while (depth > 0 && parser.next() != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> {
                        depth++
                        when (parser.name) {
                            "a:blip" -> file = rels[parser.getAttributeValue(null, "r:embed")]?.let { context.media[it] }
                            "a:off" -> if (box == null) {
                                off = (parser.getAttributeValue(null, "x")?.toLongOrNull() ?: 0L) to
                                    (parser.getAttributeValue(null, "y")?.toLongOrNull() ?: 0L)
                            }
                            "a:ext" -> if (box == null) {
                                val o = off
                                val cx = parser.getAttributeValue(null, "cx")?.toLongOrNull()
                                val cy = parser.getAttributeValue(null, "cy")?.toLongOrNull()
                                if (o != null && cx != null && cy != null) box = Box(o.first, o.second, cx, cy)
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        depth--
                        if (parser.name == "p:pic") depth = 0
                    }
                }
            }
            val place = box ?: return
            val src = file ?: return
            out.append("<img class=\"shape-img\" src=\"").append(src).append("\" style=\"").append(position(place)).append("\">")
        }

        private fun position(box: Box): String {
            fun pct(value: Long, total: Long) = "%.3f%%".format(java.util.Locale.US, value * 100f / total)
            return "left:${pct(box.x, context.width)};top:${pct(box.y, context.height)};" +
                "width:${pct(box.cx, context.width)};height:${pct(box.cy, context.height)};"
        }

        /** A point size as a share of the slide width, so text scales exactly with the slide. */
        fun fontSize(points: Float): String = "%.3fcqw".format(java.util.Locale.US, points / context.widthPoints * 100f)

        /** `a:srgbClr` / `a:schemeClr` / `a:sysClr` under the current element, as hex. */
        private fun colorOf(parser: XmlPullParser): String? = when (parser.name) {
            "a:srgbClr" -> parser.getAttributeValue(null, "val")
            "a:schemeClr" -> context.theme.color(parser.getAttributeValue(null, "val"))
            "a:sysClr" -> parser.getAttributeValue(null, "lastClr")
            else -> null
        }

        private data class RunStyle(
            val size: Float? = null,
            val bold: Boolean = false,
            val italic: Boolean = false,
            val underline: Boolean = false,
            val color: String? = null,
        ) {
            fun span(text: String, context: Context): String {
                val css = buildString {
                    size?.let {
                        append("font-size:calc(var(--scale,1) * ")
                            .append("%.3fcqw".format(java.util.Locale.US, it / context.widthPoints * 100f)).append(");")
                    }
                    if (bold) append("font-weight:bold;")
                    if (italic) append("font-style:italic;")
                    if (underline) append("text-decoration:underline;")
                    color?.let { append("color:#").append(it).append(';') }
                }
                return if (css.isEmpty()) text else "<span style=\"$css\">$text</span>"
            }
        }
    }
}
