package check
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.PdfTools
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.WrongPdfPasswordException
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

class PdfToolsCheck {
    private val scratch = File("build/scratch").apply { mkdirs() }

    /** A PDF whose page i says "P<prefix><i>" so order can be checked by text. */
    private fun pdf(prefix: String, pages: Int): ByteArray = PDDocument().use { doc ->
        repeat(pages) { i ->
            val page = PDPage(PDRectangle.A4); doc.addPage(page)
            PDPageContentStream(doc, page).use { c ->
                c.beginText(); c.setFont(PDType1Font.HELVETICA, 12f); c.newLineAtOffset(50f, 700f)
                c.showText("P$prefix$i"); c.endText()
            }
        }
        ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
    }
    private fun out(block: (ByteArrayOutputStream) -> Unit) = ByteArrayOutputStream().also(block).toByteArray()
    private fun texts(bytes: ByteArray, password: String = ""): List<String> = PDDocument.load(bytes, password).use { d ->
        (1..d.numberOfPages).map { p -> PDFTextStripper().apply { startPage = p; endPage = p }.getText(d).trim() }
    }

    @Test fun merge_keeps_order_and_all_pages() {
        val a = pdf("a", 2); val b = pdf("b", 3)
        val merged = out { PdfTools.merge(listOf({ ByteArrayInputStream(a) }, { ByteArrayInputStream(b) }), it, scratch) }
        assertEquals(listOf("Pa0", "Pa1", "Pb0", "Pb1", "Pb2"), texts(merged))
    }

    @Test fun extract_uses_given_order_and_ignores_bad_indexes() {
        val r = out { PdfTools.extract(ByteArrayInputStream(pdf("x", 4)), listOf(3, 0, 3, 9, -1), it, scratch) }
        assertEquals(listOf("Px3", "Px0"), texts(r))
    }

    @Test fun delete_removes_pages() {
        val r = out { PdfTools.delete(ByteArrayInputStream(pdf("x", 4)), listOf(1, 2), it, scratch) }
        assertEquals(listOf("Px0", "Px3"), texts(r))
    }

    @Test(expected = IllegalArgumentException::class) fun delete_refuses_every_page() {
        out { PdfTools.delete(ByteArrayInputStream(pdf("x", 2)), listOf(0, 1), it, scratch) }
    }

    @Test fun rotate_left_and_right() {
        val r = out { PdfTools.rotate(ByteArrayInputStream(pdf("x", 2)), listOf(0), -90, it, scratch) }
        PDDocument.load(r).use { assertEquals(270, it.getPage(0).rotation); assertEquals(0, it.getPage(1).rotation) }
        val r2 = out { PdfTools.rotate(ByteArrayInputStream(r), listOf(0, 1), 90, it, scratch) }
        PDDocument.load(r2).use { assertEquals(0, it.getPage(0).rotation); assertEquals(90, it.getPage(1).rotation) }
    }

    @Test fun protect_then_unlock_round_trip() {
        val locked = out { PdfTools.protect(ByteArrayInputStream(pdf("s", 2)), "secret", it, scratch) }
        try { PDDocument.load(locked).close(); fail("opened without password") } catch (_: InvalidPasswordException) {}
        assertEquals(listOf("Ps0", "Ps1"), texts(locked, "secret"))
        try { out { PdfTools.unlock(ByteArrayInputStream(locked), "wrong", it, scratch) }; fail("wrong password accepted") }
        catch (_: WrongPdfPasswordException) {}
        val open = out { PdfTools.unlock(ByteArrayInputStream(locked), "secret", it, scratch) }
        PDDocument.load(open).use { assertFalse(it.isEncrypted) }
        assertEquals(listOf("Ps0", "Ps1"), texts(open))
    }

    @Test fun normalise_rotation() {
        assertEquals(270, PdfTools.normaliseRotation(-90)); assertEquals(0, PdfTools.normaliseRotation(720))
        assertEquals(listOf(2, 0), PdfTools.pagesToKeep(listOf(2, 2, 0, 5), 3))
    }
}
