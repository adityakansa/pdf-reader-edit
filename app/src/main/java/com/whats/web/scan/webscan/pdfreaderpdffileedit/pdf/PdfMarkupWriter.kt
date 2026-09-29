package com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf

import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSFloat
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary
import java.io.File
import java.io.InputStream

/** The kinds of mark the reader can add; each becomes the matching standard PDF annotation. */
enum class MarkupKind { HIGHLIGHT, UNDERLINE, STRIKEOUT, INK }

/**
 * FR-032: one mark. Positions are fractions of the page, counted from the top left. Text marks use the
 * box; [INK][MarkupKind.INK] uses [strokes] (each a list of x, y fraction pairs) and the box is their bounds.
 */
data class PdfMarkup(
    val page: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val colorArgb: Int,
    /** The words under the mark — viewers show this in their annotation list. */
    val text: String = "",
    val kind: MarkupKind = MarkupKind.HIGHLIGHT,
    val strokes: List<List<Pair<Float, Float>>> = emptyList(),
)

/**
 * FR-032. Highlights are written as **real `/Highlight` annotations** rather than painted on. Painting
 * would bake them in and cover the text underneath, breaking selection and search in the copy; as
 * annotations any other viewer can read, move or delete them. Ported from pdfscanner `PdfMarkupWriter.kt`.
 */
object PdfMarkupWriter {

    fun write(
        input: InputStream,
        password: String?,
        marks: List<PdfMarkup>,
        target: File,
        scratchDirectory: File,
    ) {
        scratchDirectory.mkdirs()
        val memory = MemoryUsageSetting.setupMixed(MAIN_MEMORY_BYTES).setTempDir(scratchDirectory)
        val document = if (password == null) {
            PDDocument.load(input, memory)
        } else {
            PDDocument.load(input, password, memory)
        }
        document.use { open ->
            marks.filter { it.page in 0 until open.numberOfPages }.forEach { mark -> add(open, mark) }
            target.parentFile?.mkdirs()
            PdfSecurity.prepareForSave(open, password)
            open.save(target)
        }
    }

    private fun add(document: PDDocument, mark: PdfMarkup) {
        val page = document.getPage(mark.page)
        if (mark.kind == MarkupKind.INK) {
            addInk(document, page.mediaBox, mark)?.let { page.annotations.add(it) }
            return
        }
        val rect = rectangleOf(mark, page.mediaBox)
        val subtype = when (mark.kind) {
            MarkupKind.UNDERLINE -> PDAnnotationTextMarkup.SUB_TYPE_UNDERLINE
            MarkupKind.STRIKEOUT -> PDAnnotationTextMarkup.SUB_TYPE_STRIKEOUT
            else -> PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT
        }
        val annotation = PDAnnotationTextMarkup(subtype).apply {
            rectangle = rect
            color = colourOf(mark.colorArgb)
            constantOpacity = if (mark.kind == MarkupKind.HIGHLIGHT) HIGHLIGHT_OPACITY else 1f
            contents = mark.text
            // The quad points are what a viewer actually shades; the rectangle alone is not enough.
            quadPoints = floatArrayOf(
                rect.lowerLeftX, rect.upperRightY,
                rect.upperRightX, rect.upperRightY,
                rect.lowerLeftX, rect.lowerLeftY,
                rect.upperRightX, rect.lowerLeftY,
            )
        }
        if (mark.kind != MarkupKind.HIGHLIGHT) {
            // A drawn appearance, so viewers that do not generate one still show the line.
            val y = if (mark.kind == MarkupKind.UNDERLINE) {
                rect.lowerLeftY + rect.height * UNDERLINE_POSITION
            } else {
                rect.lowerLeftY + rect.height / 2f
            }
            val width = (rect.height * LINE_WEIGHT).coerceIn(MIN_LINE, MAX_LINE)
            annotation.appearance = appearance(document, rect, mark.colorArgb, width) { cs ->
                cs.moveTo(rect.lowerLeftX, y)
                cs.lineTo(rect.upperRightX, y)
            }
        }
        page.annotations.add(annotation)
    }

    /** A freehand drawing as a standard `/Ink` annotation with its own appearance stream. */
    private fun addInk(document: PDDocument, box: PDRectangle, mark: PdfMarkup): PDAnnotationMarkup? {
        val strokes = mark.strokes.filter { it.size >= 2 }.map { stroke ->
            stroke.map { (x, y) -> box.lowerLeftX + x * box.width to box.lowerLeftY + (1f - y) * box.height }
        }
        if (strokes.isEmpty()) return null
        val width = INK_WIDTH
        val xs = strokes.flatten().map { it.first }
        val ys = strokes.flatten().map { it.second }
        val rect = PDRectangle(
            xs.min() - width, ys.min() - width,
            xs.max() - xs.min() + 2 * width, ys.max() - ys.min() + 2 * width,
        )
        val inkList = COSArray()
        strokes.forEach { stroke ->
            val path = COSArray()
            stroke.forEach { (x, y) ->
                path.add(COSFloat(x))
                path.add(COSFloat(y))
            }
            inkList.add(path)
        }
        return PDAnnotationMarkup().apply {
            cosObject.setName(COSName.SUBTYPE, "Ink")
            cosObject.setItem(COSName.getPDFName("InkList"), inkList)
            cosObject.setItem(COSName.BS, PDBorderStyleDictionary().apply { this.width = width }.cosObject)
            rectangle = rect
            color = colourOf(mark.colorArgb)
            constantOpacity = 1f
            appearance = appearance(document, rect, mark.colorArgb, width) { cs ->
                strokes.forEach { stroke ->
                    cs.moveTo(stroke.first().first, stroke.first().second)
                    stroke.drop(1).forEach { (x, y) -> cs.lineTo(x, y) }
                }
            }
        }
    }

    /** A normal-appearance form whose bounding box is [rect], so page coordinates draw in place. */
    private fun appearance(
        document: PDDocument,
        rect: PDRectangle,
        argb: Int,
        lineWidth: Float,
        path: (PDPageContentStream) -> Unit,
    ): PDAppearanceDictionary {
        val stream = PDAppearanceStream(document).apply {
            bBox = rect
            resources = PDResources()
        }
        PDPageContentStream(document, stream).use { cs ->
            val c = colourOf(argb).components
            cs.setStrokingColor(c[0], c[1], c[2])
            cs.setLineWidth(lineWidth)
            cs.setLineCapStyle(ROUND)
            cs.setLineJoinStyle(ROUND)
            path(cs)
            cs.stroke()
        }
        return PDAppearanceDictionary().apply { setNormalAppearance(stream) }
    }

    /** Fractions counted from the top become points counted from the bottom. */
    private fun rectangleOf(mark: PdfMarkup, box: PDRectangle) = PDRectangle(
        box.lowerLeftX + mark.left * box.width,
        box.lowerLeftY + (1f - mark.bottom) * box.height,
        (mark.right - mark.left) * box.width,
        (mark.bottom - mark.top) * box.height,
    )

    private fun colourOf(argb: Int) = PDColor(
        floatArrayOf(
            ((argb shr RED_SHIFT) and BYTE_MASK) / BYTE_MAX,
            ((argb shr GREEN_SHIFT) and BYTE_MASK) / BYTE_MAX,
            (argb and BYTE_MASK) / BYTE_MAX,
        ),
        PDDeviceRGB.INSTANCE,
    )

    private const val MAIN_MEMORY_BYTES = 16L * 1024 * 1024
    private const val BYTE_MASK = 0xFF
    private const val BYTE_MAX = 255f
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8

    private const val UNDERLINE_POSITION = 0.08f
    private const val LINE_WEIGHT = 0.08f
    private const val MIN_LINE = 0.75f
    private const val MAX_LINE = 2.5f
    private const val INK_WIDTH = 2f
    private const val ROUND = 1

    /** Highlights are see-through by convention; a solid one would hide the words it marks. */
    private const val HIGHLIGHT_OPACITY = 0.4f
}
