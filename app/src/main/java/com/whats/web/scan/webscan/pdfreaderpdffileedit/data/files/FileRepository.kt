package com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.prefs.AppPreferences
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.prefs.SortField
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.prefs.SortOrder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** A file plus the user's marks on it — what every list in the app renders. */
data class LibraryFile(
    val file: DocFile,
    val favourite: Boolean,
    val lastOpenedAt: Long,
)

/** FR-013 … FR-020. The single read/write path for the library. */
@Singleton
class FileRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val index: FileIndex,
    private val dao: FileMetaDao,
    private val prefs: AppPreferences,
    private val incoming: IncomingFile,
) {
    /** Everything the phone has, sorted by the user's choice; samples only fill an empty type. */
    val all: Flow<List<LibraryFile>> =
        combine(index.files, dao.all(), prefs.sortOrder) { files, meta, order ->
            val byKey = meta.associateBy { it.key }
            val (samples, real) = files.partition { it.isSample }
            val visibleSamples = samples.filter { sample -> real.none { it.type == sample.type } }
            (real + visibleSamples)
                .map { file ->
                    val m = byKey[file.key]
                    LibraryFile(file, m?.favourite == true, m?.lastOpenedAt ?: 0L)
                }
                .sortedWith(comparator(order))
        }

    val favourites: Flow<List<LibraryFile>> = all.map { list -> list.filter { it.favourite } }

    /** FR-017: newest first, capped, and files that vanished from storage simply drop out. */
    val recents: Flow<List<LibraryFile>> = all.map { list ->
        list.filter { it.lastOpenedAt > 0L }.sortedByDescending { it.lastOpenedAt }.take(RECENT_CAP)
    }

    private fun comparator(order: SortOrder): Comparator<LibraryFile> {
        val base: Comparator<LibraryFile> = when (order.field) {
            SortField.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.file.name }
            SortField.DATE -> compareBy { it.file.modified }
            SortField.SIZE -> compareBy { it.file.size }
        }
        return if (order.ascending) base else base.reversed()
    }

    suspend fun toggleFavourite(key: String) {
        val current = dao.find(key)
        val next = current?.favourite != true
        dao.setFavourite(key, next, if (next) System.currentTimeMillis() else 0L)
    }

    suspend fun markOpened(key: String) = dao.markOpened(key, System.currentTimeMillis())

    suspend fun setSortOrder(order: SortOrder) = prefs.setSortOrder(order)

    suspend fun sortOrder(): SortOrder = prefs.sortOrder.first()

    /** Files opened from another app are not in the index, so they are looked up there too. */
    fun find(key: String): DocFile? =
        index.files.value.firstOrNull { it.key == key } ?: incoming.find(key)

    /** FR-018. Returns false when the file is read-only to us — the caller shows the failure. */
    suspend fun delete(file: DocFile): Boolean = withContext(Dispatchers.IO) {
        if (file.isSample) return@withContext false
        val deleted = runCatching {
            when {
                DocumentsContract.isDocumentUri(context, file.uri) ->
                    DocumentsContract.deleteDocument(context.contentResolver, file.uri)

                file.uri.scheme == "file" -> File(requireNotNull(file.uri.path)).delete()

                else -> context.contentResolver.delete(file.uri, null, null) > 0
            }
        }.getOrDefault(false)
        if (deleted) {
            dao.delete(file.key)
            index.refresh()
        }
        deleted
    }

    /** A `file://` URI cannot leave the app on API 24+; hand other apps a FileProvider URI instead. */
    fun shareableUri(file: DocFile): Uri = when (file.uri.scheme) {
        "file" -> androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            File(requireNotNull(file.uri.path)),
        )
        else -> file.uri
    }

    /** FR-018 File info: where the user would find this file with a file manager. */
    fun locationOf(file: DocFile): String = when {
        file.isSample -> "In app"
        file.uri.scheme == "file" -> file.uri.path?.substringBeforeLast('/').orEmpty()
        file.key.startsWith("/") -> file.key.substringBeforeLast('/')
        else -> runCatching { DocumentsContract.getDocumentId(file.uri) }.getOrDefault(file.uri.toString())
    }

    private companion object {
        const val RECENT_CAP = 100
    }
}
