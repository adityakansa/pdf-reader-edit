package com.whats.web.scan.webscan.pdfreaderpdffileedit.office

import org.xmlpull.v1.XmlPullParser

/**
 * FR-033. DOCX → HTML: paragraphs, runs (bold/italic/underline), Heading 1–6, numbered and bulleted
 * lists as bullets, tables and inline images, in document order. No fidelity guarantee — the reader
 * says "Simplified view".
 */
object DocxToHtml {

    fun convert(parts: Map<String, ByteArray>, media: Map<String, String>): String {
        val body = parts["word/document.xml"] ?: return HtmlPage.wrap("")
        val rels = OoxmlZip.relationships(parts, "word/_rels/document.xml.rels", "word/")
        return HtmlPage.wrap(Writer(rels, media).convert(OoxmlZip.parser(body)))
    }

    private class Writer(
        private val rels: Map<String, String>,
        private val media: Map<String, String>,
    ) {
        private val out = StringBuilder()

        /** Open list state: DOCX has no list element, only a numbering mark on each paragraph. */
        private var inList = false

        fun convert(parser: XmlPullParser): String {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                when (parser.name) {
                    "w:p" -> paragraph(parser)
                    "w:tbl" -> table(parser)
                }
            }
            closeList()
            return out.toString()
        }

        private fun paragraph(parser: XmlPullParser) {
            var style: String? = null
            var listItem = false
            val content = StringBuilder()
            var depth = 1
            while (depth > 0 && parser.next() != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> {
                        depth++
                        when (parser.name) {
                            "w:pStyle" -> style = parser.getAttributeValue(null, "w:val")
                            "w:numPr" -> listItem = true
                            "w:r" -> {
                                content.append(textRun(parser)); depth--
                            }
                            "w:tbl" -> {
                                // A nested table inside a paragraph is rare; flush what we have and recurse.
                                flush(style, listItem, content.toString())
                                content.setLength(0)
                                table(parser)
                                depth--
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        depth--
                        if (parser.name == "w:p") break
                    }
                }
            }
            flush(style, listItem, content.toString())
        }

        private fun flush(style: String?, listItem: Boolean, content: String) {
            if (content.isBlank()) {
                if (!listItem) closeList()
                return
            }
            val heading = style?.let { HEADING.find(it)?.groupValues?.get(1)?.toIntOrNull() }
            when {
                listItem -> {
                    if (!inList) {
                        out.append("<ul>")
                        inList = true
                    }
                    out.append("<li>").append(content).append("</li>")
                }

                heading != null && heading in 1..6 -> {
                    closeList()
                    out.append("<h$heading>").append(content).append("</h$heading>")
                }

                else -> {
                    closeList()
                    out.append("<p>").append(content).append("</p>")
                }
            }
        }

        private fun closeList() {
            if (inList) {
                out.append("</ul>")
                inList = false
            }
        }

        /** One run: its formatting marks wrap its text, and an inline image is emitted where it sits. */
        private fun textRun(parser: XmlPullParser): String {
            var bold = false
            var italic = false
            var underline = false
            val text = StringBuilder()
            var depth = 1
            while (depth > 0 && parser.next() != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> {
                        depth++
                        when (parser.name) {
                            "w:b" -> bold = parser.getAttributeValue(null, "w:val") != "0"
                            "w:i" -> italic = parser.getAttributeValue(null, "w:val") != "0"
                            "w:u" -> underline = parser.getAttributeValue(null, "w:val") != "none"
                            "w:t" -> {
                                text.append(OoxmlZip.escape(parser.nextText()))
                                depth--
                            }
                            "w:br" -> text.append("<br>")
                            "w:tab" -> text.append(" ")
                            "a:blip" -> {
                                val id = parser.getAttributeValue(null, "r:embed")
                                imageTag(id)?.let(text::append)
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        depth--
                        if (parser.name == "w:r") break
                    }
                }
            }
            var html = text.toString()
            if (html.isEmpty()) return ""
            if (bold) html = "<strong>$html</strong>"
            if (italic) html = "<em>$html</em>"
            if (underline) html = "<u>$html</u>"
            return html
        }

        private fun imageTag(relId: String?): String? {
            val part = rels[relId ?: return null] ?: return null
            val file = media[part] ?: return null
            return "<img src=\"$file\">"
        }

        private fun table(parser: XmlPullParser) {
            closeList()
            out.append("<table>")
            var depth = 1
            while (depth > 0 && parser.next() != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> {
                        depth++
                        when (parser.name) {
                            "w:tr" -> out.append("<tr>")
                            "w:tc" -> out.append("<td>")
                            "w:t" -> {
                                out.append(OoxmlZip.escape(parser.nextText())); depth--
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        depth--
                        when (parser.name) {
                            "w:tr" -> out.append("</tr>")
                            "w:tc" -> out.append("</td>")
                            "w:tbl" -> depth = 0
                        }
                    }
                }
            }
            out.append("</table>")
        }

        private companion object {
            val HEADING = Regex("(?i)heading\\s*(\\d)")
        }
    }
}
