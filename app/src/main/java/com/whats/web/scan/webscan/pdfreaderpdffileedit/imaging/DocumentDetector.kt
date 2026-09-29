package com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging.model.Quad
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.max
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.CLAHE
import org.opencv.imgproc.Imgproc

/**
 * Finds the document page in a camera frame or still (FR-1, DS-8).
 *
 * Prefer **DocAligner** (Apache-2.0 TFLite corner heatmaps) on stills when a [Context] is supplied
 * so the model can load from assets — that path handles plastic sleeves, clutter and soft shadows
 * that defeat classical edges. Live camera frames use classical OpenCV so the overlay can keep up
 * with the preview. OpenCV is also the fallback when the model is missing or rejects a still, and
 * for unit tests that construct this without a Context.
 *
 * Returns the quad in normalised coordinates of the upright image, or null when no page is visible.
 * Not thread-safe (native buffers + TFLite interpreter): one instance per analysis thread.
 */
class DocumentDetector(context: Context? = null) {

    private val ml: DocAlignerCorners? = context?.applicationContext?.let { app ->
        runCatching { DocAlignerCorners(app) }
            .onFailure { Log.w(TAG, "DocAligner unavailable; using OpenCV edges only", it) }
            .getOrNull()
    }

    /** Milliseconds the last frame spent in the model, for the analyzer's throughput log. */
    @Volatile
    var lastModelMillis: Long = 0L
        private set

    /** Milliseconds the last frame spent in the OpenCV path used for live preview. */
    @Volatile
    var lastFallbackMillis: Long = 0L
        private set

    private var rowBuffer = ByteArray(0)
    private var edgeBuffer = ByteArray(0)
    private val gray = Mat()
    private val small = Mat()
    private val closed = Mat()
    private val binary = Mat()
    private val edges = Mat()
    private val hierarchy = Mat()
    private val closeKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(CLOSE_KERNEL, CLOSE_KERNEL))
    private val dilateKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(DILATE_KERNEL, DILATE_KERNEL))
    private val clahe: CLAHE = Imgproc.createCLAHE(CLAHE_CLIP, Size(CLAHE_TILE, CLAHE_TILE))

    /**
     * Detects the page in a luma plane. [rowStride] may exceed [width] (camera buffers pad rows);
     * [rotationDegrees] is how far the buffer must turn clockwise to be upright.
     */
    fun detect(luma: ByteBuffer, width: Int, height: Int, rowStride: Int, rotationDegrees: Int): Quad? =
        synchronized(this) {
            // Live preview is OpenCV only. DocAligner is accurate on stills but several hundred
            // milliseconds a frame on mid-range phones, which starves the overlay (one or two
            // updates a second looks like Edges is off). The model still runs on captured pages.
            lastModelMillis = 0L
            val fallbackStarted = SystemClock.elapsedRealtime()
            try {
                copyLuma(luma, width, height, rowStride)
                return detectInGray(rotationDegrees)
            } finally {
                lastFallbackMillis = SystemClock.elapsedRealtime() - fallbackStarted
            }
        }

    /** Detects the page in a still image, such as a captured or imported page (DS-13). */
    fun detect(bitmap: Bitmap): Quad? = synchronized(this) {
        ml?.detect(bitmap)?.takeIf { it.isPlausiblePage() }?.let { return it }

        val rgba = Mat()
        try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        } finally {
            rgba.release()
        }
        return detectInGray(rotationDegrees = 0)
    }

    private fun copyLuma(luma: ByteBuffer, width: Int, height: Int, rowStride: Int) {
        val size = width * height
        if (rowBuffer.size != size) rowBuffer = ByteArray(size)
        val source = luma.duplicate()
        if (rowStride == width && source.remaining() >= size) {
            source.get(rowBuffer, 0, size)
        } else {
            for (row in 0 until height) {
                source.position(row * rowStride)
                source.get(rowBuffer, row * width, width)
            }
        }
        if (gray.rows() != height || gray.cols() != width) gray.create(height, width, CvType.CV_8UC1)
        gray.put(0, 0, rowBuffer)
    }

    private fun detectInGray(rotationDegrees: Int): Quad? {
        val scale = WORKING_LONG_SIDE / max(gray.cols(), gray.rows()).toDouble()
        if (scale < 1.0) {
            Imgproc.resize(gray, small, Size(), scale, scale, Imgproc.INTER_AREA)
        } else {
            gray.copyTo(small)
        }

        Imgproc.GaussianBlur(small, small, Size(BLUR_KERNEL, BLUR_KERNEL), 0.0)
        clahe.apply(small, small)
        Imgproc.morphologyEx(small, closed, Imgproc.MORPH_CLOSE, closeKernel)
        val otsu = Imgproc.threshold(closed, binary, 0.0, MAX_PIXEL, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
        val high = otsu.coerceAtLeast(CANNY_HIGH_FLOOR)
        Imgproc.Canny(closed, edges, high * CANNY_LOW_RATIO, high)
        Imgproc.dilate(edges, edges, dilateKernel)

        val byEdges = bestQuad(edges, Imgproc.RETR_LIST, requireEdgeSupport = true)
        val ordered = byEdges ?: run {
            // Soft shadows often break Canny along a side; the page is still one bright blob after Otsu.
            Imgproc.dilate(binary, binary, dilateKernel)
            bestQuad(binary, Imgproc.RETR_EXTERNAL, requireEdgeSupport = false)
        }
        return ordered?.let { QuadGeometry.toUprightQuad(it, small.cols(), small.rows(), rotationDegrees) }
    }

    private fun bestQuad(source: Mat, mode: Int, requireEdgeSupport: Boolean): List<Pt>? {
        val edgeCols = source.cols()
        val edgeRows = source.rows()
        if (edgeBuffer.size != edgeCols * edgeRows) edgeBuffer = ByteArray(edgeCols * edgeRows)
        source.get(0, 0, edgeBuffer)
        val isEdge = { x: Int, y: Int ->
            x in 0 until edgeCols && y in 0 until edgeRows &&
                edgeBuffer[y * edgeCols + x] != 0.toByte()
        }

        val contours = ArrayList<MatOfPoint>()
        Imgproc.findContours(source, contours, hierarchy, mode, Imgproc.CHAIN_APPROX_SIMPLE)
        val frameArea = (small.cols() * small.rows()).toDouble()
        return try {
            contours.asSequence()
                .filter { Imgproc.boundingRect(it).area() >= frameArea * MIN_AREA_FRACTION }
                .mapNotNull { quadFrom(it) }
                .filter { it.isPlausiblePage(frameArea) }
                .mapNotNull { quad ->
                    val support = QuadGeometry.weakestSideEdgeSupport(quad, EDGE_SAMPLES_PER_SIDE, isEdge)
                    if (requireEdgeSupport && support < MIN_EDGE_SUPPORT) return@mapNotNull null
                    quad to score(quad, frameArea, support)
                }
                .maxByOrNull { it.second }
                ?.first
        } finally {
            contours.forEach { it.release() }
        }
    }

    /** The contour's convex hull simplified to four corners, trying progressively coarser tolerances. */
    private fun quadFrom(contour: MatOfPoint): List<Pt>? {
        val hullIndices = MatOfInt()
        val hull = MatOfPoint2f()
        val approx = MatOfPoint2f()
        try {
            Imgproc.convexHull(contour, hullIndices)
            val points = contour.toArray()
            hull.fromList(hullIndices.toArray().map { points[it] })
            // A page's outline fills its own hull; scattered clutter only outlines a thin part of it.
            if (Imgproc.contourArea(contour) < Imgproc.contourArea(hull) * MIN_HULL_FILL) return null
            val perimeter = Imgproc.arcLength(hull, true)
            for (tolerance in APPROX_TOLERANCES) {
                Imgproc.approxPolyDP(hull, approx, perimeter * tolerance, true)
                if (approx.rows() == QuadGeometry.CORNERS) {
                    return QuadGeometry.orderClockwise(approx.toArray().map(Point::toPt))
                }
                if (approx.rows() < QuadGeometry.CORNERS) return null
            }
            return null
        } finally {
            hullIndices.release()
            hull.release()
            approx.release()
        }
    }

    /**
     * How page-like a candidate is: big, square-cornered and backed by real edges all the way round.
     *
     * Area alone picks the wrong quad whenever the page sits on a darker desk mat or next to a
     * keyboard — the larger surrounding blob wins even though its corners are nothing like right
     * angles. Weighting by rectangularity and edge support keeps the page.
     */
    private fun score(quad: List<Pt>, frameArea: Double, edgeSupport: Double): Double {
        val areaFraction = QuadGeometry.area(quad) / frameArea
        val squareness = QuadGeometry.interiorAngles(quad)
            .maxOf { abs(it - RIGHT_ANGLE) }
            .let { worst -> (1.0 - worst / RIGHT_ANGLE).coerceIn(0.0, 1.0) }
        return areaFraction * (EDGE_WEIGHT_BASE + (1 - EDGE_WEIGHT_BASE) * edgeSupport) * squareness
    }

    private fun List<Pt>.isPlausiblePage(frameArea: Double): Boolean {
        val fraction = QuadGeometry.area(this) / frameArea
        return fraction in MIN_AREA_FRACTION..MAX_AREA_FRACTION &&
            QuadGeometry.interiorAngles(this).all { it in MIN_CORNER_ANGLE..MAX_CORNER_ANGLE }
    }

    companion object {
        init {
            check(OpenCVLoader.initLocal()) { "OpenCV native library failed to load" }
        }

        private const val TAG = "DocumentDetector"

        /** Detection runs on a copy this size; enough for accurate corners, small enough for speed. */
        const val WORKING_LONG_SIDE = 640.0

        /** A page smaller than this share of the frame is too far away to scan well, or not a page. */
        const val MIN_AREA_FRACTION = 0.08
        private const val MAX_AREA_FRACTION = 0.98
        private const val MIN_CORNER_ANGLE = 32.0
        private const val MAX_CORNER_ANGLE = 148.0
        private const val BLUR_KERNEL = 5.0
        private const val CLOSE_KERNEL = 9.0
        private const val DILATE_KERNEL = 3.0
        private const val MAX_PIXEL = 255.0
        private const val CANNY_LOW_RATIO = 0.4
        private const val CANNY_HIGH_FLOOR = 40.0
        private const val CLAHE_CLIP = 2.0
        private const val CLAHE_TILE = 8.0
        private val APPROX_TOLERANCES = doubleArrayOf(0.02, 0.03, 0.04, 0.05, 0.06, 0.08)

        /**
         * A contour must cover at least this share of its convex hull to be an outline rather than clutter.
         * Softened again for real sheets with folds/shadows (device feedback 2026-09-19).
         */
        private const val MIN_HULL_FILL = 0.60

        /**
         * Every side must run along detected edges for at least this share of its length (edge pass only).
         * Softened for soft shadows that break Canny continuity on white A4.
         */
        private const val MIN_EDGE_SUPPORT = 0.25
        private const val EDGE_SAMPLES_PER_SIDE = 40

        private const val RIGHT_ANGLE = 90.0

        /** Floor on the edge-support factor, so a shadow-broken side cannot zero out a good candidate. */
        private const val EDGE_WEIGHT_BASE = 0.5
    }
}

/** Same geometry gates for DocAligner normalised quads (unit-square shoelace). */
private fun Quad.isPlausiblePage(): Boolean {
    val pts = points.map { Pt(it.x.toDouble(), it.y.toDouble()) }
    val fraction = QuadGeometry.area(pts)
    return fraction in DocumentDetector.MIN_AREA_FRACTION..MAX_AREA_FRACTION &&
        QuadGeometry.interiorAngles(pts).all { it in MIN_CORNER_ANGLE..MAX_CORNER_ANGLE }
}

private const val MAX_AREA_FRACTION = 0.98
private const val MIN_CORNER_ANGLE = 32.0
private const val MAX_CORNER_ANGLE = 148.0

private fun Point.toPt() = Pt(x, y)
