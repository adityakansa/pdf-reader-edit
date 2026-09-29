package com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging

import com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging.model.NormalizedPoint
import com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging.model.Quad

/**
 * Turns noisy per-frame detections into a steady overlay (DS-8: no flicker while the page is still).
 *
 * - Small frame-to-frame jitter is averaged away (exponential moving average).
 * - A large jump — the page moved, or a different page — snaps straight to the new position, so the
 *   overlay never lags visibly behind a real movement.
 * - A frame that misses the page keeps the last quad for [holdMillis], so a single failed frame (motion
 *   blur, a finger crossing an edge) does not make the outline blink.
 *
 * Not thread-safe: feed it from the single analysis thread.
 */
class QuadSmoother(
    private val smoothing: Float = DEFAULT_SMOOTHING,
    private val snapDistance: Float = DEFAULT_SNAP_DISTANCE,
    private val holdMillis: Long = DEFAULT_HOLD_MS,
) {
    private var current: Quad? = null
    private var lastSeenAt = 0L

    /** Feeds one frame's detection ([detected] null when nothing was found) and returns what to draw. */
    fun update(detected: Quad?, timestampMillis: Long): Quad? {
        val previous = current
        current = when {
            detected == null -> previous.takeIf { timestampMillis - lastSeenAt <= holdMillis }
            previous == null -> detected
            QuadGeometry.meanCornerDistance(previous, detected) > snapDistance -> detected
            else -> blend(previous, detected)
        }
        if (detected != null) lastSeenAt = timestampMillis
        return current
    }

    fun reset() {
        current = null
        lastSeenAt = 0L
    }

    private fun blend(from: Quad, to: Quad): Quad {
        val (tl, tr, br, bl) = from.points.zip(to.points) { a, b ->
            NormalizedPoint(a.x + (b.x - a.x) * smoothing, a.y + (b.y - a.y) * smoothing)
        }
        return Quad(tl, tr, br, bl)
    }

    private companion object {
        const val DEFAULT_SMOOTHING = 0.22f
        const val DEFAULT_SNAP_DISTANCE = 0.08f
        const val DEFAULT_HOLD_MS = 500L
    }
}
