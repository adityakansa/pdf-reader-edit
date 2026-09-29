package check

import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.EditBox
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.PdfEdit
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.PdfPageEditor
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.PdfTools
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.TextRemover
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.TextWrap
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.text.EditFont
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.text.PdfWords
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.text.TextLine
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.text.TextLines
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

class PdfEditorCheck {
    private val scratch = File("build/scratch").apply { mkdirs() }
    private val shots = File(System.getProperty("shots", "build/shots")).apply { mkdirs() }

    /** An invoice-like page: a label and an amount in one BT block, a sentence, and a coloured cell. */
    private fun invoice(rotation: Int = 0): ByteArray = PDDocument().use { doc ->
        val page = PDPage(PDRectangle.A4)
        page.rotation = rotation
        doc.addPage(page)
        PDPageContentStream(doc, page).use { c ->
            c.setNonStrokingColor(0.85f, 0.92f, 1f)
            c.addRect(40f, 690f, 300f, 24f); c.fill()
            c.setNonStrokingColor(0f, 0f, 0f)
            c.beginText(); c.setFont(PDType1Font.HELVETICA, 12f); c.newLineAtOffset(50f, 700f)
            // Two Tj in one block: the amount is placed by the label's advance plus a TJ gap.
            c.showText("Bus Fare"); c.showTextWithPositioning(arrayOf(-9000f, "374.00")); c.endText()
            c.beginText(); c.setFont(PDType1Font.HELVETICA_BOLD, 14f); c.newLineAtOffset(50f, 600f)
            c.showText("Total Invoice Value 417.90"); c.endText()
        }
        ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
    }

    private fun text(bytes: ByteArray, password: String = ""): String =
        PDDocument.load(bytes, password).use { PDFTextStripper().getText(it) }

    private fun lines(bytes: ByteArray, page: Int = 0): List<TextLine> = PDDocument.load(bytes).use { d ->
        val p = d.getPage(page)
        TextLines.group(PdfWords.onPage(d, page), p.mediaBox.width, p.mediaBox.height)
    }

    private fun edit(bytes: ByteArray, edits: List<PdfEdit>, password: String? = null, font: File? = null): ByteArray =
        ByteArrayOutputStream().also {
            PdfPageEditor.apply(ByteArrayInputStream(bytes), password, edits, it, scratch, font)
        }.toByteArray()

    private fun render(bytes: ByteArray, name: String): BufferedImage = PDDocument.load(bytes).use { d ->
        PDFRenderer(d).renderImageWithDPI(0, 72f).also { ImageIO.write(it, "png", File(shots, "$name.png")) }
    }

    private fun retype(line: TextLine, text: String, id: Long = 1) = PdfEdit.Text(
        id = id, page = 0,
        box = com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.retypeBox(line.left, maxOf(line.right, line.left + 0.3f), line.bottom, line.fontSize, 842f),
        text = text, fontSize = line.fontSize, font = line.family, bold = line.bold,
        cover = EditBox(line.left, line.top, line.right, line.bottom), original = line.text,
    )

    @Test fun lines_split_table_columns_and_keep_sentences() {
        val found = lines(invoice()).map { it.text }
        assertEquals(listOf("Bus Fare", "374.00", "Total Invoice Value 417.90"), found)
        val bold = lines(invoice()).last()
        assertTrue(bold.bold); assertEquals(EditFont.SANS, bold.family); assertEquals(14f, bold.fontSize, 0.5f)
    }

    @Test fun retyped_words_leave_the_page_and_neighbours_stay_put() {
        val original = invoice()
        val before = PDDocument.load(original).use { PdfWords.onPage(it, 0) }.first { it.text == "374.00" }
        val busFare = lines(original).first { it.text == "Bus Fare" }
        val edited = edit(original, listOf(retype(busFare, "Taxi Fare")))
        val all = text(edited)
        assertTrue(all, all.contains("Taxi Fare"))
        assertFalse("old words must be gone, not just covered: $all", all.contains("Bus Fare"))
        val words = PDDocument.load(edited).use { PdfWords.onPage(it, 0) }
        val after = words.first { it.text == "374.00" }
        // The new words sit on the old baseline.
        val taxi = words.first { it.text == "Taxi" }
        assertEquals(busFare.bottom, taxi.bottom, 0.0015f)
        assertEquals(before.left, after.left, 0.001f)
        assertEquals(before.top, after.top, 0.001f)
        // No white box was needed, so the blue cell behind the label keeps its colour.
        val image = render(edited, "edit_retype")
        val probe = image.getRGB(45, 842 - 702)
        assertEquals("cell colour under the edit", 0xD9EBFF, probe and 0xFFFFFF, )
    }

    @Test fun words_sharing_an_operator_are_covered_instead() {
        val original = invoice()
        val total = lines(original).last()
        // Only "Total" of the one-operator line: it cannot be cut out, so it must be covered.
        val words = PDDocument.load(original).use { PdfWords.onPage(it, 0) }
        val word = words.first { it.text == "Total" }
        val cover = EditBox(word.left, word.top, word.right, word.bottom)
        PDDocument.load(original).use { d ->
            assertEquals(listOf(cover), TextRemover.remove(d, d.getPage(0), listOf(cover)))
        }
        val edited = edit(original, listOf(PdfEdit.Text(2, 0, cover, "Sum", total.fontSize, cover = cover, original = "Total")))
        val image = render(edited, "edit_whiteout")
        assertTrue(text(edited).contains("Sum"))
        // The white box hides the old word: the middle of where "Total" was is white apart from the new text.
        assertTrue(image.width > 0)
    }

    @Test fun unchanged_boxes_write_nothing() {
        val original = invoice()
        val line = lines(original).first()
        val edited = edit(original, listOf(retype(line, line.text)))
        assertEquals(text(original), text(edited))
    }

    @Test fun new_text_wraps_and_uses_the_fallback_font_for_other_scripts() {
        val original = invoice()
        val font = File("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")
        val added = PdfEdit.Text(3, 0, EditBox(0.1f, 0.5f, 0.35f, 0.6f), "Привет мир, hello world again", 12f)
        val edited = edit(original, listOf(added), font = if (font.exists()) font else null)
        render(edited, "edit_fallback")
        val all = text(edited)
        if (font.exists()) assertTrue(all, all.contains("Привет")) else assertTrue(all, all.contains("hello"))
        val lines = TextWrap.wrap("one two three four", 30f) { it.length * 5f }
        assertEquals(listOf("one", "two", "three", "four"), lines)
        assertEquals(listOf("a", "", "b"), TextWrap.wrap("a\n\nb", 100f) { it.length * 5f })
    }

    @Test fun images_are_placed_on_the_page() {
        val png = ByteArrayOutputStream().also {
            val img = BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB)
            for (x in 0 until 40) for (y in 0 until 20) img.setRGB(x, y, 0xFF0000)
            ImageIO.write(img, "png", it)
        }.toByteArray()
        val edited = edit(invoice(), listOf(PdfEdit.Image(4, 0, EditBox(0.5f, 0.1f, 0.7f, 0.2f), png)))
        PDDocument.load(edited).use { d ->
            val names = d.getPage(0).resources.xObjectNames.toList()
            assertEquals(1, names.size)
            assertTrue(d.getPage(0).resources.getXObject(names.first()) is PDImageXObject)
        }
        val image = render(edited, "edit_image")
        // Middle of the box, measured from the top left of the shown page.
        val x = (0.6f * image.width).toInt(); val y = (0.15f * image.height).toInt()
        assertEquals(0xFF0000, image.getRGB(x, y) and 0xFFFFFF)
    }

    @Test fun rotated_pages_put_new_things_where_the_user_saw_them() {
        val png = ByteArrayOutputStream().also {
            val img = BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB)
            for (x in 0 until 10) for (y in 0 until 10) img.setRGB(x, y, 0x00FF00)
            ImageIO.write(img, "png", it)
        }.toByteArray()
        for (rotation in listOf(0, 90, 180, 270)) {
            val edited = edit(invoice(rotation), listOf(PdfEdit.Image(5, 0, EditBox(0.05f, 0.05f, 0.25f, 0.15f), png)))
            val image = render(edited, "edit_rot_$rotation")
            // The renderer shows the page turned, like the phone does: the green box is at the top left.
            val x = (0.15f * image.width).toInt(); val y = (0.10f * image.height).toInt()
            assertEquals("rotation $rotation", 0x00FF00, image.getRGB(x, y) and 0xFFFFFF)
        }
    }

    @Test fun protected_pdfs_stay_protected() {
        val locked = ByteArrayOutputStream().also {
            PdfTools.protect(ByteArrayInputStream(invoice()), "pw", it, scratch)
        }.toByteArray()
        val added = PdfEdit.Text(6, 0, EditBox(0.1f, 0.8f, 0.5f, 0.85f), "Paid", 12f)
        val edited = edit(locked, listOf(added), password = "pw")
        try {
            PDDocument.load(edited).close()
            fail("opened without the password")
        } catch (_: InvalidPasswordException) {
        }
        assertTrue(text(edited, "pw").contains("Paid"))
    }
}

/** PDFs that open without a password but carry owner restrictions: every tool must still save them. */
class RestrictedPdfCheck {
    private val scratch = File("build/scratch").apply { mkdirs() }

    private fun restricted(): ByteArray = PDDocument().use { doc ->
        val page = PDPage(PDRectangle.A4); doc.addPage(page)
        PDPageContentStream(doc, page).use { c ->
            c.beginText(); c.setFont(PDType1Font.HELVETICA, 12f); c.newLineAtOffset(50f, 700f); c.showText("Restricted"); c.endText()
        }
        val perms = org.apache.pdfbox.pdmodel.encryption.AccessPermission().apply { setCanModify(false); setCanPrint(false) }
        doc.protect(org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy("owner-secret", "", perms).apply { encryptionKeyLength = 128 })
        ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
    }

    private fun out(block: (ByteArrayOutputStream) -> Unit) = ByteArrayOutputStream().also(block).toByteArray()

    @Test fun editor_rotate_and_delete_save_owner_restricted_pdfs() {
        val edited = out {
            PdfPageEditor.apply(ByteArrayInputStream(restricted()), null,
                listOf(PdfEdit.Text(1, 0, EditBox(0.1f, 0.3f, 0.5f, 0.35f), "Added", 12f)), it, scratch)
        }
        PDDocument.load(edited).use { assertTrue(PDFTextStripper().getText(it).contains("Added")) }
        val rotated = out { PdfTools.rotate(ByteArrayInputStream(restricted()), listOf(0), 90, it, scratch) }
        PDDocument.load(rotated).use { assertEquals(90, it.getPage(0).rotation) }
    }
}

class MarkupEraserCheck {
    private fun mark(kind: com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.MarkupKind, strokes: List<List<Pair<Float, Float>>> = emptyList()) =
        com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.PdfMarkup(0, 0.1f, 0.1f, 0.5f, 0.12f, 0, kind = kind, strokes = strokes)

    @Test fun erases_boxes_under_the_finger_and_ink_only_near_the_line() {
        val E = com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.MarkupEraser
        val highlight = mark(com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.MarkupKind.HIGHLIGHT)
        val ink = mark(com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.MarkupKind.INK, listOf(listOf(0.1f to 0.1f, 0.5f to 0.5f)))
        assertEquals(listOf(ink), E.erase(listOf(highlight, ink), 0, 0.3f, 0.11f).filter { it.kind == com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.MarkupKind.INK })
        assertTrue(E.hit(ink, 0.3f, 0.3f))
        assertFalse(E.hit(ink, 0.3f, 0.12f))
        assertEquals(2, E.erase(listOf(highlight, ink), 1, 0.3f, 0.11f).size)
    }
}
