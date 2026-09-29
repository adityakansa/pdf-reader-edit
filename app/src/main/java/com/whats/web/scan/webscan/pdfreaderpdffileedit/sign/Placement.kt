package com.whats.web.scan.webscan.pdfreaderpdffileedit.sign

import java.io.File

/**
 * FR-052. One thing placed on a page. Position and size are fractions of the page, so a placement made
 * on a phone-sized render lands identically in the written PDF.
 */
data class Placement(
    val id: String,
    val page: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val rotation: Float = 0f,
    val opacity: Float = 1f,
    /** Exactly one of these is set. */
    val signature: File? = null,
    val text: String? = null,
    val textColorArgb: Int = 0xFF000000.toInt(),
)

/**
 * The gesture maths, ported from pdfscanner `core/data/.../sign/AnnotationRepository.kt`
 * (`AnnotationBounds`). Each pointer event is applied to the **current** placement, not to the one that
 * existed when the gesture started, or the box snaps back to where it was dropped.
 */
object PlacementBounds {

    /** Smallest an annotation can be scaled to, as a fraction of the page. */
    const val MIN_SIZE = 0.05f

    /** Largest: a little over the page, so a signature can deliberately overhang an edge. */
    const val MAX_SIZE = 1.5f

    fun applyGesture(
        placement: Placement,
        dx: Float,
        dy: Float,
        zoom: Float = 1f,
        rotationDegrees: Float = 0f,
    ): Placement {
        var updated = moved(placement, dx, dy)
        if (zoom != 1f) updated = scaled(updated, zoom)
        if (rotationDegrees != 0f) updated = rotated(updated, rotationDegrees)
        return updated
    }

    fun moved(placement: Placement, dx: Float, dy: Float): Placement = placement.copy(
        // Half the box may hang off the page; more than that and it would be lost.
        x = (placement.x + dx).coerceIn(-placement.width / 2f, 1f - placement.width / 2f),
        y = (placement.y + dy).coerceIn(-placement.height / 2f, 1f - placement.height / 2f),
    )

    /** Scales around the middle, so a pinch grows the box where it sits. */
    fun scaled(placement: Placement, factor: Float): Placement {
        val width = (placement.width * factor).coerceIn(MIN_SIZE, MAX_SIZE)
        val applied = width / placement.width
        val height = placement.height * applied
        return placement.copy(
            width = width,
            height = height,
            x = placement.x - (width - placement.width) / 2f,
            y = placement.y - (height - placement.height) / 2f,
        )
    }

    fun rotated(placement: Placement, degrees: Float): Placement =
        placement.copy(rotation = (placement.rotation + degrees).mod(FULL_TURN))

    private const val FULL_TURN = 360f
}
