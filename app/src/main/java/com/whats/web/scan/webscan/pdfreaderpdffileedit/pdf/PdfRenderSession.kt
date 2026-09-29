package com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Closeable

/** A bitmap size in pixels. */
data class RenderSize(val width: Int, val height: Int)

/** Page size in PDF points (1/72 inch), as the file declares it. */
data class PdfPageDimensions(val widthPoints: Int, val heightPoints: Int) {
    val aspectRatio: Float get() = widthPoints.toFloat() / heightPoints
}

/**
 * FR-030. One open PDF rendered by the platform renderer. A renderer may have only one page open at a
 * time, so every call is serialised here; callers render from a background dispatcher. Ported from
 * pdfscanner `core/pdf/render/PdfRenderSession.kt`.
 */
class PdfRenderSession private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) : Closeable {

    private val lock = Mutex()

    val pageCount: Int = renderer.pageCount

    /** Sizes of every page, read once: the viewer lays out before anything is rendered. */
    suspend fun pageSizes(): List<PdfPageDimensions> = lock.withLock {
        (0 until pageCount).map { index ->
            renderer.openPage(index).use { PdfPageDimensions(it.width, it.height) }
        }
    }

    /**
     * Renders page [index] into a bitmap [widthPixels] wide, keeping its aspect ratio. Drawn onto white
     * first: PDF pages are transparent where nothing is painted, which would otherwise show as black.
     */
    suspend fun renderPage(index: Int, widthPixels: Int): Bitmap = lock.withLock {
        require(index in 0 until pageCount) { "No page $index in a $pageCount page document" }
        renderer.openPage(index).use { page ->
            val size = renderSize(PdfPageDimensions(page.width, page.height), widthPixels)
            val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        }
    }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }

    companion object {
        /**
         * Throws [java.io.IOException] for a damaged file and [SecurityException] for an encrypted one,
         * which [PdfAccess] handles by decrypting to a temporary copy first.
         */
        fun open(descriptor: ParcelFileDescriptor): PdfRenderSession =
            PdfRenderSession(descriptor, PdfRenderer(descriptor))

        /** The bitmap size for a page shown [widthPixels] wide, capped so one page stays under [maxPixels]. */
        fun renderSize(
            page: PdfPageDimensions,
            widthPixels: Int,
            maxPixels: Int = MAX_PIXELS_PER_PAGE,
        ): RenderSize {
            val height = (widthPixels / page.aspectRatio).toInt().coerceAtLeast(1)
            val pixels = widthPixels.toLong() * height
            if (pixels <= maxPixels) return RenderSize(widthPixels.coerceAtLeast(1), height)
            val scale = kotlin.math.sqrt(maxPixels.toDouble() / pixels).toFloat()
            return RenderSize(
                (widthPixels * scale).toInt().coerceAtLeast(1),
                (height * scale).toInt().coerceAtLeast(1),
            )
        }

        /** About 8 MP: a full-screen page on a tablet at 2× zoom, 32 MB as ARGB_8888. */
        const val MAX_PIXELS_PER_PAGE = 8_000_000
    }
}
