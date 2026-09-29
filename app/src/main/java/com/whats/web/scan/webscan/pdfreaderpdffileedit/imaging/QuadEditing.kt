package com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging

import com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging.model.NormalizedPoint
import com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging.model.Quad
import kotlin.math.hypot

/**
 * Rules for dragging crop corners (FR-8, DS-13). Pure Kotlin so every rule is unit-tested; the UI only
 * converts between screen pixels and normalised image coordinates.
 *
 * Coordinates are normalised to the image; [aspect] (width ÷ height) makes distances round in pixels
 * rather than stretched along the image's longer side.
 */
object QuadEditing {

    /** The corner closest to [point] within [maxDistance], or null if the touch is not near any corner. */
    fun nearestCorner(quad: Quad, point: NormalizedPoint, aspect: Float, maxDistance: Float): Int? =
        quad.points.withIndex()
            .map { (index, corner) -> index to distance(corner, point, aspect) }
            .filter { (_, distance) -> distance <= maxDistance }
            .minByOrNull { (_, distance) -> distance }
            ?.first

    /**
     * Moves corner [index] to ([targetX], [targetY]) — which a drag may carry outside the image — clamped
     * into the image. Within [snapDistance] of the matching
     * corner of [detected] it snaps there, so the detected edge is easy to hit exactly. A move that would
     * make the quad concave or self-intersecting is refused and the original quad is returned: a crossed
     * crop cannot be warped into a page.
     */
    fun moveCorner(
        quad: Quad,
        index: Int,
        targetX: Float,
        targetY: Float,
        detected: Quad?,
        aspect: Float,
        snapDistance: Float,
    ): Quad {
        require(index in 0 until QuadGeometry.CORNERS) { "Corner index must be 0..3, was $index" }
        val clamped = NormalizedPoint(targetX.coerceIn(0f, 1f), targetY.coerceIn(0f, 1f))
        val snapCandidate = detected?.points?.get(index)
        val placed = if (snapCandidate != null && distance(snapCandidate, clamped, aspect) <= snapDistance) {
            snapCandidate
        } else {
            clamped
        }
        val points = quad.points.toMutableList().also { it[index] = placed }
        val moved = Quad(points[0], points[1], points[2], points[3])
        return if (isConvex(moved)) moved else quad
    }

    /** True when the corners, in order, turn the same way at every corner and the quad has real area. */
    fun isConvex(quad: Quad): Boolean {
        val p = quad.points
        val turns = p.indices.map { i ->
            val a = p[i]
            val b = p[(i + 1) % QuadGeometry.CORNERS]
            val c = p[(i + 2) % QuadGeometry.CORNERS]
            (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
        }
        return turns.all { it > MIN_TURN } || turns.all { it < -MIN_TURN }
    }

    private fun distance(a: NormalizedPoint, b: NormalizedPoint, aspect: Float): Float =
        hypot(((a.x - b.x) * aspect).toDouble(), (a.y - b.y).toDouble()).toFloat()

    /** Corners closer to collinear than this are treated as a flat, unusable crop. */
    private const val MIN_TURN = 1e-4f
}
