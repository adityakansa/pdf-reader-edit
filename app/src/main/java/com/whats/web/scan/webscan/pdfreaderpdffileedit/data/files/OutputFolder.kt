package com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FR-045. Everything this app creates lands in `Documents/PDF Reader/` where other file apps can see
 * it. Partly written files stay invisible (IS_PENDING) so a crash never leaves a half PDF in the list.
 */
@Singleton
class OutputFolder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val index: FileIndex,
) {
    data class Output(val uri: Uri, val name: String)

    /** Writes `<baseName>.<extension>`; PDFs by default, Word and text for the document editor. */
    suspend fun write(
        baseName: String,
        extension: String = "pdf",
        mimeType: String = "application/pdf",
        write: (OutputStream) -> Unit,
    ): Output = withContext(Dispatchers.IO) {
        val name = "$baseName.$extension"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) writeViaMediaStore(name, mimeType, write)
        else writeViaFile(name, write)
    }

    /**
     * A picture (PDF to Image) into `Pictures/PDF Reader/`, where the gallery shows it. [bytes] is a JPEG.
     */
    suspend fun writeImage(baseName: String, bytes: ByteArray): Output = withContext(Dispatchers.IO) {
        val name = "$baseName.jpg"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$PICTURES_PATH/")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
                ?: error("MediaStore refused a new picture")
            runCatching { resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("No output stream") }
                .onFailure { resolver.delete(uri, null, null); throw it }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            // MediaStore renames on a clash ("Lease_page_1 (1).jpg"); report the name it kept.
            val kept = resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: name
            Output(uri, kept)
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "PDF Reader").apply { mkdirs() }
            val unique = uniqueName(name) { File(dir, it).exists() }
            val file = File(dir, unique)
            file.writeBytes(bytes)
            Output(Uri.fromFile(file), unique)
        }
    }

    private fun writeViaMediaStore(name: String, mimeType: String, write: (OutputStream) -> Unit): Output {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val unique = uniqueName(name) { candidate -> mediaStoreHas(candidate) }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, unique)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$RELATIVE_PATH/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: error("MediaStore refused a new document")
        runCatching {
            resolver.openOutputStream(uri)?.use(write) ?: error("No output stream for $uri")
        }.onFailure {
            resolver.delete(uri, null, null)
            throw it
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        index.onOutputWritten()
        return Output(uri, unique)
    }

    private fun writeViaFile(name: String, write: (OutputStream) -> Unit): Output {
        val dir = legacyDirectory().apply { mkdirs() }
        val unique = uniqueName(name) { candidate -> File(dir, candidate).exists() }
        val file = File(dir, unique)
        val temp = File(dir, ".$unique.part")
        runCatching { temp.outputStream().use(write) }.onFailure { temp.delete(); throw it }
        temp.renameTo(file)
        index.onOutputWritten()
        return Output(Uri.fromFile(file), unique)
    }

    private fun mediaStoreHas(name: String): Boolean {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection =
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        return context.contentResolver.query(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            projection,
            selection,
            arrayOf(name, "$RELATIVE_PATH%"),
            null,
        )?.use { it.count > 0 } ?: false
    }

    private inline fun uniqueName(name: String, taken: (String) -> Boolean): String {
        if (!taken(name)) return name
        val stem = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.')
        var n = 1
        while (taken("$stem ($n).$ext")) n++
        return "$stem ($n).$ext"
    }

    companion object {
        val RELATIVE_PATH = "${Environment.DIRECTORY_DOCUMENTS}/PDF Reader"
        const val DISPLAY_LOCATION = "Documents/PDF Reader"
        val PICTURES_PATH = "${Environment.DIRECTORY_PICTURES}/PDF Reader"
        const val PICTURES_LOCATION = "Pictures/PDF Reader"

        fun legacyDirectory(): File =
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "PDF Reader")

        private val stamp get() = SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.US).format(Date())

        fun scanName(): String = "Scan $stamp"

        fun imagesName(): String = "Images $stamp"

        fun signedName(original: String): String = "${original.substringBeforeLast('.')}_signed"

        fun highlightedName(original: String): String = "${original.substringBeforeLast('.')}_highlighted"

        fun mergedName(): String = "Merged $stamp"

        fun documentName(): String = "Document $stamp"

        /** `<name>_<suffix>` for a tool's output, e.g. `Lease_pages`, `Lease_protected`. */
        fun derivedName(original: String, suffix: String): String = "${original.substringBeforeLast('.')}_$suffix"
    }
}
