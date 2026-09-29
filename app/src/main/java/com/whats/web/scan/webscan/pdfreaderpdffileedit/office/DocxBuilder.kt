package com.whats.web.scan.webscan.pdfreaderpdffileedit.office

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** One piece of a Word document built from a PDF. */
sealed interface WordBlock {
    data class Paragraph(
        val runs: List<WordRun>,
        /** 0 for body text, 1 or 2 for headings. */
        val heading: Int = 0,
        val centered: Boolean = false,
        /** Extra space above, in points, to keep the gaps the PDF had between blocks. */
        val spaceBefore: Float = 0f,
    ) : WordBlock

    /** Rows of cells; every row has the same number of cells. */
    data class Table(val rows: List<List<String>>) : WordBlock

    /** A picture, e.g. a scanned page. Size in points. */
    class Picture(val bytes: ByteArray, val png: Boolean, val widthPt: Float, val heightPt: Float) : WordBlock

    data object PageBreak : WordBlock
}

data class WordRun(val text: String, val bold: Boolean = false, val italic: Boolean = false, val sizePt: Float = 0f)

/**
 * Step 12c. Writes a .docx from [WordBlock]s: paragraphs with bold, italic and point sizes, headings,
 * tables, inline pictures and page breaks, on a page the size of the PDF's first page. Opens in Word,
 * Google Docs, LibreOffice and this app's own viewer.
 */
object DocxBuilder {

    fun write(blocks: List<WordBlock>, title: String, pageWidthPt: Float, pageHeightPt: Float, out: OutputStream) {
        val pictures = blocks.filterIsInstance<WordBlock.Picture>()
        ZipOutputStream(out).use { zip ->
            fun put(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            fun put(name: String, text: String) = put(name, text.toByteArray(Charsets.UTF_8))
            put("[Content_Types].xml", CONTENT_TYPES)
            put("_rels/.rels", ROOT_RELS)
            put("word/_rels/document.xml.rels", relationships(pictures))
            put("word/styles.xml", STYLES)
            put("docProps/core.xml", core(title))
            pictures.forEachIndexed { i, p -> put("word/media/${mediaName(i, p)}", p.bytes) }
            put("word/document.xml", document(blocks, pictures, pageWidthPt, pageHeightPt))
        }
    }

    internal fun document(
        blocks: List<WordBlock>,
        pictures: List<WordBlock.Picture>,
        pageWidthPt: Float,
        pageHeightPt: Float,
    ): String {
        val margin = MARGIN_PT
        val usableWidth = (pageWidthPt - 2 * margin).coerceAtLeast(72f)
        val usableHeight = (pageHeightPt - 2 * margin).coerceAtLeast(72f)
        val body = StringBuilder()
        blocks.forEach { block ->
            when (block) {
                is WordBlock.Paragraph -> paragraph(body, block)
                is WordBlock.Table -> table(body, block, usableWidth)
                is WordBlock.Picture -> picture(body, block, pictures.indexOf(block), usableWidth, usableHeight)
                WordBlock.PageBreak -> body.append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>")
            }
        }
        val sect = "<w:sectPr><w:pgSz w:w=\"${twips(pageWidthPt)}\" w:h=\"${twips(pageHeightPt)}\"" +
            (if (pageWidthPt > pageHeightPt) " w:orient=\"landscape\"" else "") + "/>" +
            "<w:pgMar w:top=\"${twips(margin)}\" w:right=\"${twips(margin)}\" w:bottom=\"${twips(margin)}\" " +
            "w:left=\"${twips(margin)}\" w:header=\"0\" w:footer=\"0\" w:gutter=\"0\"/></w:sectPr>"
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<w:document $NAMESPACES><w:body>$body$sect</w:body></w:document>"
    }

    private fun paragraph(body: StringBuilder, p: WordBlock.Paragraph) {
        body.append("<w:p><w:pPr>")
        if (p.heading in 1..2) body.append("<w:pStyle w:val=\"Heading${p.heading}\"/>")
        if (p.spaceBefore > 0f) body.append("<w:spacing w:before=\"${twips(p.spaceBefore)}\"/>")
        if (p.centered) body.append("<w:jc w:val=\"center\"/>")
        body.append("</w:pPr>")
        p.runs.forEach { run ->
            if (run.text.isEmpty()) return@forEach
            body.append("<w:r>")
            if (run.bold || run.italic || run.sizePt > 0f) {
                body.append("<w:rPr>")
                if (run.bold) body.append("<w:b/>")
                if (run.italic) body.append("<w:i/>")
                if (run.sizePt > 0f) {
                    val halfPoints = (run.sizePt * 2).toInt().coerceIn(2, MAX_HALF_POINTS)
                    body.append("<w:sz w:val=\"$halfPoints\"/><w:szCs w:val=\"$halfPoints\"/>")
                }
                body.append("</w:rPr>")
            }
            // Tabs separate columns the PDF laid side by side; Word needs them as <w:tab/>.
            run.text.split('\t').forEachIndexed { i, piece ->
                if (i > 0) body.append("<w:tab/>")
                if (piece.isNotEmpty()) body.append("<w:t xml:space=\"preserve\">").append(DocxWriter.xml(piece)).append("</w:t>")
            }
            body.append("</w:r>")
        }
        body.append("</w:p>")
    }

    private fun table(body: StringBuilder, t: WordBlock.Table, usableWidth: Float) {
        val columns = t.rows.maxOfOrNull { it.size } ?: return
        if (columns == 0) return
        val cellWidth = twips(usableWidth / columns)
        body.append("<w:tbl><w:tblPr><w:tblStyle w:val=\"TableGrid\"/><w:tblW w:w=\"0\" w:type=\"auto\"/></w:tblPr><w:tblGrid>")
        repeat(columns) { body.append("<w:gridCol w:w=\"$cellWidth\"/>") }
        body.append("</w:tblGrid>")
        t.rows.forEach { row ->
            body.append("<w:tr>")
            for (c in 0 until columns) {
                val text = row.getOrElse(c) { "" }
                body.append("<w:tc><w:tcPr><w:tcW w:w=\"$cellWidth\" w:type=\"dxa\"/></w:tcPr><w:p>")
                if (text.isNotEmpty()) body.append("<w:r><w:t xml:space=\"preserve\">").append(DocxWriter.xml(text)).append("</w:t></w:r>")
                body.append("</w:p></w:tc>")
            }
            body.append("</w:tr>")
        }
        // Word requires a paragraph after a table before the next table or the section end.
        body.append("</w:tbl><w:p/>")
    }

    private fun picture(body: StringBuilder, p: WordBlock.Picture, index: Int, maxWidth: Float, maxHeight: Float) {
        val scale = minOf(1f, maxWidth / p.widthPt, maxHeight / p.heightPt)
        val cx = emu(p.widthPt * scale)
        val cy = emu(p.heightPt * scale)
        val id = index + 1
        body.append(
            "<w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr><w:r><w:drawing>" +
                "<wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\"><wp:extent cx=\"$cx\" cy=\"$cy\"/>" +
                "<wp:docPr id=\"$id\" name=\"Picture $id\"/>" +
                "<wp:cNvGraphicFramePr><a:graphicFrameLocks noChangeAspect=\"1\"/></wp:cNvGraphicFramePr>" +
                "<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">" +
                "<pic:pic><pic:nvPicPr><pic:cNvPr id=\"$id\" name=\"Picture $id\"/><pic:cNvPicPr/></pic:nvPicPr>" +
                "<pic:blipFill><a:blip r:embed=\"rIdImg$id\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>" +
                "<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"$cx\" cy=\"$cy\"/></a:xfrm>" +
                "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic>" +
                "</wp:inline></w:drawing></w:r></w:p>",
        )
    }

    private fun relationships(pictures: List<WordBlock.Picture>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
        append("<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>")
        pictures.forEachIndexed { i, p ->
            append("<Relationship Id=\"rIdImg${i + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/${mediaName(i, p)}\"/>")
        }
        append("</Relationships>")
    }

    private fun mediaName(index: Int, p: WordBlock.Picture) = "image${index + 1}.${if (p.png) "png" else "jpeg"}"

    private fun twips(points: Float): Int = (points * 20).toInt()

    private fun emu(points: Float): Long = (points * EMU_PER_POINT).toLong()

    private fun core(title: String) = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
        "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" " +
        "xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:title>${DocxWriter.xml(title)}</dc:title></cp:coreProperties>"

    private const val MARGIN_PT = 54f
    private const val EMU_PER_POINT = 12700f
    private const val MAX_HALF_POINTS = 400

    private const val NAMESPACES =
        "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" " +
            "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" " +
            "xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" " +
            "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" " +
            "xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\""

    private const val CONTENT_TYPES = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
        "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
        "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
        "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
        "<Default Extension=\"jpeg\" ContentType=\"image/jpeg\"/>" +
        "<Default Extension=\"png\" ContentType=\"image/png\"/>" +
        "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
        "<Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>" +
        "<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>" +
        "</Types>"

    private const val ROOT_RELS = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>" +
        "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/>" +
        "</Relationships>"

    private const val STYLES = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
        "<w:styles xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">" +
        "<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\" w:cs=\"Calibri\"/>" +
        "<w:sz w:val=\"22\"/><w:szCs w:val=\"22\"/></w:rPr></w:rPrDefault>" +
        "<w:pPrDefault><w:pPr><w:spacing w:after=\"120\" w:line=\"264\" w:lineRule=\"auto\"/></w:pPr></w:pPrDefault></w:docDefaults>" +
        "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/></w:style>" +
        "<w:style w:type=\"paragraph\" w:styleId=\"Heading1\"><w:name w:val=\"heading 1\"/><w:basedOn w:val=\"Normal\"/>" +
        "<w:next w:val=\"Normal\"/><w:pPr><w:keepNext/><w:spacing w:before=\"240\" w:after=\"120\"/><w:outlineLvl w:val=\"0\"/></w:pPr>" +
        "<w:rPr><w:b/><w:sz w:val=\"36\"/><w:szCs w:val=\"36\"/></w:rPr></w:style>" +
        "<w:style w:type=\"paragraph\" w:styleId=\"Heading2\"><w:name w:val=\"heading 2\"/><w:basedOn w:val=\"Normal\"/>" +
        "<w:next w:val=\"Normal\"/><w:pPr><w:keepNext/><w:spacing w:before=\"200\" w:after=\"100\"/><w:outlineLvl w:val=\"1\"/></w:pPr>" +
        "<w:rPr><w:b/><w:sz w:val=\"28\"/><w:szCs w:val=\"28\"/></w:rPr></w:style>" +
        "<w:style w:type=\"table\" w:styleId=\"TableGrid\"><w:name w:val=\"Table Grid\"/><w:tblPr><w:tblBorders>" +
        "<w:top w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"BFBFBF\"/><w:left w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"BFBFBF\"/>" +
        "<w:bottom w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"BFBFBF\"/><w:right w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"BFBFBF\"/>" +
        "<w:insideH w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"BFBFBF\"/><w:insideV w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"BFBFBF\"/>" +
        "</w:tblBorders><w:tblCellMar><w:left w:w=\"80\" w:type=\"dxa\"/><w:right w:w=\"80\" w:type=\"dxa\"/></w:tblCellMar></w:tblPr></w:style>" +
        "</w:styles>"
}
