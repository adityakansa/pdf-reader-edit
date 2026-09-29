package com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** A deleted document, kept in app storage until it is restored, emptied or 30 days old. */
data class BinEntry(
    val id: String,
    val name: String,
    val ext: String,
    val size: Long,
    val deletedAt: Long,
    /** Where it was, when that was a real path; restore puts it back there when it can. */
    val originalPath: String?,
) {
    val type: DocType? get() = DocType.ofExtension(ext)

    fun daysLeft(now: Long = System.currentTimeMillis()): Int =
        (RecycleBin.RETENTION_DAYS - TimeUnit.MILLISECONDS.toDays(now - deletedAt)).toInt().coerceAtLeast(0)
}

/**
 * Step 12a. Delete in the library no longer destroys a file: it is copied into the app's private
 * storage first and only then removed from the phone, so a slip of the finger can be undone for 30 days
 * (One Read, Document Reader and every gallery app do the same). Entries past 30 days are purged when the
 * bin is next read.
 */
@Singleton
class RecycleBin @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: FileRepository,
    private val output: OutputFolder,
    private val index: FileIndex,
) {
    private val dir: File get() = File(context.filesDir, "recycle_bin").apply { mkdirs() }
    private val manifest: File get() = File(dir, "bin.json")
    private val lock = Mutex()
    private val _entries = MutableStateFlow<List<BinEntry>>(emptyList())

    val entries: StateFlow<List<BinEntry>> = _entries

    /** Loads the bin and drops anything older than [RETENTION_DAYS]. */
    suspend fun load() = withContext(Dispatchers.IO) {
        lock.withLock {
            val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(RETENTION_DAYS.toLong())
            val (keep, expired) = read().partition { it.deletedAt >= cutoff }
            expired.forEach { blob(it).delete() }
            if (expired.isNotEmpty()) save(keep)
            _entries.value = keep.sortedByDescending { it.deletedAt }
        }
    }

    /**
     * Copies [file] into the bin, then deletes it from the phone. The copy is dropped again when the
     * delete is refused, so a file is never in both places or in neither.
     */
    suspend fun moveToBin(file: DocFile): Boolean = withContext(Dispatchers.IO) {
        if (file.isSample) return@withContext false
        val entry = BinEntry(
            id = UUID.randomUUID().toString(),
            name = file.name,
            ext = file.ext,
            size = file.size,
            deletedAt = System.currentTimeMillis(),
            originalPath = file.key.takeIf { it.startsWith("/") } ?: file.uri.takeIf { it.scheme == "file" }?.path,
        )
        val copied = runCatching {
            context.contentResolver.openInputStream(file.uri)?.use { input ->
                blob(entry).outputStream().use { input.copyTo(it) }
            } ?: error("unreadable")
        }.isSuccess
        if (!copied) {
            blob(entry).delete()
            // Nothing could be kept, so this is a plain delete, as before the bin existed.
            return@withContext repository.delete(file)
        }
        if (!repository.delete(file)) {
            blob(entry).delete()
            return@withContext false
        }
        lock.withLock {
            val all = read() + entry
            save(all)
            _entries.value = all.sortedByDescending { it.deletedAt }
        }
        true
    }

    /** Puts the file back where it was, or into Documents/PDF Reader when that folder is gone or closed to us. */
    suspend fun restore(entry: BinEntry): Uri? = withContext(Dispatchers.IO) {
        val source = blob(entry)
        if (!source.exists()) {
            forget(entry)
            return@withContext null
        }
        val back = entry.originalPath?.let(::File)?.let { original ->
            runCatching {
                val target = freeName(original)
                source.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
                Uri.fromFile(target)
            }.getOrNull()
        } ?: runCatching {
            output.write(entry.name.substringBeforeLast('.'), entry.ext, mimeOf(entry.ext)) { out ->
                source.inputStream().use { it.copyTo(out) }
            }.uri
        }.getOrNull()
        if (back != null) {
            forget(entry)
            index.refresh()
        }
        back
    }

    suspend fun deleteForever(entry: BinEntry) = withContext(Dispatchers.IO) { forget(entry) }

    suspend fun empty() = withContext(Dispatchers.IO) {
        lock.withLock {
            read().forEach { blob(it).delete() }
            save(emptyList())
            _entries.value = emptyList()
        }
    }

    private suspend fun forget(entry: BinEntry) = lock.withLock {
        blob(entry).delete()
        val rest = read().filterNot { it.id == entry.id }
        save(rest)
        _entries.value = rest.sortedByDescending { it.deletedAt }
    }

    private fun blob(entry: BinEntry) = File(dir, entry.id)

    private fun freeName(original: File): File {
        if (!original.exists()) return original
        val stem = original.nameWithoutExtension
        val ext = original.extension
        var n = 1
        while (File(original.parentFile, "$stem ($n).$ext").exists()) n++
        return File(original.parentFile, "$stem ($n).$ext")
    }

    private fun read(): List<BinEntry> = runCatching {
        if (!manifest.exists()) return emptyList()
        val array = JSONArray(manifest.readText())
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            BinEntry(
                id = o.getString("id"),
                name = o.getString("name"),
                ext = o.getString("ext"),
                size = o.optLong("size"),
                deletedAt = o.getLong("deletedAt"),
                originalPath = o.optString("originalPath").takeIf { it.isNotEmpty() },
            )
        }
    }.getOrDefault(emptyList())

    private fun save(entries: List<BinEntry>) {
        val array = JSONArray()
        entries.forEach { e ->
            array.put(
                JSONObject()
                    .put("id", e.id)
                    .put("name", e.name)
                    .put("ext", e.ext)
                    .put("size", e.size)
                    .put("deletedAt", e.deletedAt)
                    .put("originalPath", e.originalPath ?: ""),
            )
        }
        val temp = File(dir, "bin.json.part")
        temp.writeText(array.toString())
        temp.renameTo(manifest)
    }

    private fun mimeOf(ext: String): String =
        DocFile("", Uri.EMPTY, "", ext, DocType.ofExtension(ext) ?: DocType.PDF, 0, 0).mimeType

    companion object {
        const val RETENTION_DAYS = 30
    }
}
