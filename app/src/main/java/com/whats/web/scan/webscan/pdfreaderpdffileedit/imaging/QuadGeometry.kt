package com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging

import com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging.model.NormalizedPoint
import com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging.model.Quad
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot

/** A point in pixel space. Pure Kotlin so the geometry tests run on a plain JVM without OpenCV. */
data class Pt(val x: Double, val y: Double)

/**
 * Pure geometry shared by detection and (later) warping. Image coordinates have y pointing down,
 * so "clockwise" is clockwise as seen on screen.
 */
object QuadGeometry {

    /**
     * Orders four arbitrary corners clockwise starting at the top-left. Sorting by angle around the
     * centroid is robust to strong perspective and rotation, where sorting by x or y alone swaps corners.
     */
    fun orderClockwise(points: List<Pt>): List<Pt> {
        require(points.size == CORNERS) { "A quad has $CORNERS corners, got ${points.size}" }
        val cx = points.sumOf { it.x } / CORNERS
        val cy = points.sumOf { it.y } / CORNERS
        val byAngle = points.sortedBy { atan2(it.y - cy, it.x - cx) }
        val topLeft = byAngle.indices.minBy { byAngle[it].x + byAngle[it].y }
        return List(CORNERS) { byAngle[(topLeft + it) % CORNERS] }
    }

    /** Shoelace area of a simple polygon, always positive. */
    fun area(points: List<Pt>): Double {
        var twice = 0.0
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            twice += a.x * b.y - b.x * a.y
        }
        return abs(twice) / 2
    }

    /** Interior angles in degrees, one per corner, for a quad given in order. */
    fun interiorAngles(ordered: List<Pt>): List<Double> = ordered.indices.map { i ->
        val prev = ordered[(i + CORNERS - 1) % CORNERS]
        val here = ordered[i]
        val next = ordered[(i + 1) % CORNERS]
        val ax = prev.x - here.x
        val ay = prev.y - here.y
        val bx = next.x - here.x
        val by = next.y - here.y
        val cos = (ax * bx + ay * by) / (hypot(ax, ay) * hypot(bx, by))
        Math.toDegrees(acos(cos.coerceIn(-1.0, 1.0)))
    }

    /**
     * Converts a quad found in a camera buffer of [width]×[height] into normalised coordinates of
     * the upright image, where [rotationDegrees] is how far the buffer must turn clockwise to be upright.
     */
    fun toUprightQuad(ordered: List<Pt>, width: Int, height: Int, rotationDegrees: Int): Quad {
        val upright = ordered.map { p ->
            val x = (p.x / width).coerceIn(0.0, 1.0)
            val y = (p.y / height).coerceIn(0.0, 1.0)
            when (rotationDegrees) {
                0 -> Pt(x, y)
                QUARTER -> Pt(1 - y, x)
                HALF -> Pt(1 - x, 1 - y)
                THREE_QUARTERS -> Pt(y, 1 - x)
                else -> throw IllegalArgumentException("Rotation must be 0, 90, 180 or 270, was $rotationDegrees")
            }
        }
        // Rotation moves which corner is top-left, so order again in the upright frame.
        val (topLeft, topRight, bottomRight, bottomLeft) = orderClockwise(upright).map {
            NormalizedPoint(it.x.toFloat(), it.y.toFloat())
        }
        return Quad(topLeft, topRight, bottomRight, bottomLeft)
    }

    /**
     * Share of each side of [ordered] that runs along detected edges, reported for the weakest side.
     * A real page outline follows edges all the way round; a hull drawn around clutter mostly crosses
     * empty space. [isEdge] answers for a pixel; points outside the image count as no edge.
     */
    fun weakestSideEdgeSupport(ordered: List<Pt>, samplesPerSide: Int, isEdge: (x: Int, y: Int) -> Boolean): Double =
        ordered.indices.minOf { i ->
            val from = ordered[i]
            val to = ordered[(i + 1) % CORNERS]
            // Skip the ends: corners are where edges bend and are often slightly rounded or dog-eared.
            val onEdge = (1 until samplesPerSide).count { s ->
                val t = s.toDouble() / samplesPerSide
                isEdge((from.x + (to.x - from.x) * t).toInt(), (from.y + (to.y - from.y) * t).toInt())
            }
            onEdge.toDouble() / (samplesPerSide - 1)
        }

    /** Mean distance between matching corners, in normalised units. */
    fun meanCornerDistance(a: Quad, b: Quad): Float =
        a.points.zip(b.points).sumOf { (p, q) -> hypot((p.x - q.x).toDouble(), (p.y - q.y).toDouble()) }
            .toFloat() / CORNERS

    const val CORNERS = 4
    private const val QUARTER = 90
    private const val HALF = 180
    private const val THREE_QUARTERS = 270
}
