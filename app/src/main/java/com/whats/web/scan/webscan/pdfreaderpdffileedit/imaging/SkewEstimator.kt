package com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging

import kotlin.math.abs

/**
 * Decides how far a page's text is tilted from the angles of line segments found along its text lines
 * (FR-10, DS-14). Pure Kotlin: the segment finding runs in OpenCV, the judgement is tested here.
 *
 * Angles are in degrees in image coordinates, where y points down: a positive angle means text lines run
 * down to the right, a clockwise tilt on screen.
 */
object SkewEstimator {

    /** DS-14: only small tilts are corrected automatically; a larger one is a rotation, not a skew. */
    const val MAX_CORRECTABLE_DEGREES = 10.0

    /** Below this the page is already straight enough; rotating would only soften it. */
    const val MIN_CORRECTION_DEGREES = 0.3

    /** Fewer near-horizontal segments than this is not enough evidence (a photo, a blank page). */
    const val MIN_SEGMENTS = 8

    /**
     * The angle to correct, or 0 when the page should be left alone. Uses the median so a few stray
     * segments (a signature, a table border at an angle) cannot drag the estimate.
     */
    fun correctionDegrees(segmentAngles: List<Double>): Double {
        val candidates = segmentAngles.filter { abs(it) <= MAX_CORRECTABLE_DEGREES }.sorted()
        if (candidates.size < MIN_SEGMENTS) return 0.0
        val mid = candidates.size / 2
        val median = if (candidates.size % 2 == 1) candidates[mid] else (candidates[mid - 1] + candidates[mid]) / 2
        return if (abs(median) < MIN_CORRECTION_DEGREES) 0.0 else median
    }

    /** Angle of the segment from (x1, y1) to (x2, y2), folded into -90°..90° so direction does not matter. */
    fun segmentAngle(x1: Double, y1: Double, x2: Double, y2: Double): Double {
        var degrees = Math.toDegrees(kotlin.math.atan2(y2 - y1, x2 - x1))
        if (degrees > HALF_TURN_HALF) degrees -= HALF_TURN
        if (degrees < -HALF_TURN_HALF) degrees += HALF_TURN
        return degrees
    }

    private const val HALF_TURN = 180.0
    private const val HALF_TURN_HALF = 90.0
}
