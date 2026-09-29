package check

import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.MarkupKind
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.PdfMarkup
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.PdfMarkupWriter
import org.apache.pdfbox.cos.COSArray
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.rendering.PDFRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

class MarkupCheck {
    private val scratch = File("scratch").apply { mkdirs() }

    private fun source(): ByteArray = PDDocument().use { doc ->
        val page = PDPage(PDRectangle.A4); doc.addPage(page)
        PDPageContentStream(doc, page).use { c ->
            c.setFont(PDType1Font.HELVETICA, 18f)
            listOf(700f, 650f, 600f).forEachIndexed { i, y ->
                c.beginText(); c.newLineAtOffset(72f, y); c.showText("Line ${i + 1}: The quick brown fox"); c.endText()
            }
        }
        ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
    }

    @Test fun every_kind_becomes_the_standard_annotation() {
        val h = PDRectangle.A4.height; val w = PDRectangle.A4.width
        fun box(y: Float) = Triple(72f / w, (h - y - 18f) / h, (h - y + 4f) / h)
        val marks = listOf(
            box(700f).let { (l, t, b) -> PdfMarkup(0, l, t, 0.62f, b, 0xFFFFEB3B.toInt(), "Line 1", MarkupKind.HIGHLIGHT) },
            box(650f).let { (l, t, b) -> PdfMarkup(0, l, t, 0.62f, b, 0xFF1E6FD9.toInt(), "Line 2", MarkupKind.UNDERLINE) },
            box(600f).let { (l, t, b) -> PdfMarkup(0, l, t, 0.62f, b, 0xFFD32F2F.toInt(), "Line 3", MarkupKind.STRIKEOUT) },
            PdfMarkup(0, 0.2f, 0.35f, 0.6f, 0.45f, 0xFF000000.toInt(), kind = MarkupKind.INK,
                strokes = listOf(listOf(0.2f to 0.45f, 0.3f to 0.35f, 0.4f to 0.45f, 0.5f to 0.35f, 0.6f to 0.45f))),
        )
        val out = File(scratch, "annotated.pdf")
        PdfMarkupWriter.write(ByteArrayInputStream(source()), null, marks, out, scratch)
        PDDocument.load(out).use { doc ->
            val annotations = doc.getPage(0).annotations
            assertEquals(listOf("Highlight", "Underline", "StrikeOut", "Ink"), annotations.map { it.subtype })
            val ink = annotations.last().cosObject.getDictionaryObject(COSName.getPDFName("InkList")) as COSArray
            assertEquals(10, (ink.getObject(0) as COSArray).size())
            annotations.drop(1).forEach { assertNotNull(it.appearance?.normalAppearance) }
            ImageIO.write(PDFRenderer(doc).renderImageWithDPI(0, 60f), "png", File(scratch, "annotated.png"))
        }
    }
}
