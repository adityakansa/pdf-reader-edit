package com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf

import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSFloat
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdfwriter.ContentStreamWriter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDTransparencyGroup
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import com.tom_roush.pdfbox.util.Matrix
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.text.EditFont
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** A rectangle on the page as fractions (0..1 from the top left), the unit every overlay in the app uses. */
data class EditBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun contains(x: Float, y: Float, slop: Float = 0f): Boolean =
        x >= left - slop && x <= right + slop && y >= top - slop && y <= bottom + slop
}

/** One change the PDF editor makes to a page. */
sealed interface PdfEdit {
    val id: Long
    val page: Int
    val box: EditBox

    /**
     * Text written onto the page. With [cover] it **replaces** text that was there: the old words in that box
     * are taken out of the page, and only where that is impossible is white laid over them.
     */
    data class Text(
        override val id: Long,
        override val page: Int,
        override val box: EditBox,
        val text: String,
        /** Points, as the PDF measures type. */
        val fontSize: Float,
        val colorArgb: Int = BLACK,
        val font: EditFont = EditFont.SANS,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val cover: EditBox? = null,
        /** The text that was there, so an unchanged box can be skipped. */
        val original: String? = null,
        /** Set when the user changed size, colour or font, which makes even the same words a change. */
        val restyled: Boolean = false,
    ) : PdfEdit {
        val unchanged: Boolean get() = cover != null && original == text && !restyled
    }

    /** A picture placed on the page (PNG or JPEG bytes). */
    data class Image(
        override val id: Long,
        override val page: Int,
        override val box: EditBox,
        val bytes: ByteArray,
    ) : PdfEdit {
        override fun equals(other: Any?): Boolean =
            other is Image && other.id == id && other.page == page && other.box == box && other.bytes.contentEquals(bytes)

        override fun hashCode(): Int = id.hashCode()
    }

    companion object {
        const val BLACK = 0xFF000000.toInt()
    }
}

/**
 * Step 12b. Writes the PDF editor's changes into a copy of the document.
 *
 * Retyped text: the old words are removed from the page content itself when each text-showing operator
 * that drew them lies wholly inside the edited box (the operator is swapped for an empty `TJ` that moves
 * the pen by the same distance, so text after it on the line does not shift). Then nothing of the old
 * words is left to copy or search, and a coloured table cell keeps its colour. Where old glyphs cannot be
 * removed that cleanly (they share an operator with text outside the box, or come from a form), white is
 * painted over them instead.
 *
 * New text and images are appended in a separate content stream, so the rest of the page is untouched.
 */
object PdfPageEditor {

    fun apply(
        input: InputStream,
        password: String?,
        edits: List<PdfEdit>,
        target: OutputStream,
        scratchDirectory: File,
        /** A TrueType font with wide coverage for text the built-in fonts cannot encode; null to drop such characters. */
        fallbackFont: File? = null,
    ) {
        scratchDirectory.mkdirs()
        val memory = MemoryUsageSetting.setupMixed(MAIN_MEMORY_BYTES).setTempDir(scratchDirectory)
        val document = if (password == null) PDDocument.load(input, memory) else PDDocument.load(input, password, memory)
        document.use { pdf ->
            val fallback = lazy { fallbackFont?.takeIf { it.exists() }?.let { runCatching { PDType0Font.load(pdf, it) }.getOrNull() } }
            edits.filterNot { it is PdfEdit.Text && it.unchanged }
                .groupBy { it.page }
                .forEach { (pageIndex, pageEdits) ->
                    if (pageIndex !in 0 until pdf.numberOfPages) return@forEach
                    val page = pdf.getPage(pageIndex)
                    val retyped = pageEdits.filterIsInstance<PdfEdit.Text>().filter { it.cover != null }
                    val left = if (retyped.isEmpty()) emptyList() else TextRemover.remove(pdf, page, retyped.map { it.cover!! })
                    val whiteouts = retyped.filter { it.cover in left }
                    drawEdits(pdf, page, pageEdits, whiteouts) { fallback.value }
                }
            PdfSecurity.prepareForSave(pdf, password)
            pdf.save(target)
        }
    }

    private fun drawEdits(
        document: PDDocument,
        page: PDPage,
        edits: List<PdfEdit>,
        whiteouts: List<PdfEdit.Text>,
        fallback: () -> PDFont?,
    ) {
        val box = page.mediaBox
        val rotation = ((page.rotation % FULL_TURN) + FULL_TURN) % FULL_TURN
        val turned = rotation == QUARTER || rotation == THREE_QUARTERS
        // The size of the page as the viewer shows it, in points.
        val shownWidth = if (turned) box.height else box.width
        val shownHeight = if (turned) box.width else box.height
        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { content ->
            // From here on, (0,0) is the top left of the page as shown and y grows downwards.
            content.transform(displayToPage(rotation, box.lowerLeftX, box.lowerLeftY, box.width, box.height))
            whiteouts.forEach { edit ->
                val cover = edit.cover ?: return@forEach
                // A word box's bottom is its baseline. Letters reach about one font size above it at most
                // and descenders (g, p, y) a quarter below, so the white spans exactly that.
                val baseline = cover.bottom * shownHeight
                val size = edit.fontSize.coerceAtLeast(cover.height * shownHeight)
                val top = minOf(cover.top * shownHeight, baseline - size * COVER_ABOVE)
                content.setNonStrokingColor(1f, 1f, 1f)
                content.addRect(
                    (cover.left - COVER_PAD) * shownWidth,
                    top,
                    (cover.width + 2 * COVER_PAD) * shownWidth,
                    baseline + size * COVER_BELOW - top,
                )
                content.fill()
            }
            edits.forEach { edit ->
                when (edit) {
                    is PdfEdit.Text -> if (!edit.unchanged) drawText(content, edit, shownWidth, shownHeight, fallback)
                    is PdfEdit.Image -> {
                        val image = PDImageXObject.createFromByteArray(document, edit.bytes, "image-${edit.id}")
                        val x = edit.box.left * shownWidth
                        val y = edit.box.top * shownHeight
                        val w = edit.box.width * shownWidth
                        val h = edit.box.height * shownHeight
                        // Image space has y up; the flip puts the picture's top at the box's top.
                        content.drawImage(image, Matrix(w, 0f, 0f, -h, x, y + h))
                    }
                }
            }
        }
    }

    private fun drawText(
        content: PDPageContentStream,
        edit: PdfEdit.Text,
        shownWidth: Float,
        shownHeight: Float,
        fallback: () -> PDFont?,
    ) {
        val builtIn = builtInFont(edit.font, edit.bold, edit.italic)
        val needsFallback = edit.text.any { it != '\n' && !encodable(it) }
        val font = if (needsFallback) fallback() ?: builtIn else builtIn
        // Characters no font here can draw (e.g. Devanagari, which also needs shaping) are left out rather
        // than failing the whole save.
        val text = edit.text.filter { it == '\n' || canDraw(font, it) }
        val size = edit.fontSize.coerceAtLeast(MIN_FONT)
        val x = edit.box.left * shownWidth
        val top = edit.box.top * shownHeight
        val width = edit.box.width * shownWidth
        val lines = TextWrap.wrap(text, width) { font.getStringWidth(it) / FONT_UNITS * size }
        content.setNonStrokingColor(
            ((edit.colorArgb shr 16) and 0xFF) / 255f,
            ((edit.colorArgb shr 8) and 0xFF) / 255f,
            (edit.colorArgb and 0xFF) / 255f,
        )
        lines.forEachIndexed { index, line ->
            if (line.isEmpty()) return@forEachIndexed
            val baseline = top + size * FIRST_BASELINE + index * size * LINE_SPACING
            content.beginText()
            content.setFont(font, size)
            // d = -1 undoes the page flip above, so the letters stand upright.
            content.setTextMatrix(Matrix(1f, 0f, 0f, -1f, x, baseline))
            content.showText(line)
            content.endText()
        }
    }

    /** The matrix that makes "shown page, top-left origin, y down" the drawing space. */
    internal fun displayToPage(rotation: Int, llx: Float, lly: Float, w: Float, h: Float): Matrix = when (rotation) {
        QUARTER -> Matrix(0f, 1f, 1f, 0f, llx, lly)
        HALF -> Matrix(-1f, 0f, 0f, 1f, llx + w, lly)
        THREE_QUARTERS -> Matrix(0f, -1f, -1f, 0f, llx + w, lly + h)
        else -> Matrix(1f, 0f, 0f, -1f, llx, lly + h)
    }

    private fun builtInFont(font: EditFont, bold: Boolean, italic: Boolean): PDType1Font = when (font) {
        EditFont.SANS -> when {
            bold && italic -> PDType1Font.HELVETICA_BOLD_OBLIQUE
            bold -> PDType1Font.HELVETICA_BOLD
            italic -> PDType1Font.HELVETICA_OBLIQUE
            else -> PDType1Font.HELVETICA
        }
        EditFont.SERIF -> when {
            bold && italic -> PDType1Font.TIMES_BOLD_ITALIC
            bold -> PDType1Font.TIMES_BOLD
            italic -> PDType1Font.TIMES_ITALIC
            else -> PDType1Font.TIMES_ROMAN
        }
        EditFont.MONO -> when {
            bold && italic -> PDType1Font.COURIER_BOLD_OBLIQUE
            bold -> PDType1Font.COURIER_BOLD
            italic -> PDType1Font.COURIER_OBLIQUE
            else -> PDType1Font.COURIER
        }
    }

    private fun canDraw(font: PDFont, c: Char): Boolean =
        if (font is PDType1Font) encodable(c) else runCatching { font.encode(c.toString()) }.isSuccess

    /** What the built-in fonts' WinAnsi encoding can carry. */
    fun encodable(c: Char): Boolean = c.code in 32..126 || c.code in 160..255 || c in WIN_ANSI_EXTRA

    private val WIN_ANSI_EXTRA = setOf('€', '‚', 'ƒ', '„', '…', '†', '‡', 'ˆ', '‰', 'Š', '‹', 'Œ', 'Ž', '‘', '’', '“', '”', '•', '–', '—', '˜', '™', 'š', '›', 'œ', 'ž', 'Ÿ')

    /** Where the first baseline sits below the top of the box, and line spacing, both in font sizes; the editor draws the same. */
    const val FIRST_BASELINE = 0.94f
    const val LINE_SPACING = 1.2f

    private const val FONT_UNITS = 1000f
    private const val MIN_FONT = 2f
    private const val COVER_PAD = 0.0015f
    private const val COVER_ABOVE = 0.95f
    private const val COVER_BELOW = 0.26f
    private const val MAIN_MEMORY_BYTES = 16L * 1024 * 1024
    private const val FULL_TURN = 360
    private const val QUARTER = 90
    private const val HALF = 180
    private const val THREE_QUARTERS = 270
}

/**
 * The box a retyped line gets: its first baseline lands exactly on the old baseline (a word box's bottom is
 * its baseline), one line tall, and at least as wide as the old words.
 */
fun retypeBox(left: Float, right: Float, baseline: Float, fontSize: Float, pageHeight: Float): EditBox {
    val size = fontSize / pageHeight
    val top = baseline - size * PdfPageEditor.FIRST_BASELINE
    return EditBox(left, top, right, top + size * PdfPageEditor.LINE_SPACING)
}

/** Breaks text into lines that fit a width: at newlines, then between words, then inside a too-long word. */
object TextWrap {
    fun wrap(text: String, maxWidth: Float, measure: (String) -> Float): List<String> {
        if (maxWidth <= 0f) return text.split('\n')
        val out = mutableListOf<String>()
        for (paragraph in text.split('\n')) {
            var line = ""
            for (word in paragraph.split(' ')) {
                val candidate = if (line.isEmpty()) word else "$line $word"
                // A little tolerance, because the screen measured with a different font.
                if (measure(candidate) <= maxWidth * WRAP_TOLERANCE || line.isEmpty()) {
                    line = candidate
                } else {
                    out += line
                    line = word
                }
                while (measure(line) > maxWidth * WRAP_TOLERANCE && line.length > 1) {
                    var cut = line.length - 1
                    while (cut > 1 && measure(line.substring(0, cut)) > maxWidth) cut--
                    out += line.substring(0, cut)
                    line = line.substring(cut)
                }
            }
            out += line
        }
        return out
    }

    private const val WRAP_TOLERANCE = 1.04f
}

/**
 * Takes the words inside [covers] out of a page's content. Returns the covers that still need white laid
 * over them because some glyph in them could not be removed.
 */
internal object TextRemover {

    private class Glyph(val operator: Int, val x: Float, val y: Float)

    fun remove(document: PDDocument, page: PDPage, covers: List<EditBox>): List<EditBox> = try {
        removeOrThrow(document, page, covers)
    } catch (_: Exception) {
        // Anything unusual in the content stream: leave it as it was and cover the old words instead.
        covers
    }

    private fun removeOrThrow(document: PDDocument, page: PDPage, covers: List<EditBox>): List<EditBox> {
        val width = page.mediaBox.width
        val height = page.mediaBox.height
        val parser = PDFStreamParser(page)
        parser.parse()
        val tokens: MutableList<Any> = parser.tokens.toMutableList()
        val operatorAt = tokens.indices.filter { tokens[it] is Operator }

        val glyphs = mutableListOf<Glyph>()
        // For each Tj/TJ (by the order operators run in): the TJ number that moves the pen as far as it did.
        val advances = HashMap<Int, Float>()
        val pageIndex = document.pages.indexOf(page)

        val stripper = object : PDFTextStripper() {
            var ordinal = -1
            var nesting = 0
            var formDepth = 0

            override fun processOperator(operator: Operator, operands: List<COSBase>) {
                val topLevel = nesting == 0 && formDepth == 0
                if (topLevel) ordinal++
                val mine = ordinal
                val shows = topLevel && (operator.name == "Tj" || operator.name == "TJ")
                val before = if (shows) textMatrix?.clone() else null
                val state = graphicsState.textState
                val fontSize = state.fontSize
                val scale = state.horizontalScaling / PERCENT
                nesting++
                try {
                    super.processOperator(operator, operands)
                } finally {
                    nesting--
                }
                val after = textMatrix
                if (before != null && after != null && fontSize != 0f && scale != 0f) {
                    val a = before.scaleX
                    val b = before.shearY
                    val norm = a * a + b * b
                    if (norm > 0f) {
                        val tx = ((after.translateX - before.translateX) * a + (after.translateY - before.translateY) * b) / norm
                        advances[mine] = -tx * THOUSAND / (fontSize * scale)
                    }
                }
            }

            override fun showForm(form: PDFormXObject) {
                formDepth++
                try {
                    super.showForm(form)
                } finally {
                    formDepth--
                }
            }

            override fun showTransparencyGroup(form: PDTransparencyGroup) {
                formDepth++
                try {
                    super.showTransparencyGroup(form)
                } finally {
                    formDepth--
                }
            }

            override fun processTextPosition(text: TextPosition) {
                // The same frame PdfWords measures words in, so the covers line up with these points.
                val cx = (text.xDirAdj + text.widthDirAdj / 2f) / width
                val cy = (text.yDirAdj - text.heightDir / 2f) / height
                glyphs += Glyph(if (formDepth == 0) ordinal else -1, cx, cy)
            }
        }
        stripper.sortByPosition = false
        stripper.startPage = pageIndex + 1
        stripper.endPage = pageIndex + 1
        stripper.getText(document)

        fun inCover(g: Glyph) = covers.any { it.contains(g.x, g.y, SLOP) }

        val removable = glyphs.groupBy { it.operator }
            .filterKeys { it >= 0 && it in advances }
            .filterValues { list -> list.all(::inCover) }
            .keys
            .filter { it < operatorAt.size && operatorAt[it] > 0 }
        removable.forEach { ordinal ->
            val at = operatorAt[ordinal]
            val name = (tokens[at] as Operator).name
            if (name != "Tj" && name != "TJ") return@forEach
            tokens[at - 1] = COSArray().apply { add(COSFloat(advances.getValue(ordinal))) }
            tokens[at] = Operator.getOperator("TJ")
        }
        if (removable.isNotEmpty()) {
            val stream = PDStream(document)
            stream.createOutputStream(COSName.FLATE_DECODE).use { ContentStreamWriter(it).writeTokens(tokens) }
            page.setContents(stream)
        }
        val removed = removable.toSet()
        return covers.filter { cover ->
            glyphs.any { g -> cover.contains(g.x, g.y, SLOP) && g.operator !in removed }
        }
    }

    private const val SLOP = 0.004f
    private const val PERCENT = 100f
    private const val THOUSAND = 1000f
}
