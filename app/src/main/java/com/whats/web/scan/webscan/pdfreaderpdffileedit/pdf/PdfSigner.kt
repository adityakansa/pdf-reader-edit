package com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf

import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging.model.PdfPlacement
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * FR-053. Draws signatures and stamps onto an **existing** PDF — the path pdfscanner never had, since
 * it could only sign pages it had scanned itself.
 *
 * Each page is reopened in append mode (the pattern pdfscanner's `PdfWatermarker` uses), so everything
 * already on the page — text, its selectability, its fonts — is untouched and only the new marks are
 * added. Placements are fractions of the visible page, so they are mapped onto the page's `cropBox`
 * and its `/Rotate`, not onto the raw media box: a page that a scanner saved rotated 90° would
 * otherwise put the signature on its side and in the wrong corner.
 */
object PdfSigner {

    fun sign(
        input: InputStream,
        password: String?,
        overlays: Map<Int, List<PdfOverlay>>,
        target: OutputStream,
        scratchDirectory: File,
    ) {
        scratchDirectory.mkdirs()
        val memory = MemoryUsageSetting.setupMixed(MAIN_MEMORY_BYTES).setTempDir(scratchDirectory)
        val document = if (password == null) {
            PDDocument.load(input, memory)
        } else {
            PDDocument.load(input, password, memory)
        }
        document.use { pdf ->
            overlays.forEach { (pageIndex, marks) ->
                if (pageIndex !in 0 until pdf.numberOfPages || marks.isEmpty()) return@forEach
                val page = pdf.getPage(pageIndex)
                val box = page.cropBox ?: page.mediaBox
                val rotation = ((page.rotation % FULL_TURN) + FULL_TURN) % FULL_TURN

                PDPageContentStream(pdf, page, PDPageContentStream.AppendMode.APPEND, true, true).use { content ->
                    marks.forEach { overlay ->
                        drawOverlay(
                            document = pdf,
                            content = content,
                            // rotateForPage has already turned the fractions back into unrotated page space.
                            placement = placementFor(box.lowerLeftX, box.lowerLeftY, box.width, box.height),
                            overlay = rotateForPage(overlay, rotation),
                        )
                    }
                }
            }
            PdfSecurity.prepareForSave(pdf, password)
            pdf.save(target)
        }
    }

    /** The whole visible page is the "image" the overlay fractions are measured against. */
    private fun placementFor(x: Float, y: Float, width: Float, height: Float) =
        PdfPlacement(
            pageWidth = width,
            pageHeight = height,
            imageX = x,
            imageY = y,
            imageWidth = width,
            imageHeight = height,
        )

    /**
     * Maps a placement made on the *displayed* page onto page coordinates when the page carries a
     * `/Rotate`. The viewer showed the page turned; the content stream draws in the unturned space.
     */
    private fun rotateForPage(overlay: PdfOverlay, rotation: Int): PdfOverlay = when (rotation) {
        90 -> overlay.moved(
            x = 1f - overlay.y - overlay.height,
            y = overlay.x,
            width = overlay.height,
            height = overlay.width,
            rotation = overlay.rotation - 90f,
        )

        180 -> overlay.moved(
            x = 1f - overlay.x - overlay.width,
            y = 1f - overlay.y - overlay.height,
            width = overlay.width,
            height = overlay.height,
            rotation = overlay.rotation - 180f,
        )

        270 -> overlay.moved(
            x = overlay.y,
            y = 1f - overlay.x - overlay.width,
            width = overlay.height,
            height = overlay.width,
            rotation = overlay.rotation - 270f,
        )

        else -> overlay
    }

    private fun PdfOverlay.moved(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        rotation: Float,
    ): PdfOverlay = when (this) {
        is PdfOverlay.Image -> copy(x = x, y = y, width = width, height = height, rotation = rotation)
        is PdfOverlay.Text -> copy(x = x, y = y, width = width, height = height, rotation = rotation)
    }

    private const val MAIN_MEMORY_BYTES = 16L * 1024 * 1024
    private const val FULL_TURN = 360
}
