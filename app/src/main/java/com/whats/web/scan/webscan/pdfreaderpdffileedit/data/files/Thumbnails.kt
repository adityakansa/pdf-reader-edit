package com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.PdfRenderSession
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * First-page previews for the file list, the way Adobe Scan shows every document as a small page.
 *
 * PDFs render page 1 with the platform renderer. Office files use the `docProps/thumbnail.jpeg` that
 * Word and PowerPoint embed when they save (no preview → the list shows the type tile instead).
 * Results are cached twice: in memory for scrolling, and as small JPEGs in `cacheDir/thumbs`, keyed
 * on the file key + size + modified time, so an edited file gets a new preview. At most two
 * renders run at once, so a fast fling through a long list cannot open dozens of files together.
 */
@Singleton
class Thumbnails @Inject constructor(@ApplicationContext private val context: Context) {

    /** A preview, the PDF page count when known, and whether the PDF needs a password. */
    data class Preview(val bitmap: Bitmap?, val pages: Int?, val locked: Boolean = false)

    private val dir: File get() = File(context.cacheDir, "thumbs").apply { mkdirs() }
    private val gate = Semaphore(MAX_PARALLEL)
    private val memory = object : LruCache<String, Preview>(MEMORY_BYTES) {
        override fun sizeOf(key: String, value: Preview): Int = value.bitmap?.byteCount ?: 64
    }

    /** Cached result, if this file was previewed during this run. Lets rows draw instantly on rebind. */
    fun cached(file: DocFile): Preview? = memory.get(cacheKey(file))

    suspend fun preview(file: DocFile): Preview {
        val key = cacheKey(file)
        memory.get(key)?.let { return it }
        return gate.withPermit {
            memory.get(key) ?: withContext(Dispatchers.IO) { load(file, key) }.also { memory.put(key, it) }
        }
    }

    private suspend fun load(file: DocFile, key: String): Preview = runCatching {
        when {
            file.type == DocType.PDF -> loadPdf(file, key)
            file.ext.lowercase() in OOXML -> Preview(loadOfficePreview(file, key), pages = null)
            else -> Preview(null, null)
        }
    }.getOrElse { Preview(null, null) }

    private suspend fun loadPdf(file: DocFile, key: String): Preview {
        val jpeg = File(dir, "$key.jpg")
        val pagesFile = File(dir, "$key.pages")
        if (jpeg.exists() && pagesFile.exists()) {
            val pages = pagesFile.readText().toIntOrNull()
            return Preview(BitmapFactory.decodeFile(jpeg.absolutePath), pages)
        }
        val descriptor = context.contentResolver.openFileDescriptor(file.uri, "r")
            ?: throw IOException("Cannot open ${file.uri}")
        val session = try {
            PdfRenderSession.open(descriptor)
        } catch (_: SecurityException) {
            // Encrypted: the platform renderer cannot draw it without the password.
            descriptor.close()
            return Preview(null, null, locked = true)
        } catch (e: IOException) {
            descriptor.close()
            throw e
        }
        return session.use { s ->
            val bitmap = s.renderPage(0, WIDTH_PIXELS)
            store(jpeg, bitmap)
            pagesFile.writeText(s.pageCount.toString())
            Preview(bitmap, s.pageCount)
        }
    }

    /** The preview Office embeds in the package, if it did. Read by streaming, never unzipping the file. */
    private fun loadOfficePreview(file: DocFile, key: String): Bitmap? {
        val jpeg = File(dir, "$key.jpg")
        val none = File(dir, "$key.none")
        if (jpeg.exists()) return BitmapFactory.decodeFile(jpeg.absolutePath)
        if (none.exists()) return null
        val bytes = context.contentResolver.openInputStream(file.uri)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                generateSequence { zip.nextEntry }
                    .firstOrNull { it.name.equals(OFFICE_THUMBNAIL, ignoreCase = true) }
                    ?.let { zip.readBytes() }
            }
        }
        val bitmap = bytes?.let { decodeSampled(it) }
        if (bitmap == null) {
            none.createNewFile()
            return null
        }
        store(jpeg, bitmap)
        return bitmap
    }

    private fun decodeSampled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= WIDTH_PIXELS) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun store(target: File, bitmap: Bitmap) {
        runCatching {
            val temp = File(target.parentFile, "${target.name}.tmp")
            temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            temp.renameTo(target)
        }
    }

    private fun cacheKey(file: DocFile): String {
        val digest = MessageDigest.getInstance("SHA-1").digest("${file.key}|${file.size}|${file.modified}".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val WIDTH_PIXELS = 180
        const val JPEG_QUALITY = 80
        const val MAX_PARALLEL = 2
        const val MEMORY_BYTES = 12 * 1024 * 1024
        const val OFFICE_THUMBNAIL = "docProps/thumbnail.jpeg"
        val OOXML = setOf("docx", "xlsx", "pptx")
    }
}
