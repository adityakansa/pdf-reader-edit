package com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.OutputFolder
import com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging.model.PageMargin
import com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging.model.PdfPageSize
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FR-041. Photos → one PDF. Each image is decoded at a size that fits [MAX_SIDE_PIXELS], turned
 * upright using its EXIF orientation and re-encoded as JPEG once, so twenty phone photos become a
 * document of a few megabytes instead of eighty.
 */
@Singleton
class ImagesToPdf @Inject constructor(
    @ApplicationContext private val context: Context,
    private val outputFolder: OutputFolder,
) {
    suspend fun convert(
        images: List<Uri>,
        pageSize: PdfPageSize,
        margin: PageMargin,
        onProgress: (Int) -> Unit = {},
    ): OutputFolder.Output = withContext(Dispatchers.IO) {
        require(images.isNotEmpty()) { "Pick at least one image" }
        val scratch = File(context.cacheDir, "pdf").apply { mkdirs() }
        val writer = PdfWriter(scratch)
        val name = OutputFolder.imagesName()
        writer.begin(PdfOptions(title = name, pageSize = pageSize, marginPoints = margin.points)).use { builder ->
            images.take(MAX_IMAGES).forEachIndexed { index, uri ->
                val bitmap = decodeUpright(uri) ?: return@forEachIndexed
                val jpeg = ByteArrayOutputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                    out.toByteArray()
                }
                builder.addJpegPage(jpeg, bitmap.width, bitmap.height)
                bitmap.recycle()
                onProgress(index + 1)
            }
            outputFolder.write(name) { out -> builder.writeTo(out) }
        }
    }

    private fun decodeUpright(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = generateSequence(1) { it * 2 }.first { longest / it <= MAX_SIDE_PIXELS }
        }
        val decoded = context.contentResolver.openInputStream(uri)
            ?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return null
        val degrees = context.contentResolver.openInputStream(uri)?.use { input ->
            when (ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        if (degrees == 0f) return decoded
        val rotated = Bitmap.createBitmap(
            decoded,
            0,
            0,
            decoded.width,
            decoded.height,
            Matrix().apply { postRotate(degrees) },
            true,
        )
        if (rotated !== decoded) decoded.recycle()
        return rotated
    }

    companion object {
        const val MAX_IMAGES = 100

        /** About 300 dpi on A4 — sharp on screen and when printed, without a 12 MP page. */
        const val MAX_SIDE_PIXELS = 2_480
        const val JPEG_QUALITY = 85
    }
}
