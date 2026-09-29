package com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging

import android.graphics.Bitmap
import com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging.model.PageFilter
import kotlin.math.roundToInt
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfFloat
import org.opencv.core.MatOfInt
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * The basic page filters (FR-9, DS-15). Filters never touch the stored original: they run on a copy
 * of the warped page each time, so switching filters loses nothing.
 *
 * - Auto-enhance: evens out lighting (shadows, a darker corner) by dividing by one brightness background, then
 *   stretches contrast so paper is white and ink is dark. Colour hue is kept.
 * - Grayscale: plain conversion.
 * - Black & white: the same lighting correction, then a local mean threshold, so text stays crisp even where
 *   part of the page was in shadow.
 *
 * Pro (DS-16, hidden until Wave C):
 * - Colour restore: lighting evened out on each colour channel, then saturation lifted, for faded prints and
 *   yellowed paper.
 * - Shadow removal: each colour channel divided by its own background, which removes coloured casts and hand shadows
 *   more strongly than Auto while leaving ink colours alone.
 * - Sharpen: an unsharp mask on the lightness channel, for slightly soft captures.
 */
object PageFilters {

    private val openCvLoaded by lazy { OpenCVLoader.initLocal() }

    /** Filters a BGR page into a new matrix: 3-channel for colour filters, 1-channel for the others. */
    fun apply(page: Mat, filter: PageFilter): Mat {
        check(openCvLoaded) { "OpenCV native library failed to load" }
        return when (filter) {
            PageFilter.ORIGINAL -> page.clone()
            PageFilter.GRAYSCALE -> Mat().also { Imgproc.cvtColor(page, it, Imgproc.COLOR_BGR2GRAY) }
            PageFilter.AUTO_ENHANCE -> autoEnhance(page)
            PageFilter.BLACK_AND_WHITE -> blackAndWhite(page)
        }
    }

    /** The same filter on a display bitmap, for previews. */
    fun apply(bitmap: Bitmap, filter: PageFilter): Bitmap {
        check(openCvLoaded) { "OpenCV native library failed to load" }
        val rgba = Mat()
        val bgr = Mat()
        val shown = Mat()
        try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
            val filtered = apply(bgr, filter)
            val code = if (filtered.channels() == 1) Imgproc.COLOR_GRAY2RGBA else Imgproc.COLOR_BGR2RGBA
            Imgproc.cvtColor(filtered, shown, code)
            filtered.release()
            return Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888).also {
                Utils.matToBitmap(shown, it)
            }
        } finally {
            rgba.release()
            bgr.release()
            shown.release()
        }
    }

    /**
     * Evens out lighting by dividing every colour channel by the same brightness background, which keeps each pixel's
     * hue, then stretches contrast with one linear pass. Two full-resolution passes in total: converting a 12 MP page
     * to Lab and back, as this filter first did, took about four times as long on the test phone (DS-15 timing).
     */
    private fun autoEnhance(page: Mat): Mat {
        val gray = Mat()
        val background = Mat()
        val background3 = Mat()
        val result = Mat()
        try {
            Imgproc.cvtColor(page, gray, Imgproc.COLOR_BGR2GRAY)
            estimateBackground(gray, background)
            Imgproc.cvtColor(background, background3, Imgproc.COLOR_GRAY2BGR)
            Core.divide(page, background3, result, MAX_PIXEL, CvType.CV_8U)
            val (low, high) = stretchBoundsOf(result)
            if (high > low) {
                val gain = MAX_PIXEL / (high - low)
                result.convertTo(result, CvType.CV_8U, gain, -low * gain)
            }
            return result
        } finally {
            gray.release()
            background.release()
            background3.release()
        }
    }

    /** Contrast-stretch bounds of a (flattened) page, measured on a small grey copy: percentiles barely move. */
    private fun stretchBoundsOf(page: Mat): Pair<Int, Int> {
        val small = Mat()
        val gray = Mat()
        val histogram = Mat()
        val channelIndex = MatOfInt(0)
        val noMask = Mat()
        val bins = MatOfInt(LEVELS)
        val range = MatOfFloat(0f, LEVELS.toFloat())
        try {
            val scale = minOf(1.0, BACKGROUND_WORKING_WIDTH * 2 / page.cols())
            Imgproc.resize(page, small, Size(), scale, scale, Imgproc.INTER_AREA)
            if (small.channels() == 1) small.copyTo(gray) else Imgproc.cvtColor(small, gray, Imgproc.COLOR_BGR2GRAY)
            Imgproc.calcHist(listOf(gray), channelIndex, noMask, histogram, bins, range)
            val counts = IntArray(LEVELS) { histogram.get(it, 0)[0].toInt() }
            return contrastStretchBounds(counts, STRETCH_FRACTION)
        } finally {
            listOf(small, gray, histogram, channelIndex, noMask, bins, range).forEach(Mat::release)
        }
    }

    private fun blackAndWhite(page: Mat): Mat {
        val gray = Mat()
        try {
            Imgproc.cvtColor(page, gray, Imgproc.COLOR_BGR2GRAY)
            val flat = flattenLighting(gray)
            val result = Mat()
            Imgproc.adaptiveThreshold(
                flat,
                result,
                MAX_PIXEL,
                // Mean (a box filter, constant cost per pixel) rather than Gaussian: the lighting is already flat, and
                // a Gaussian over a 100 px block took ~850 ms on a 12 MP page on the test phone.
                Imgproc.ADAPTIVE_THRESH_MEAN_C,
                Imgproc.THRESH_BINARY,
                thresholdBlockSize(page.cols()),
                THRESHOLD_OFFSET,
            )
            flat.release()
            return result
        } finally {
            gray.release()
        }
    }

    /**
     * Divides a single channel by an estimate of its background (the paper under uneven light), so the
     * paper becomes evenly bright. The background is estimated on a small copy — text removed by a
     * dilation, smoothed by a median blur — then scaled back up: cheap even for 12 MP.
     */
    internal fun flattenLighting(channel: Mat): Mat {
        val background = Mat()
        val result = Mat()
        try {
            estimateBackground(channel, background)
            Core.divide(channel, background, result, MAX_PIXEL, CvType.CV_8U)
            return result
        } finally {
            background.release()
        }
    }

    /** The paper under the text, full size: text removed by a dilation and median blur on a small copy. */
    private fun estimateBackground(channel: Mat, into: Mat) {
        val small = Mat()
        try {
            val scale = BACKGROUND_WORKING_WIDTH / channel.cols().toDouble()
            Imgproc.resize(channel, small, Size(), minOf(1.0, scale), minOf(1.0, scale), Imgproc.INTER_AREA)
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(DILATE_KERNEL, DILATE_KERNEL))
            Imgproc.dilate(small, small, kernel)
            kernel.release()
            Imgproc.medianBlur(small, small, MEDIAN_KERNEL)
            Imgproc.resize(small, into, channel.size(), 0.0, 0.0, Imgproc.INTER_LINEAR)
        } finally {
            small.release()
        }
    }

    /** Stretches a single channel in place so its 1st and 99th percentiles become black and white. */
    internal fun stretchContrast(channel: Mat) {
        val histogram = Mat()
        val channelIndex = MatOfInt(0)
        val noMask = Mat()
        val bins = MatOfInt(LEVELS)
        val range = MatOfFloat(0f, LEVELS.toFloat())
        try {
            Imgproc.calcHist(listOf(channel), channelIndex, noMask, histogram, bins, range)
            val counts = IntArray(LEVELS) { histogram.get(it, 0)[0].toInt() }
            val (low, high) = contrastStretchBounds(counts, STRETCH_FRACTION)
            if (high > low) {
                val gain = MAX_PIXEL / (high - low)
                channel.convertTo(channel, CvType.CV_8U, gain, -low * gain)
            }
        } finally {
            listOf(histogram, channelIndex, noMask, bins, range).forEach(Mat::release)
        }
    }

    /**
     * The grey levels below which [fraction] of pixels fall and above which [fraction] of pixels rise.
     * Pure, so the stretch is tested without OpenCV.
     */
    fun contrastStretchBounds(histogram: IntArray, fraction: Double): Pair<Int, Int> {
        val total = histogram.sum().toLong()
        if (total == 0L) return 0 to histogram.lastIndex
        val cut = (total * fraction).roundToInt()
        var seen = 0L
        var low = 0
        while (low < histogram.lastIndex && seen + histogram[low] <= cut) seen += histogram[low++]
        seen = 0L
        var high = histogram.lastIndex
        while (high > 0 && seen + histogram[high] <= cut) seen += histogram[high--]
        return low to high
    }

    /** Adaptive-threshold neighbourhood: about 1/40 of the page width, odd, at least 15 px. */
    fun thresholdBlockSize(pageWidth: Int): Int {
        val size = maxOf(MIN_BLOCK, pageWidth / BLOCK_DIVISOR)
        return if (size % 2 == 0) size + 1 else size
    }

    private const val LEVELS = 256
    private const val MAX_PIXEL = 255.0
    private const val STRETCH_FRACTION = 0.01
    private const val THRESHOLD_OFFSET = 10.0
    private const val MIN_BLOCK = 15
    private const val BLOCK_DIVISOR = 40
    private const val BACKGROUND_WORKING_WIDTH = 500.0
    private const val DILATE_KERNEL = 7.0
    private const val MEDIAN_KERNEL = 21
}
