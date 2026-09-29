package check

import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.DocxBuilder
import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.DocxToHtml
import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.OoxmlZip
import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.WordBlock
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.text.PdfWords
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.text.TextLines
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.text.WordLayout
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class PdfToWordCheck {

    private fun text(c: PDPageContentStream, font: PDFont, size: Float, x: Float, y: Float, s: String) {
        c.beginText(); c.setFont(font, size); c.newLineAtOffset(x, y); c.showText(s); c.endText()
    }

    private fun report(): ByteArray = PDDocument().use { doc ->
        val page = PDPage(PDRectangle.A4); doc.addPage(page)
        val w = PDRectangle.A4.width
        PDPageContentStream(doc, page).use { c ->
            val title = "Quarterly Report"
            val tw = PDType1Font.HELVETICA_BOLD.getStringWidth(title) / 1000 * 24
            text(c, PDType1Font.HELVETICA_BOLD, 24f, (w - tw) / 2, 780f, title)
            text(c, PDType1Font.HELVETICA_BOLD, 15f, 60f, 740f, "Summary")
            text(c, PDType1Font.HELVETICA, 11f, 60f, 715f, "Sales grew in every region this quarter, led by strong demand for the new infor-")
            text(c, PDType1Font.HELVETICA, 11f, 60f, 701f, "mation services and a steady rise in renewals across existing customers.")
            text(c, PDType1Font.HELVETICA, 11f, 60f, 660f, "A second paragraph starts after a gap.")
            listOf(620f to listOf("Region", "Q1", "Q2"), 604f to listOf("North", "120", "140"), 588f to listOf("South", "98", "133"))
                .forEach { (y, cells) -> cells.forEachIndexed { i, cell -> text(c, PDType1Font.HELVETICA, 11f, 60f + i * 150f, y, cell) } }
        }
        ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
    }

    @Test fun pdf_page_becomes_headings_paragraphs_and_a_table() {
        val blocks = PDDocument.load(report()).use { d ->
            val box = d.getPage(0).mediaBox
            WordLayout.blocks(TextLines.group(PdfWords.onPage(d, 0), box.width, box.height), box.width, box.height)
        }
        val paragraphs = blocks.filterIsInstance<WordBlock.Paragraph>()
        assertEquals("Quarterly Report", paragraphs[0].runs.joinToString("") { it.text })
        assertEquals(1, paragraphs[0].heading)
        assertTrue("title is centred", paragraphs[0].centered)
        assertEquals(2, paragraphs[1].heading)
        val body = paragraphs[2].runs.joinToString("") { it.text }
        assertTrue(body, body.contains("new information services"))
        assertTrue(body, body.endsWith("existing customers."))
        assertTrue("gap kept", paragraphs[3].spaceBefore > 0f)
        val table = blocks.filterIsInstance<WordBlock.Table>().single()
        assertEquals(listOf(listOf("Region", "Q1", "Q2"), listOf("North", "120", "140"), listOf("South", "98", "133")), table.rows)

        val img = java.awt.image.BufferedImage(20, 10, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val png = ByteArrayOutputStream().also { out -> javax.imageio.ImageIO.write(img, "png", out) }.toByteArray()
        val file = File("build/pdf2word.docx")
        file.outputStream().use {
            DocxBuilder.write(blocks + WordBlock.PageBreak + WordBlock.Picture(png, true, 200f, 100f), "Quarterly Report", 595f, 842f, it)
        }
        // Our own viewer reads it back.
        val html = DocxToHtml.convert(OoxmlZip.read(file.inputStream()), emptyMap())
        assertTrue(html.contains("Quarterly Report"))
        assertTrue(html.contains("<table"))
    }
}
