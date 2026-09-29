package com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf

import kotlin.math.hypot

/** Step 12b: the Annotate eraser. Rubbing over a mark that is not saved yet takes it away. */
object MarkupEraser {

    /** [marks] without the ones on [page] under the point ([x], [y]) (page fractions). */
    fun erase(marks: List<PdfMarkup>, page: Int, x: Float, y: Float, slop: Float = SLOP): List<PdfMarkup> =
        marks.filterNot { it.page == page && hit(it, x, y, slop) }

    fun hit(mark: PdfMarkup, x: Float, y: Float, slop: Float = SLOP): Boolean = when (mark.kind) {
        // A pen stroke is thin: the finger has to be near the line itself, not just inside its bounds.
        MarkupKind.INK -> mark.strokes.any { stroke -> nearStroke(stroke, x, y, slop * 2) }
        else -> x >= mark.left - slop && x <= mark.right + slop && y >= mark.top - slop && y <= mark.bottom + slop
    }

    private fun nearStroke(points: List<Pair<Float, Float>>, x: Float, y: Float, reach: Float): Boolean {
        if (points.size == 1) return hypot(points[0].first - x, points[0].second - y) <= reach
        return points.zipWithNext().any { (a, b) -> distanceToSegment(x, y, a, b) <= reach }
    }

    private fun distanceToSegment(x: Float, y: Float, a: Pair<Float, Float>, b: Pair<Float, Float>): Float {
        val dx = b.first - a.first
        val dy = b.second - a.second
        val lengthSquared = dx * dx + dy * dy
        val t = if (lengthSquared == 0f) 0f else (((x - a.first) * dx + (y - a.second) * dy) / lengthSquared).coerceIn(0f, 1f)
        return hypot(a.first + t * dx - x, a.second + t * dy - y)
    }

    private const val SLOP = 0.008f
}
