package com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging

import android.graphics.Bitmap
import com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging.model.Quad
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/** Width and height in pixels. */
data class PixelSize(val width: Int, val height: Int)

/**
 * Flattens the quad of a photographed page into an upright rectangle (FR-8). DS-13 uses it for the
 * live preview; DS-14 builds the full-resolution pipeline on it.
 */
object PerspectiveWarper {

    /** Warps [quad] of [source] into a rectangle whose longer side is at most [maxSide] pixels. */
    fun warp(source: Bitmap, quad: Quad, maxSide: Int): Bitmap {
        check(openCvLoaded) { "OpenCV native library failed to load" }
        val size = outputSize(quad, source.width, source.height, maxSide)
        val src = Mat()
        val dst = Mat()
        val from =
            MatOfPoint2f(
                *quad.points.map {
                    Point(it.x * source.width.toDouble(), it.y * source.height.toDouble())
                }.toTypedArray(),
            )
        val to = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(size.width.toDouble(), 0.0),
            Point(size.width.toDouble(), size.height.toDouble()),
            Point(0.0, size.height.toDouble()),
        )
        val transform = Imgproc.getPerspectiveTransform(from, to)
        try {
            Utils.bitmapToMat(source, src)
            Imgproc.warpPerspective(
                src,
                dst,
                transform,
                Size(size.width.toDouble(), size.height.toDouble()),
                Imgproc.INTER_LINEAR,
            )
            return Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888).also {
                Utils.matToBitmap(dst, it)
            }
        } finally {
            src.release()
            dst.release()
            from.release()
            to.release()
            transform.release()
        }
    }

    // Loaded on first warp, not when the class loads, so the pure sizing maths also runs in JVM tests.
    private val openCvLoaded by lazy { OpenCVLoader.initLocal() }

    /**
     * The flattened page's size: as wide as the quad's longer horizontal edge and as tall as its longer
     * vertical edge, so no side of the page is squashed, then scaled down to fit [maxSide].
     */
    fun outputSize(quad: Quad, imageWidth: Int, imageHeight: Int, maxSide: Int): PixelSize {
        fun length(a: Int, b: Int): Double {
            val p = quad.points[a]
            val q = quad.points[b]
            return hypot(((p.x - q.x) * imageWidth).toDouble(), ((p.y - q.y) * imageHeight).toDouble())
        }
        val width = max(length(0, 1), length(3, 2))
        val height = max(length(0, 3), length(1, 2))
        val scale = minOf(1.0, maxSide / max(width, height))
        return PixelSize(
            width = (width * scale).roundToInt().coerceAtLeast(1),
            height = (height * scale).roundToInt().coerceAtLeast(1),
        )
    }
}
