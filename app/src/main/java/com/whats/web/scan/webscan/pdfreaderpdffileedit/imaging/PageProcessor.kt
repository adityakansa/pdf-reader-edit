package com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging

import com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging.model.PageFilter
import com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging.model.PageRotation
import com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging.model.Quad
import kotlin.math.max
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc

/** A processed page: encoded JPEG plus what the pipeline did, for logs and tests. */
class ProcessedPage(
    val jpeg: ByteArray,
    val width: Int,
    val height: Int,
    val deskewDegrees: Double,
    val filter: PageFilter,
)

/**
 * Turns an original capture into the page image that is shown and exported (DS-14):
 * decode (upright per EXIF) → perspective warp of the crop to a rectangle at capture resolution →
 * automatic deskew of small text tilt → filter (DS-15) → 90° rotation (DS-17) → JPEG. The original is never modified.
 *
 * Works in OpenCV matrices end to end: a 12 MP photo never becomes an ARGB Bitmap (48 MB), only a
 * 3-channel matrix. Safe to call from several threads: each call allocates its own matrices.
 */
object PageProcessor {

    private val openCvLoaded by lazy { OpenCVLoader.initLocal() }

    fun process(
        original: ByteArray,
        crop: Quad,
        filter: PageFilter = PageFilter.ORIGINAL,
        deskew: Boolean = true,
        rotation: PageRotation = PageRotation.NONE,
    ): ProcessedPage {
        check(openCvLoaded) { "OpenCV native library failed to load" }
        val encoded = MatOfByte(*original)
        // IMREAD_COLOR applies the EXIF orientation, so crops (set on the upright image) line up.
        val source = Imgcodecs.imdecode(encoded, Imgcodecs.IMREAD_COLOR)
        encoded.release()
        require(!source.empty()) { "Not a decodable image" }

        val warped = Mat()
        val straightened = Mat()
        try {
            warp(source, crop, warped)
            source.release()
            val angle = if (deskew) estimateSkew(warped) else 0.0
            val straight = if (angle != 0.0) rotate(warped, angle, straightened) else warped
            val filtered = PageFilters.apply(straight, filter)
            val turned = rotateQuarterTurns(filtered, rotation)
            try {
                return ProcessedPage(encode(turned), turned.cols(), turned.rows(), angle, filter)
            } finally {
                if (turned !== filtered) turned.release()
                filtered.release()
            }
        } finally {
            source.release()
            warped.release()
            straightened.release()
        }
    }

    private fun warp(source: Mat, crop: Quad, into: Mat) {
        val width = source.cols()
        val height = source.rows()
        if (crop == Quad.FULL_IMAGE) {
            source.copyTo(into)
            return
        }
        // Keep capture resolution: never shrink below the crop's own pixel size.
        val size = PerspectiveWarper.outputSize(crop, width, height, maxSide = max(width, height))
        val from =
            MatOfPoint2f(*crop.points.map { Point(it.x * width.toDouble(), it.y * height.toDouble()) }.toTypedArray())
        val to = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(size.width.toDouble(), 0.0),
            Point(size.width.toDouble(), size.height.toDouble()),
            Point(0.0, size.height.toDouble()),
        )
        val transform = Imgproc.getPerspectiveTransform(from, to)
        try {
            // Bicubic: straight text edges stay crisp where bilinear would soften and stair-step them.
            Imgproc.warpPerspective(
                source,
                into,
                transform,
                Size(size.width.toDouble(), size.height.toDouble()),
                Imgproc.INTER_CUBIC,
                Core.BORDER_REPLICATE,
            )
        } finally {
            from.release()
            to.release()
            transform.release()
        }
    }

    /**
     * Estimates text tilt on a downscaled copy: dark marks are joined horizontally into line-shaped blobs,
     * and near-horizontal segments along them vote through [SkewEstimator].
     */
    internal fun estimateSkew(page: Mat): Double {
        val gray = Mat()
        val small = Mat()
        val ink = Mat()
        val lines = Mat()
        try {
            Imgproc.cvtColor(page, gray, Imgproc.COLOR_BGR2GRAY)
            val scale = SKEW_WORKING_WIDTH / page.cols().toDouble()
            Imgproc.resize(gray, small, Size(), minOf(1.0, scale), minOf(1.0, scale), Imgproc.INTER_AREA)
            Imgproc.adaptiveThreshold(
                small,
                ink,
                MAX_PIXEL,
                Imgproc.ADAPTIVE_THRESH_MEAN_C,
                Imgproc.THRESH_BINARY_INV,
                THRESHOLD_BLOCK,
                THRESHOLD_OFFSET,
            )
            val join = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(JOIN_WIDTH, 1.0))
            Imgproc.morphologyEx(ink, ink, Imgproc.MORPH_CLOSE, join)
            join.release()
            Imgproc.HoughLinesP(
                ink,
                lines,
                1.0,
                Math.PI / HOUGH_ANGLE_STEPS,
                HOUGH_VOTES,
                small.cols() * MIN_LINE_FRACTION,
                LINE_GAP,
            )
            val angles = (0 until lines.rows()).map { row ->
                val (x1, y1, x2, y2) = lines.get(row, 0)
                SkewEstimator.segmentAngle(x1, y1, x2, y2)
            }
            return SkewEstimator.correctionDegrees(angles)
        } finally {
            gray.release()
            small.release()
            ink.release()
            lines.release()
        }
    }

    /**
     * Rotates by [degrees] about the centre, keeping the page size and filling uncovered corners with the
     * edge colour. OpenCV's positive angle is counter-clockwise, which undoes a positive (clockwise) tilt.
     */
    private fun rotate(page: Mat, degrees: Double, into: Mat): Mat {
        val centre = Point(page.cols() / 2.0, page.rows() / 2.0)
        val matrix = Imgproc.getRotationMatrix2D(centre, degrees, 1.0)
        Imgproc.warpAffine(page, into, matrix, page.size(), Imgproc.INTER_CUBIC, Core.BORDER_REPLICATE, Scalar.all(0.0))
        matrix.release()
        return into
    }

    /** Lossless 90° steps into a new matrix, or [page] itself when there is nothing to turn. */
    private fun rotateQuarterTurns(page: Mat, rotation: PageRotation): Mat {
        val code = when (rotation) {
            PageRotation.NONE -> return page
            PageRotation.CLOCKWISE_90 -> Core.ROTATE_90_CLOCKWISE
            PageRotation.HALF_TURN -> Core.ROTATE_180
            PageRotation.CLOCKWISE_270 -> Core.ROTATE_90_COUNTERCLOCKWISE
        }
        return Mat().also { Core.rotate(page, it, code) }
    }

    private fun encode(page: Mat): ByteArray {
        val out = MatOfByte()
        val params = MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, JPEG_QUALITY)
        try {
            check(Imgcodecs.imencode(".jpg", page, out, params)) { "JPEG encoding failed" }
            return out.toArray()
        } finally {
            out.release()
            params.release()
        }
    }

    private const val JPEG_QUALITY = 92
    private const val MAX_PIXEL = 255.0
    private const val SKEW_WORKING_WIDTH = 1000.0
    private const val THRESHOLD_BLOCK = 25
    private const val THRESHOLD_OFFSET = 15.0
    private const val JOIN_WIDTH = 25.0
    private const val HOUGH_ANGLE_STEPS = 1800.0
    private const val HOUGH_VOTES = 80
    private const val MIN_LINE_FRACTION = 0.25
    private const val LINE_GAP = 10.0
}
