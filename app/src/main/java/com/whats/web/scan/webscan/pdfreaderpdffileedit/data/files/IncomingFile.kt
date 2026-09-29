package com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FR-021. A document another app handed us. It is not in the index (it may live anywhere), so it is
 * described from the content resolver and pushed straight to the shell, which opens the viewer.
 */
@Singleton
class IncomingFile @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: FileMetaDao,
) {
    // A channel, not a replaying flow: a file must open once. A replay would reopen the last document
    // every time the shell resubscribed, e.g. after a rotation.
    private val _files = Channel<DocFile>(Channel.BUFFERED)
    val files: Flow<DocFile> = _files.receiveAsFlow()

    private val extra = mutableMapOf<String, DocFile>()

    /** Lets the reader resolve a key that never came from [FileIndex]. */
    fun find(key: String): DocFile? = extra[key]

    suspend fun offer(uri: Uri, mimeType: String?) {
        val doc = describe(uri, mimeType) ?: return
        extra[doc.key] = doc
        dao.markOpened(doc.key, System.currentTimeMillis())
        _files.send(doc)
    }

    private suspend fun describe(uri: Uri, mimeType: String?): DocFile? = withContext(Dispatchers.IO) {
        var name: String? = null
        var size = 0L
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val nameCol = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeCol = c.getColumnIndex(OpenableColumns.SIZE)
                    if (nameCol >= 0) name = c.getString(nameCol)
                    if (sizeCol >= 0) size = c.getLong(sizeCol)
                }
            }
        }
        val displayName = name ?: uri.lastPathSegment?.substringAfterLast('/') ?: return@withContext null
        val ext = displayName.substringAfterLast('.', "").lowercase().ifEmpty {
            extensionOfMime(mimeType) ?: return@withContext null
        }
        val type = DocType.ofExtension(ext) ?: return@withContext null
        DocFile(
            key = uri.toString(),
            uri = uri,
            name = displayName,
            ext = ext,
            type = type,
            size = size,
            modified = System.currentTimeMillis(),
        )
    }

    private fun extensionOfMime(mime: String?): String? = when (mime) {
        "application/pdf" -> "pdf"
        "application/msword" -> "doc"
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
        "application/vnd.ms-excel" -> "xls"
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx"
        "application/vnd.ms-powerpoint" -> "ppt"
        "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "pptx"
        else -> null
    }
}
