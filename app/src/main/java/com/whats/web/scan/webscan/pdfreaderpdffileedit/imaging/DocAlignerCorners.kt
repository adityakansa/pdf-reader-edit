package com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging.model.NormalizedPoint
import com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging.model.Quad
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import org.tensorflow.lite.Interpreter

/**
 * On-device document corners via DocAligner `lcnet100` (Apache-2.0, DocsaidLab).
 *
 * Input `[1,256,256,3]` NHWC float32 RGB in `[0,1]`; output `[1,128,128,4]` corner heatmaps
 * ordered TL, TR, BR, BL. Decode is argmax + local soft-argmax (refine radius 2).
 *
 * Live frames run at camera rate, so every per-frame allocation matters: the luma sampling grid is
 * precomputed per frame geometry, and the heatmaps come back in a direct [ByteBuffer] rather than a
 * boxed `Array<Array<FloatArray>>` that TFLite would have to fill element by element.
 *
 * Not thread-safe across callers: [DocumentDetector] serialises access.
 */
internal class DocAlignerCorners(context: Context) : AutoCloseable {

    private val interpreter: Interpreter
    private val inputBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(INPUT_BYTES).order(ByteOrder.nativeOrder())
    private val outputBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(OUTPUT_BYTES).order(ByteOrder.nativeOrder())
    private val outputFloats: FloatBuffer = outputBuffer.asFloatBuffer()
    private val heatmaps = FloatArray(HEATMAP_SIDE * HEATMAP_SIDE * CORNERS)

    /** Scratch for [fillInputFromBitmap]; the model input is a fixed size, so this is allocated once. */
    private val pixels = IntArray(INPUT_SIDE * INPUT_SIDE)

    /**
     * Buffer offset to sample for each of the 256×256 model input pixels. Rebuilt only when the frame
     * geometry changes, which in practice means once per camera session.
     */
    private var sampleOffsets = IntArray(0)
    private var gridWidth = -1
    private var gridHeight = -1
    private var gridStride = -1
    private var gridRotation = -1

    init {
        val options = Interpreter.Options().apply {
            setNumThreads(THREADS)
            // Float32 convolutions run several times faster through XNNPACK than the reference
            // kernels, and this is the whole per-frame budget for live detection.
            setUseXNNPACK(true)
        }
        interpreter = Interpreter(loadModel(context.applicationContext), options)
    }

    /** Still image (capture / import / page review). */
    fun detect(bitmap: Bitmap): Quad? {
        fillInputFromBitmap(bitmap)
        return runAndDecode()
    }

    /**
     * Live camera luma plane. DocAligner was trained on RGB; feeding Y as grey RGB is accurate
     * enough for the outline and keeps analysis off the colour-conversion path.
     */
    fun detectLuma(luma: ByteBuffer, width: Int, height: Int, rowStride: Int, rotationDegrees: Int): Quad? {
        fillInputFromLuma(luma, width, height, rowStride, rotationDegrees)
        return runAndDecode()
    }

    private fun runAndDecode(): Quad? {
        inputBuffer.rewind()
        outputBuffer.rewind()
        interpreter.run(inputBuffer, outputBuffer)
        outputFloats.rewind()
        outputFloats.get(heatmaps)
        return decodeCorners(heatmaps)
    }

    private fun fillInputFromBitmap(bitmap: Bitmap) {
        val scaled = if (bitmap.width == INPUT_SIDE && bitmap.height == INPUT_SIDE) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, INPUT_SIDE, INPUT_SIDE, true)
        }
        inputBuffer.rewind()
        scaled.getPixels(pixels, 0, INPUT_SIDE, 0, 0, INPUT_SIDE, INPUT_SIDE)
        for (pixel in pixels) {
            inputBuffer.putFloat(Color.red(pixel) / CHANNEL_MAX)
            inputBuffer.putFloat(Color.green(pixel) / CHANNEL_MAX)
            inputBuffer.putFloat(Color.blue(pixel) / CHANNEL_MAX)
        }
        if (scaled !== bitmap) scaled.recycle()
    }

    private fun fillInputFromLuma(luma: ByteBuffer, width: Int, height: Int, rowStride: Int, rotationDegrees: Int) {
        buildSampleGrid(width, height, rowStride, rotationDegrees)
        val source = luma.duplicate()
        inputBuffer.rewind()
        val offsets = sampleOffsets
        for (i in offsets.indices) {
            val v = (source.get(offsets[i]).toInt() and BYTE_MASK) / CHANNEL_MAX
            inputBuffer.putFloat(v)
            inputBuffer.putFloat(v)
            inputBuffer.putFloat(v)
        }
    }

    /**
     * Precomputes which buffer byte each model input pixel reads, undoing the clockwise rotation that
     * makes the frame upright (inverse of [QuadGeometry.toUprightQuad]). Doing this per frame cost
     * 65k boxed pairs of doubles, which dominated the live detection budget.
     */
    private fun buildSampleGrid(width: Int, height: Int, rowStride: Int, rotationDegrees: Int) {
        val unchanged = gridWidth == width && gridHeight == height &&
            gridStride == rowStride && gridRotation == rotationDegrees
        if (unchanged) return
        if (sampleOffsets.size != INPUT_SIDE * INPUT_SIDE) {
            sampleOffsets = IntArray(INPUT_SIDE * INPUT_SIDE)
        }
        var i = 0
        for (outY in 0 until INPUT_SIDE) {
            for (outX in 0 until INPUT_SIDE) {
                val u = (outX + PIXEL_CENTER_D) / INPUT_SIDE
                val v = (outY + PIXEL_CENTER_D) / INPUT_SIDE
                val sx: Double
                val sy: Double
                when (rotationDegrees) {
                    ROTATION_90 -> {
                        sx = v
                        sy = UNIT - u
                    }

                    ROTATION_180 -> {
                        sx = UNIT - u
                        sy = UNIT - v
                    }

                    ROTATION_270 -> {
                        sx = UNIT - v
                        sy = u
                    }

                    else -> {
                        sx = u
                        sy = v
                    }
                }
                val sampleX = (sx * (width - 1)).toInt().coerceIn(0, width - 1)
                val sampleY = (sy * (height - 1)).toInt().coerceIn(0, height - 1)
                sampleOffsets[i++] = sampleY * rowStride + sampleX
            }
        }
        gridWidth = width
        gridHeight = height
        gridStride = rowStride
        gridRotation = rotationDegrees
    }

    override fun close() {
        interpreter.close()
    }

    companion object {
        const val MODEL_ASSET = "docaligner_lcnet100.tflite"
        private const val INPUT_SIDE = 256
        private const val HEATMAP_SIDE = 128
        private const val CORNERS = 4
        private const val CORNER_TL = 0
        private const val CORNER_TR = 1
        private const val CORNER_BR = 2
        private const val CORNER_BL = 3

        /** Four threads on the little+big cluster roughly halves inference against two. */
        private const val THREADS = 4
        private const val REFINE_RADIUS = 2
        private const val PIXEL_CENTER = 0.5f
        private const val PIXEL_CENTER_D = 0.5
        private const val UNIT = 1.0
        private const val CHANNEL_MAX = 255f
        private const val BYTE_MASK = 0xFF
        private const val ROTATION_90 = 90
        private const val ROTATION_180 = 180
        private const val ROTATION_270 = 270
        private const val FLOAT_BYTES = 4
        private const val INPUT_BYTES = 1 * INPUT_SIDE * INPUT_SIDE * 3 * FLOAT_BYTES
        private const val OUTPUT_BYTES = 1 * HEATMAP_SIDE * HEATMAP_SIDE * CORNERS * FLOAT_BYTES

        /**
         * A corner heatmap peak below this is the model saying "no page here". Without the gate the
         * soft-argmax still returns four points on an empty frame, and a bogus quad that happens to
         * pass the geometry checks makes the outline dance over nothing.
         */
        private const val MIN_PEAK = 0.25f

        private fun loadModel(context: Context): MappedByteBuffer {
            context.assets.openFd(MODEL_ASSET).use { fd ->
                FileInputStream(fd.fileDescriptor).channel.use { channel ->
                    return channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                }
            }
        }

        /**
         * Soft-argmax peaks → normalised upright [Quad], or null when no corner stands out far enough
         * from the background for the frame to hold a page.
         *
         * [heatmaps] is the flat `[128,128,4]` output, indexed `(row * 128 + col) * 4 + corner`.
         */
        @Suppress("NestedBlockDepth")
        fun decodeCorners(heatmaps: FloatArray): Quad? {
            val points = Array(CORNERS) { NormalizedPoint(PIXEL_CENTER, PIXEL_CENTER) }
            for (k in 0 until CORNERS) {
                var rMax = 0
                var cMax = 0
                var vMax = Float.NEGATIVE_INFINITY
                for (r in 0 until HEATMAP_SIDE) {
                    val rowBase = r * HEATMAP_SIDE * CORNERS + k
                    for (c in 0 until HEATMAP_SIDE) {
                        val v = heatmaps[rowBase + c * CORNERS]
                        if (v > vMax) {
                            vMax = v
                            rMax = r
                            cMax = c
                        }
                    }
                }
                if (vMax < MIN_PEAK) return null
                var fr = rMax.toFloat()
                var fc = cMax.toFloat()
                var sum = 0f
                var sumR = 0f
                var sumC = 0f
                for (dr in -REFINE_RADIUS..REFINE_RADIUS) {
                    @Suppress("LoopWithTooManyJumpStatements")
                    for (dc in -REFINE_RADIUS..REFINE_RADIUS) {
                        val r = rMax + dr
                        val c = cMax + dc
                        if (r !in 0 until HEATMAP_SIDE || c !in 0 until HEATMAP_SIDE) continue
                        val v = heatmaps[(r * HEATMAP_SIDE + c) * CORNERS + k]
                        if (v <= 0f) continue
                        sum += v
                        sumR += v * r
                        sumC += v * c
                    }
                }
                if (sum > 0f) {
                    fr = sumR / sum
                    fc = sumC / sum
                }
                points[k] = NormalizedPoint(
                    ((fc + PIXEL_CENTER) / HEATMAP_SIDE).coerceIn(0f, 1f),
                    ((fr + PIXEL_CENTER) / HEATMAP_SIDE).coerceIn(0f, 1f),
                )
            }
            return Quad(
                points[CORNER_TL],
                points[CORNER_TR],
                points[CORNER_BR],
                points[CORNER_BL],
            )
        }
    }
}
