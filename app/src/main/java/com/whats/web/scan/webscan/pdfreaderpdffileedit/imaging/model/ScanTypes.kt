package com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging.model

/**
 * The pure geometry and page types the scanner works in. Trimmed from pdfscanner's `core/model`:
 * this app ships four filters, three page sizes and no Pro-only imaging.
 */

/** FR-043: the filters offered on the review screen. */
enum class PageFilter {
    ORIGINAL,
    AUTO_ENHANCE,
    GRAYSCALE,
    BLACK_AND_WHITE,
}

/** Pages rotate in 90° steps only. */
enum class PageRotation(val degrees: Int) {
    NONE(0),
    CLOCKWISE_90(90),
    HALF_TURN(180),
    CLOCKWISE_270(270),
    ;

    fun rotatedClockwise(): PageRotation = entries[(ordinal + 1) % entries.size]

    companion object {
        fun ofDegrees(degrees: Int): PageRotation = entries.firstOrNull { it.degrees == degrees }
            ?: throw IllegalArgumentException("Rotation must be a multiple of 90 in 0..270, was $degrees")
    }
}

/** A point in image space, normalised to 0..1 so it survives re-encoding at another resolution. */
@kotlinx.serialization.Serializable
data class NormalizedPoint(val x: Float, val y: Float) {
    init {
        require(x in 0f..1f && y in 0f..1f) { "Point ($x, $y) is outside the image" }
    }
}

/** The document's four corners in the original capture, clockwise from top-left. */
@kotlinx.serialization.Serializable
data class Quad(
    val topLeft: NormalizedPoint,
    val topRight: NormalizedPoint,
    val bottomRight: NormalizedPoint,
    val bottomLeft: NormalizedPoint,
) {
    val points: List<NormalizedPoint> get() = listOf(topLeft, topRight, bottomRight, bottomLeft)

    companion object {
        /** The whole image: what an import with no detected edges starts from. */
        val FULL_IMAGE = Quad(
            NormalizedPoint(0f, 0f),
            NormalizedPoint(1f, 0f),
            NormalizedPoint(1f, 1f),
            NormalizedPoint(0f, 1f),
        )
    }
}

/** FR-041 page sizes in PostScript points (1/72 inch). [FIT] makes each page the size of its image. */
enum class PdfPageSize(val widthPoints: Float, val heightPoints: Float) {
    FIT(0f, 0f),
    A4(595.28f, 841.89f),
    LETTER(612f, 792f),
}

/** FR-041 margins, in points. */
enum class PageMargin(val points: Float) {
    NONE(0f),
    SMALL(18f),
    WIDE(54f),
}

/** Where an image sits on a PDF page, in points, origin bottom-left as in PDF. */
data class PdfPlacement(
    val pageWidth: Float,
    val pageHeight: Float,
    val imageX: Float,
    val imageY: Float,
    val imageWidth: Float,
    val imageHeight: Float,
)

/** Page geometry for PDF export, pure so it is tested on the JVM. Ported from pdfscanner `PdfLayout`. */
object PdfLayout {
    /** A fitted page is sized as if the image were printed with its longer side on A4's longer side. */
    private const val A4_LONG_SIDE_INCHES = 11.69f
    private const val POINTS_PER_INCH = 72f
    private const val MIN_DPI = 72f
    private const val MAX_DPI = 600f

    fun fittedDpi(widthPixels: Int, heightPixels: Int): Float =
        (maxOf(widthPixels, heightPixels) / A4_LONG_SIDE_INCHES).coerceIn(MIN_DPI, MAX_DPI)

    fun place(widthPixels: Int, heightPixels: Int, size: PdfPageSize, marginPoints: Float = 0f): PdfPlacement {
        require(widthPixels > 0 && heightPixels > 0) { "Image must have a size, was ${widthPixels}x$heightPixels" }
        val margin = marginPoints.coerceAtLeast(0f)
        if (size == PdfPageSize.FIT) {
            val scale = POINTS_PER_INCH / fittedDpi(widthPixels, heightPixels)
            val w = widthPixels * scale
            val h = heightPixels * scale
            // A fitted page grows by the margin rather than shrinking the image.
            return PdfPlacement(w + 2 * margin, h + 2 * margin, margin, margin, w, h)
        }
        val landscape = widthPixels > heightPixels
        val pageWidth = if (landscape) size.heightPoints else size.widthPoints
        val pageHeight = if (landscape) size.widthPoints else size.heightPoints
        val usableWidth = (pageWidth - 2 * margin).coerceAtLeast(1f)
        val usableHeight = (pageHeight - 2 * margin).coerceAtLeast(1f)
        val scale = minOf(usableWidth / widthPixels, usableHeight / heightPixels)
        val w = widthPixels * scale
        val h = heightPixels * scale
        return PdfPlacement(pageWidth, pageHeight, (pageWidth - w) / 2, (pageHeight - h) / 2, w, h)
    }
}

/** Where content of [width] × [height] sits after being centred and fitted into a box. */
data class FittedRect(val left: Float, val top: Float, val width: Float, val height: Float) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
}

/**
 * The rectangle an image of [contentAspect] (width ÷ height) occupies when fitted into a box without
 * cropping. A zero-sized box returns the box itself, so callers can lay out before the image loaded.
 */
fun fittedContentRect(boxWidth: Float, boxHeight: Float, contentAspect: Float): FittedRect {
    if (boxWidth <= 0f || boxHeight <= 0f || contentAspect <= 0f) {
        return FittedRect(0f, 0f, boxWidth.coerceAtLeast(0f), boxHeight.coerceAtLeast(0f))
    }
    val boxAspect = boxWidth / boxHeight
    val (width, height) = if (boxAspect > contentAspect) {
        boxHeight * contentAspect to boxHeight
    } else {
        boxWidth to boxWidth / contentAspect
    }
    return FittedRect(
        left = (boxWidth - width) / 2f,
        top = (boxHeight - height) / 2f,
        width = width,
        height = height,
    )
}
