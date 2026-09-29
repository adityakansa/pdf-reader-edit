package com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf

import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging.model.PdfLayout
import com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging.model.PdfPageSize
import com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging.model.PdfPlacement
import java.io.OutputStream
import java.io.File
import java.util.Calendar
import java.util.TimeZone

/** Options for one PDF (FR-041, FR-044). */
data class PdfOptions(
    val title: String,
    val pageSize: PdfPageSize = PdfPageSize.FIT,
    val marginPoints: Float = 0f,
)

/**
 * FR-044 / FR-041. Writes multi-page PDFs from JPEG page images with PdfBox-Android. JPEG data is
 * embedded as-is (DCTDecode), so a page is never recompressed, and each page is sized from its image.
 * Ported from pdfscanner `core/pdf/PdfWriter.kt` minus its encryption and invisible-OCR-layer paths.
 */
class PdfWriter(private val scratchDirectory: File) {

    /** Starts a document; add pages, then [PdfDocumentBuilder.writeTo]. Always close it. */
    fun begin(options: PdfOptions): PdfDocumentBuilder {
        scratchDirectory.mkdirs()
        val memory = MemoryUsageSetting.setupMixed(MAIN_MEMORY_BYTES).setTempDir(scratchDirectory)
        return PdfDocumentBuilder(PDDocument(memory), options)
    }

    private companion object {
        const val MAIN_MEMORY_BYTES = 32L * 1024 * 1024
    }
}

class PdfDocumentBuilder internal constructor(
    private val document: PDDocument,
    private val options: PdfOptions,
) : AutoCloseable {

    var pageCount: Int = 0
        private set

    /** Adds one page showing [jpeg] (an upright JPEG of [widthPixels] × [heightPixels]). */
    fun addJpegPage(
        jpeg: ByteArray,
        widthPixels: Int,
        heightPixels: Int,
        overlays: List<PdfOverlay> = emptyList(),
    ) {
        val placement = PdfLayout.place(widthPixels, heightPixels, options.pageSize, options.marginPoints)
        val page = PDPage(PDRectangle(placement.pageWidth, placement.pageHeight))
        document.addPage(page)
        val image = JPEGFactory.createFromByteArray(document, jpeg)
        PDPageContentStream(document, page).use { content ->
            content.drawImage(image, placement.imageX, placement.imageY, placement.imageWidth, placement.imageHeight)
            overlays.forEach { overlay -> drawOverlay(document, content, placement, overlay) }
        }
        pageCount++
    }

    fun writeTo(target: OutputStream) {
        check(pageCount > 0) { "A PDF needs at least one page" }
        document.documentInformation = PDDocumentInformation().apply {
            title = options.title
            creator = CREATOR
            producer = CREATOR
            creationDate = Calendar.getInstance(TimeZone.getDefault())
        }
        document.save(target)
    }

    override fun close() {
        document.close()
    }

    private companion object {
        const val CREATOR = "PDF Reader"
    }
}

/**
 * FR-053. Draws one overlay into a content stream: fractions become points against [placement], the
 * vertical axis is flipped (PDF counts up from the bottom), and rotation and opacity are applied about
 * the middle of the box, so the output matches what was on screen.
 */
internal fun drawOverlay(
    document: PDDocument,
    content: PDPageContentStream,
    placement: PdfPlacement,
    overlay: PdfOverlay,
) {
    val width = overlay.width * placement.imageWidth
    val height = overlay.height * placement.imageHeight
    if (width <= 0f || height <= 0f) return
    val x = placement.imageX + overlay.x * placement.imageWidth
    // The stored y counts down from the top of the image; PDF counts up from the bottom of the page.
    val bottom = placement.imageY + placement.imageHeight - (overlay.y * placement.imageHeight) - height

    content.saveGraphicsState()
    if (overlay.opacity < 1f) {
        content.setGraphicsStateParameters(
            PDExtendedGraphicsState().apply {
                nonStrokingAlphaConstant = overlay.opacity
                strokingAlphaConstant = overlay.opacity
            },
        )
    }
    // Rotate about the middle of the box rather than its corner, the way the screen does.
    content.transform(Matrix.getTranslateInstance(x + width / 2f, bottom + height / 2f))
    if (overlay.rotation != 0f) {
        // Screen rotation is clockwise, PDF's is anticlockwise.
        content.transform(Matrix.getRotateInstance(-Math.toRadians(overlay.rotation.toDouble()), 0f, 0f))
    }
    content.transform(Matrix.getTranslateInstance(-width / 2f, -height / 2f))

    when (overlay) {
        is PdfOverlay.Image -> {
            val image = PDImageXObject.createFromByteArray(document, overlay.png, null)
            content.drawImage(image, 0f, 0f, width, height)
        }

        is PdfOverlay.Text -> {
            val text = OverlayText.encodable(overlay.text)
            if (text.isNotEmpty()) {
                val font = PDType1Font.HELVETICA
                val size = OverlayText.fontSize(height)
                val natural = font.getStringWidth(text) / OverlayText.FONT_UNITS_PER_POINT * size
                content.setNonStrokingColor(
                    ((overlay.colorArgb shr 16) and 0xFF) / 255f,
                    ((overlay.colorArgb shr 8) and 0xFF) / 255f,
                    (overlay.colorArgb and 0xFF) / 255f,
                )
                content.beginText()
                content.setFont(font, size)
                content.setTextMatrix(
                    Matrix(
                        OverlayText.horizontalScale(width, natural),
                        0f, 0f, 1f, 0f, height * OverlayText.BASELINE,
                    ),
                )
                content.showText(text)
                content.endText()
            }
        }
    }
    content.restoreGraphicsState()
}
