package com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf

/**
 * FR-052 / FR-053: something drawn on top of a page — a signature or a stamp. Position and size are
 * fractions of the page (0..1 from the top left), exactly as the placement screen stores them, so what
 * was put on screen lands in the same place in the output. Ported from pdfscanner `PdfOverlay.kt`.
 */
sealed interface PdfOverlay {

    val x: Float
    val y: Float
    val width: Float
    val height: Float

    /** Clockwise, in degrees, about the middle of the box. */
    val rotation: Float
    val opacity: Float

    /** A signature: PNG bytes with transparency, drawn as-is. */
    data class Image(
        val png: ByteArray,
        override val x: Float,
        override val y: Float,
        override val width: Float,
        override val height: Float,
        override val rotation: Float = 0f,
        override val opacity: Float = 1f,
    ) : PdfOverlay {

        // A data class over a ByteArray needs these by hand, or two identical signatures compare unequal.
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Image) return false
            return png.contentEquals(other.png) && x == other.x && y == other.y &&
                width == other.width && height == other.height &&
                rotation == other.rotation && opacity == other.opacity
        }

        override fun hashCode(): Int = png.contentHashCode() * HASH_PRIME + x.hashCode()

        private companion object {
            const val HASH_PRIME = 31
        }
    }

    /** A text or date stamp. */
    data class Text(
        val text: String,
        val colorArgb: Int,
        override val x: Float,
        override val y: Float,
        override val width: Float,
        override val height: Float,
        override val rotation: Float = 0f,
        override val opacity: Float = 1f,
    ) : PdfOverlay
}

/** Shared by the writer and the signer: how a stamp is sized and squeezed inside its box. */
internal object OverlayText {
    /** PdfBox reports font widths in 1/1000 of a point. */
    const val FONT_UNITS_PER_POINT = 1000f

    /** Where a stamp's baseline sits inside its box, leaving room for descenders. */
    const val BASELINE = 0.2f

    /** Cap height is roughly 70 % of the point size, so a box of h points fits a font of h / 0.7. */
    fun fontSize(boxHeight: Float): Float = (boxHeight * 0.7f).coerceAtLeast(1f)

    fun horizontalScale(boxWidth: Float, naturalWidth: Float): Float =
        if (naturalWidth <= 0f) 1f else (boxWidth / naturalWidth).coerceIn(0.1f, 10f)

    /** PdfBox's standard fonts are WinAnsi only; anything else would throw on `showText`. */
    fun encodable(text: String): String = text.filter { it.code in 32..255 }
}
