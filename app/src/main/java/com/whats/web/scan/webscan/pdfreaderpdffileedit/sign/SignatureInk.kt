package com.whats.web.scan.webscan.pdfreaderpdffileedit.sign

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * FR-051. Lifts pen strokes off paper: a photo of a signature becomes dark ink on transparency,
 * cropped to the ink. Ported from pdfscanner `feature/capture/.../camera/SignatureInk.kt`.
 */
object SignatureInk {

    /**
     * Turns a photo into a transparent-background signature, or null when the picture holds no ink
     * worth keeping (FR-051's "No signature found").
     */
    fun extract(source: Bitmap): Bitmap? {
        val width = source.width
        val height = source.height
        if (width <= 0 || height <= 0) return null
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val luma = FloatArray(pixels.size) { index ->
            val pixel = pixels[index]
            // Rec. 601 luma: the paper/ink split is a brightness question, not a colour one.
            0.299f * ((pixel shr RED_SHIFT) and BYTE_MASK) +
                0.587f * ((pixel shr GREEN_SHIFT) and BYTE_MASK) +
                0.114f * (pixel and BYTE_MASK)
        }
        // The paper is whatever most of the picture is; anything much darker is ink.
        val paper = luma.sorted()[(luma.size * PAPER_PERCENTILE).toInt().coerceIn(0, luma.lastIndex)]

        var inkPixels = 0
        for (index in pixels.indices) {
            val argb = if (paper > MIN_PAPER_LUMA) inkArgb(luma[index], paper) else fallbackInkArgb(luma[index])
            pixels[index] = argb
            if (alphaOf(argb) > INK_VISIBLE_ALPHA) inkPixels++
        }
        if (inkPixels < pixels.size * MIN_INK_FRACTION) return null

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return cropToInk(output, pixels)
    }

    internal fun inkArgb(luma: Float, paper: Float): Int {
        val transparentAbove = paper - BACKGROUND_MARGIN
        val opaqueBelow = transparentAbove * INK_FRACTION
        return when {
            luma >= transparentAbove -> TRANSPARENT
            luma <= opaqueBelow -> OPAQUE_BLACK
            else -> {
                val span = (transparentAbove - opaqueBelow).coerceAtLeast(1f)
                val alpha = (transparentAbove - luma) / span * ALPHA_MAX
                alpha.roundToInt().coerceIn(0, ALPHA_MAX.toInt()) shl ALPHA_SHIFT
            }
        }
    }

    internal fun fallbackInkArgb(luma: Float): Int =
        if (luma < FALLBACK_INK_CUT) OPAQUE_BLACK else TRANSPARENT

    internal fun alphaOf(color: Int): Int = (color ushr ALPHA_SHIFT) and BYTE_MASK

    internal fun cropToInk(source: Bitmap, pixels: IntArray): Bitmap {
        var minX = source.width
        var minY = source.height
        var maxX = -1
        var maxY = -1
        val width = source.width
        for (i in pixels.indices) {
            if (alphaOf(pixels[i]) <= INK_VISIBLE_ALPHA) continue
            val x = i % width
            val y = i / width
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
        }
        if (maxX < minX) return source
        val pad = max((width * INK_PAD_FRACTION).roundToInt(), 1)
        val left = (minX - pad).coerceAtLeast(0)
        val top = (minY - pad).coerceAtLeast(0)
        val right = (maxX + pad).coerceAtMost(source.width - 1)
        val bottom = (maxY + pad).coerceAtMost(source.height - 1)
        val cropped = Bitmap.createBitmap(source, left, top, right - left + 1, bottom - top + 1)
        if (cropped !== source) source.recycle()
        return cropped
    }

    internal const val INK_VISIBLE_ALPHA = 16
    internal const val MIN_INK_FRACTION = 0.002f
    private const val PAPER_PERCENTILE = 0.9f
    private const val MIN_PAPER_LUMA = 60f
    private const val BACKGROUND_MARGIN = 35f
    private const val INK_FRACTION = 0.75f
    private const val ALPHA_MAX = 255f
    private const val INK_PAD_FRACTION = 0.04f
    private const val FALLBACK_INK_CUT = 140f
    private const val TRANSPARENT = 0
    private const val OPAQUE_BLACK = 0xFF000000.toInt()
    private const val ALPHA_SHIFT = 24
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val BYTE_MASK = 0xFF
}
