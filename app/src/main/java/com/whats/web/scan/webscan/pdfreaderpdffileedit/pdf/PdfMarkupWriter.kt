package com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf

import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup
import java.io.File
import java.io.InputStream

/** FR-032: one highlight. Positions are fractions of the page, counted from the top left. */
data class PdfMarkup(
    val page: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val colorArgb: Int,
    /** The words under the highlight — viewers show this in their annotation list. */
    val text: String = "",
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
            open.save(target)
        }
    }

    private fun add(document: PDDocument, mark: PdfMarkup) {
        val page = document.getPage(mark.page)
        val rect = rectangleOf(mark, page.mediaBox)
        val highlight = PDAnnotationTextMarkup(PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT).apply {
            rectangle = rect
            color = colourOf(mark.colorArgb)
            constantOpacity = HIGHLIGHT_OPACITY
            contents = mark.text
            // The quad points are what a viewer actually shades; the rectangle alone is not enough.
            quadPoints = floatArrayOf(
                rect.lowerLeftX, rect.upperRightY,
                rect.upperRightX, rect.upperRightY,
                rect.lowerLeftX, rect.lowerLeftY,
                rect.upperRightX, rect.lowerLeftY,
            )
        }
        page.annotations.add(highlight)
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

    /** Highlights are see-through by convention; a solid one would hide the words it marks. */
    private const val HIGHLIGHT_OPACITY = 0.4f
}
