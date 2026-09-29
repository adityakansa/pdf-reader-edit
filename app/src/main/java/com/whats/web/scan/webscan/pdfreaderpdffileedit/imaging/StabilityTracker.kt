package com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging

import com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging.model.Quad

/** One frame's auto-capture verdict: how far through the hold the page is, and whether to shoot now. */
data class AutoCaptureSignal(val progress: Float, val fire: Boolean) {
    companion object {
        val IDLE = AutoCaptureSignal(progress = 0f, fire = false)
    }
}

/**
 * Decides when auto-capture fires (FR-2, DS-9): the page has been held still for [holdMillis] and the
 * camera is in focus.
 *
 * - "Still" is measured against where the page settled, not frame to frame, so slow drift cannot
 *   creep past the tolerance one small step at a time.
 * - After firing it disarms, so one page is never captured twice. It re-arms when the page leaves the
 *   view for [rearmAbsentMillis], when it moves more than [rearmDistance] — which is what swapping in
 *   the next page looks like — or, more forgivingly, once [rearmAfterMillis] has passed **and** the page
 *   has moved further than [stillTolerance] since the shot. Without that last path a batch scan fired
 *   once and then never again: laying the next page in the same spot barely moves the corners, so the
 *   tracker stayed disarmed and every following page had to be taken by hand. A page that never moves at
 *   all — a phone on a stand over one sheet — still cannot be shot twice.
 * - Focus only gates the final shot: a page held still while the lens hunts keeps its progress and
 *   fires as soon as focus lands.
 *
 * Distances are mean corner distances in normalised image units. Not thread-safe: feed it from the
 * analysis thread only.
 */
class StabilityTracker(
    private val holdMillis: Long = DEFAULT_HOLD_MS,
    private val stillTolerance: Float = DEFAULT_STILL_TOLERANCE,
    private val rearmDistance: Float = DEFAULT_REARM_DISTANCE,
    private val rearmAbsentMillis: Long = DEFAULT_REARM_ABSENT_MS,
    private val rearmAfterMillis: Long = DEFAULT_REARM_AFTER_MS,
) {
    private var anchor: Quad? = null
    private var anchoredAt = 0L
    private var armed = true
    private var lastFired: Quad? = null
    private var absentSince: Long? = null
    private var firedAt = 0L

    /** True once the page has actually moved since the last shot, however slightly. */
    private var movedSinceFire = false

    fun update(quad: Quad?, inFocus: Boolean, nowMillis: Long): AutoCaptureSignal {
        if (quad == null) {
            anchor = null
            if (!armed) {
                val since = absentSince ?: nowMillis.also { absentSince = it }
                if (nowMillis - since >= rearmAbsentMillis) rearm()
            }
            return AutoCaptureSignal.IDLE
        }
        absentSince = null

        if (!armed) {
            if (!shouldRearm(quad, nowMillis)) return AutoCaptureSignal.IDLE
            rearm()
        }

        val settled = anchor
        if (settled == null || QuadGeometry.meanCornerDistance(settled, quad) > stillTolerance) {
            anchor = quad
            anchoredAt = nowMillis
            return AutoCaptureSignal.IDLE
        }

        val progress = ((nowMillis - anchoredAt).toFloat() / holdMillis).coerceAtMost(1f)
        if (progress < 1f || !inFocus) return AutoCaptureSignal(progress, fire = false)

        armed = false
        lastFired = quad
        firedAt = nowMillis
        movedSinceFire = false
        anchor = null
        return AutoCaptureSignal(progress = 1f, fire = true)
    }

    /**
     * Whether a disarmed tracker may arm again for [quad]: the page is clearly somewhere else, or it has
     * moved at all and the cooldown since the shot has passed.
     */
    private fun shouldRearm(quad: Quad, nowMillis: Long): Boolean {
        val fired = lastFired ?: return true
        val moved = QuadGeometry.meanCornerDistance(fired, quad)
        if (moved > stillTolerance) movedSinceFire = true
        return moved > rearmDistance || (movedSinceFire && nowMillis - firedAt >= rearmAfterMillis)
    }

    /** Forget everything, e.g. when auto-capture is switched off and on again. */
    fun reset() {
        anchor = null
        lastFired = null
        absentSince = null
        armed = true
        movedSinceFire = false
    }

    private fun rearm() {
        armed = true
        lastFired = null
        absentSince = null
        movedSinceFire = false
    }

    private companion object {
        const val DEFAULT_HOLD_MS = 800L

        /**
         * How far the page may drift from where it settled and still count as still, as a share of
         * the frame. Hand-held, a phone wanders a percent or so of the frame over a hold this long
         * even when the user thinks they are holding it steady, so a tighter tolerance meant the
         * anchor reset every few frames and auto-capture almost never fired.
         */
        const val DEFAULT_STILL_TOLERANCE = 0.03f

        /**
         * How far the page must jump to re-arm at once. Laying the next sheet on the same spot moves the
         * corners by a few percent of the frame, so the old 0.15 was a threshold a tidy batch scan never
         * reached.
         */
        const val DEFAULT_REARM_DISTANCE = 0.10f
        const val DEFAULT_REARM_ABSENT_MS = 600L

        /** Cooldown before a page that only shifted slightly may be shot again. */
        const val DEFAULT_REARM_AFTER_MS = 2_500L
    }
}
