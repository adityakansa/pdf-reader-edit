package com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging

/**
 * Image formats a page can be imported from, recognised by their first bytes rather than by file name
 * or declared MIME type, both of which lie often enough (a PDF named `.jpg`, `application/octet-stream`
 * for a photo from a cloud drive).
 */
enum class ImageFormat(
    /** The oldest Android version whose BitmapFactory decodes this format. */
    val minSdk: Int,
) {
    JPEG(minSdk = 1),
    PNG(minSdk = 1),
    WEBP(minSdk = 1),
    HEIF(minSdk = HEIF_MIN_SDK),
    ;

    companion object {
        /** How many leading bytes [detect] needs. */
        const val HEADER_BYTES = 12

        /** The format of an image starting with [header], or null if it is not a supported image. */
        fun detect(header: ByteArray): ImageFormat? = when {
            header.startsWith(JPEG_SIGNATURE) -> JPEG
            header.startsWith(PNG_SIGNATURE) -> PNG
            header.hasAscii(0, "RIFF") && header.hasAscii(WEBP_BRAND_OFFSET, "WEBP") -> WEBP
            header.hasAscii(FTYP_OFFSET, "ftyp") && HEIF_BRANDS.any { header.hasAscii(BRAND_OFFSET, it) } -> HEIF
            else -> null
        }

        private fun ByteArray.startsWith(signature: IntArray): Boolean =
            size >= signature.size && signature.indices.all { this[it] == signature[it].toByte() }

        private fun ByteArray.hasAscii(offset: Int, text: String): Boolean =
            size >= offset + text.length && text.indices.all { this[offset + it] == text[it].code.toByte() }

        private val JPEG_SIGNATURE = intArrayOf(0xFF, 0xD8, 0xFF)
        private val PNG_SIGNATURE = intArrayOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        private const val WEBP_BRAND_OFFSET = 8
        private const val FTYP_OFFSET = 4
        private const val BRAND_OFFSET = 8
        private val HEIF_BRANDS = listOf("heic", "heix", "hevc", "heim", "heis", "mif1", "msf1")
    }
}

/** BitmapFactory decodes HEIF/HEIC from Android 9. */
private const val HEIF_MIN_SDK = 28
