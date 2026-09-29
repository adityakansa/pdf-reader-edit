package com.whats.web.scan.webscan.pdfreaderpdffileedit.office

import org.xmlpull.v1.XmlPullParser

/** FR-035. PPTX → HTML: one card per slide, in slide order, with its text frames and pictures. */
object PptxToHtml {

    fun convert(parts: Map<String, ByteArray>, media: Map<String, String>): String {
        val slides = parts.keys
            .filter { it.startsWith("ppt/slides/slide") && it.endsWith(".xml") }
            .sortedBy { it.substringAfterLast("slide").substringBefore(".xml").toIntOrNull() ?: 0 }

        val body = StringBuilder()
        slides.forEachIndexed { index, path ->
            val rels = OoxmlZip.relationships(
                parts,
                "ppt/slides/_rels/${path.substringAfterLast('/')}.rels",
                "ppt/slides/",
            )
            body.append("<div class=\"slide\">")
            body.append("<div class=\"slide-number\">").append(index + 1).append("</div>")
            body.append(slide(OoxmlZip.parser(parts.getValue(path)), rels, media))
            body.append("</div>")
        }
        return HtmlPage.wrap(body.toString())
    }

    private fun slide(parser: XmlPullParser, rels: Map<String, String>, media: Map<String, String>): String {
        val out = StringBuilder()
        val paragraph = StringBuilder()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "a:t" -> paragraph.append(OoxmlZip.escape(parser.nextText()))

                    "a:blip" -> {
                        val part = rels[parser.getAttributeValue(null, "r:embed")]
                        media[part]?.let { out.append("<img src=\"").append(it).append("\">") }
                    }
                }

                XmlPullParser.END_TAG -> if (parser.name == "a:p") {
                    if (paragraph.isNotBlank()) out.append("<p>").append(paragraph).append("</p>")
                    paragraph.setLength(0)
                }
            }
        }
        if (paragraph.isNotBlank()) out.append("<p>").append(paragraph).append("</p>")
        return out.toString()
    }
}
