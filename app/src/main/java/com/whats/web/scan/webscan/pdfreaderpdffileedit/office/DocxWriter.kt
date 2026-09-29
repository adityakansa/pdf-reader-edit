package com.whats.web.scan.webscan.pdfreaderpdffileedit.office

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes a small, valid Word document (.docx) from [SimpleMarkup] paragraphs: Title-less body with
 * Heading 1/2, bullets and numbering (real Word lists via numbering.xml), bold and italic runs.
 * Opens in Word, Google Docs, LibreOffice and this app's viewer.
 */
object DocxWriter {

    fun write(paragraphs: List<SimpleMarkup.Paragraph>, title: String, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            fun put(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("[Content_Types].xml", CONTENT_TYPES)
            put("_rels/.rels", ROOT_RELS)
            put("word/_rels/document.xml.rels", DOCUMENT_RELS)
            put("word/styles.xml", STYLES)
            val parts = parts(paragraphs)
            put("word/numbering.xml", parts.numbering)
            put("docProps/core.xml", core(title))
            put("word/document.xml", parts.document)
        }
    }

    /** document.xml and the numbering.xml it refers to (one bullet list, one instance per numbered list). */
    internal class Parts(val document: String, val numbering: String)

    internal fun parts(paragraphs: List<SimpleMarkup.Paragraph>): Parts {
        val body = StringBuilder()
        var listInstance = 1
        var previous: SimpleMarkup.Kind? = null
        paragraphs.forEach { p ->
            // A numbered list that starts again after other text restarts at 1: a new w:num instance.
            if (p.kind == SimpleMarkup.Kind.NUMBERED && previous != SimpleMarkup.Kind.NUMBERED) listInstance++
            body.append("<w:p><w:pPr>")
            when (p.kind) {
                SimpleMarkup.Kind.HEADING1 -> body.append("<w:pStyle w:val=\"Heading1\"/>")
                SimpleMarkup.Kind.HEADING2 -> body.append("<w:pStyle w:val=\"Heading2\"/>")
                SimpleMarkup.Kind.BULLET -> body.append("<w:pStyle w:val=\"ListParagraph\"/><w:numPr><w:ilvl w:val=\"0\"/><w:numId w:val=\"1\"/></w:numPr>")
                SimpleMarkup.Kind.NUMBERED -> body.append("<w:pStyle w:val=\"ListParagraph\"/><w:numPr><w:ilvl w:val=\"0\"/><w:numId w:val=\"$listInstance\"/></w:numPr>")
                SimpleMarkup.Kind.NORMAL -> Unit
            }
            body.append("</w:pPr>")
            p.runs.forEach { run ->
                body.append("<w:r>")
                if (run.bold || run.italic) {
                    body.append("<w:rPr>")
                    if (run.bold) body.append("<w:b/>")
                    if (run.italic) body.append("<w:i/>")
                    body.append("</w:rPr>")
                }
                body.append("<w:t xml:space=\"preserve\">").append(xml(run.text)).append("</w:t></w:r>")
            }
            body.append("</w:p>")
            previous = p.kind
        }
        val numbers = (2..listInstance).joinToString("") {
            "<w:num w:numId=\"$it\"><w:abstractNumId w:val=\"1\"/><w:lvlOverride w:ilvl=\"0\"><w:startOverride w:val=\"1\"/></w:lvlOverride></w:num>"
        }
        val numbering = NUMBERING.replace("</w:numbering>", "$numbers</w:numbering>")
        val document = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<w:document xmlns:w=\"$W\"><w:body>$body" +
            "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/>" +
            "<w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\" w:header=\"708\" w:footer=\"708\" w:gutter=\"0\"/>" +
            "</w:sectPr></w:body></w:document>"
        return Parts(document, numbering)
    }

    private fun core(title: String) = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
        "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" " +
        "xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:title>${xml(title)}</dc:title></cp:coreProperties>"

    internal fun xml(text: String): String = buildString(text.length) {
        text.forEach { c ->
            when {
                c == '&' -> append("&amp;")
                c == '<' -> append("&lt;")
                c == '>' -> append("&gt;")
                c == '"' -> append("&quot;")
                // XML 1.0 forbids most control characters; drop them rather than write a broken file.
                c < ' ' && c != '\t' -> Unit
                else -> append(c)
            }
        }
    }

    private const val W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"

    private const val CONTENT_TYPES = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
        "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
        "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
        "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
        "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
        "<Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>" +
        "<Override PartName=\"/word/numbering.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml\"/>" +
        "<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>" +
        "</Types>"

    private const val ROOT_RELS = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>" +
        "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/>" +
        "</Relationships>"

    private const val DOCUMENT_RELS = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" +
        "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering\" Target=\"numbering.xml\"/>" +
        "</Relationships>"

    private const val STYLES = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
        "<w:styles xmlns:w=\"$W\">" +
        "<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\" w:cs=\"Calibri\"/>" +
        "<w:sz w:val=\"22\"/></w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:after=\"160\" w:line=\"259\" w:lineRule=\"auto\"/></w:pPr></w:pPrDefault></w:docDefaults>" +
        "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/></w:style>" +
        "<w:style w:type=\"paragraph\" w:styleId=\"Heading1\"><w:name w:val=\"heading 1\"/><w:basedOn w:val=\"Normal\"/>" +
        "<w:next w:val=\"Normal\"/><w:pPr><w:keepNext/><w:spacing w:before=\"240\" w:after=\"120\"/><w:outlineLvl w:val=\"0\"/></w:pPr>" +
        "<w:rPr><w:b/><w:color w:val=\"1F3864\"/><w:sz w:val=\"32\"/></w:rPr></w:style>" +
        "<w:style w:type=\"paragraph\" w:styleId=\"Heading2\"><w:name w:val=\"heading 2\"/><w:basedOn w:val=\"Normal\"/>" +
        "<w:next w:val=\"Normal\"/><w:pPr><w:keepNext/><w:spacing w:before=\"200\" w:after=\"80\"/><w:outlineLvl w:val=\"1\"/></w:pPr>" +
        "<w:rPr><w:b/><w:color w:val=\"2F5496\"/><w:sz w:val=\"26\"/></w:rPr></w:style>" +
        "<w:style w:type=\"paragraph\" w:styleId=\"ListParagraph\"><w:name w:val=\"List Paragraph\"/><w:basedOn w:val=\"Normal\"/>" +
        "<w:pPr><w:spacing w:after=\"60\"/><w:ind w:left=\"720\"/></w:pPr></w:style>" +
        "</w:styles>"

    private const val NUMBERING = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
        "<w:numbering xmlns:w=\"$W\">" +
        "<w:abstractNum w:abstractNumId=\"0\"><w:multiLevelType w:val=\"singleLevel\"/>" +
        "<w:lvl w:ilvl=\"0\"><w:start w:val=\"1\"/><w:numFmt w:val=\"bullet\"/><w:lvlText w:val=\"•\"/><w:lvlJc w:val=\"left\"/>" +
        "<w:pPr><w:ind w:left=\"720\" w:hanging=\"360\"/></w:pPr></w:lvl></w:abstractNum>" +
        "<w:abstractNum w:abstractNumId=\"1\"><w:multiLevelType w:val=\"singleLevel\"/>" +
        "<w:lvl w:ilvl=\"0\"><w:start w:val=\"1\"/><w:numFmt w:val=\"decimal\"/><w:lvlText w:val=\"%1.\"/><w:lvlJc w:val=\"left\"/>" +
        "<w:pPr><w:ind w:left=\"720\" w:hanging=\"360\"/></w:pPr></w:lvl></w:abstractNum>" +
        "<w:num w:numId=\"1\"><w:abstractNumId w:val=\"0\"/></w:num>" +
        "</w:numbering>"
}
