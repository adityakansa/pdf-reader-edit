package com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FR-011. One scan of the phone for the seven document extensions: MediaStore first (it knows almost
 * everything), then a bounded walk of the public folders for files MediaStore never indexed. In SAF
 * mode the same list is built from the trees the user granted instead.
 */
@Singleton
class FileIndex @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageAccess: StorageAccess,
    private val samples: SampleFiles,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshes = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)
    private val outputs = MutableStateFlow(0)

    val files: StateFlow<List<DocFile>> =
        combine(refreshes.onStart { emit(Unit) }, storageAccess.state, outputs) { _, access, _ -> access }
            .map { access -> scan(access) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val observer = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean) {
            refresh()
        }
    }

    init {
        runCatching {
            context.contentResolver.registerContentObserver(
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
                true,
                observer,
            )
        }
    }

    fun refresh() {
        refreshes.tryEmit(Unit)
    }

    /** Called after the app writes a PDF so the new file shows up without waiting for MediaStore. */
    fun onOutputWritten() {
        outputs.value += 1
    }

    private suspend fun scan(access: StorageAccess.State): List<DocFile> = withContext(Dispatchers.IO) {
        val found = LinkedHashMap<String, DocFile>()
        if (access.hasFullAccess) {
            queryMediaStore(found)
            walkPublicFolders(found)
        } else {
            access.grantedTrees.forEach { tree -> walkTree(tree, found) }
            access.grantedFiles.forEach { uri -> readDocument(uri)?.let { found.putIfAbsent(it.key, it) } }
            // Own output is readable without any permission.
            walkDirectory(OutputFolder.legacyDirectory(), found, depth = 0)
        }
        found.values.toList() + samples.list()
    }

    private fun queryMediaStore(into: MutableMap<String, DocFile>) {
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.DATA,
        )
        val uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        // Filtering in SQL keeps a 100k-row media database from crossing the Binder one row at a time.
        val selection = DocType.allExtensions.joinToString(" OR ") {
            "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
        }
        val args = DocType.allExtensions.map { "%.$it" }.toTypedArray()
        val cursor = runCatching {
            context.contentResolver.query(uri, projection, selection, args, null)
        }.getOrNull() ?: return
        cursor.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val dataCol = c.getColumnIndex(MediaStore.Files.FileColumns.DATA)
            while (c.moveToNext()) {
                val name = c.getString(nameCol) ?: continue
                val ext = name.substringAfterLast('.', "").lowercase()
                val type = DocType.ofExtension(ext) ?: continue
                val path = if (dataCol >= 0) c.getString(dataCol) else null
                if (path != null && path.contains("/Android/data/")) continue
                val size = c.getLong(sizeCol)
                if (size <= 0L) continue
                val id = c.getLong(idCol)
                val fileUri = ContentUris.withAppendedId(uri, id)
                val doc = DocFile(
                    key = path ?: fileUri.toString(),
                    uri = fileUri,
                    name = name,
                    ext = ext,
                    type = type,
                    size = size,
                    modified = c.getLong(dateCol) * 1_000L,
                )
                into.putIfAbsent(doc.key, doc)
            }
        }
    }

    /** MediaStore misses files dropped by other apps until it rescans; these folders cover the gap. */
    private fun walkPublicFolders(into: MutableMap<String, DocFile>) {
        val roots = listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            File(Environment.getExternalStorageDirectory(), "Android/media"),
            Environment.getExternalStorageDirectory(),
        )
        roots.forEach { walkDirectory(it, into, depth = 0) }
    }

    // ponytail: depth-capped recursive walk, plain File API. Swap for a work-queue if deep trees ever hurt.
    private fun walkDirectory(dir: File?, into: MutableMap<String, DocFile>, depth: Int) {
        if (dir == null || depth > MAX_WALK_DEPTH || !dir.isDirectory) return
        if (dir.name == "Android" && depth > 0) return
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (child.isDirectory) {
                if (child.name.startsWith(".")) continue
                if (child.absolutePath.contains("/Android/data")) continue
                walkDirectory(child, into, depth + 1)
                continue
            }
            val ext = child.name.substringAfterLast('.', "").lowercase()
            val type = DocType.ofExtension(ext) ?: continue
            if (child.length() <= 0L) continue
            into.putIfAbsent(
                child.absolutePath,
                DocFile(
                    key = child.absolutePath,
                    uri = Uri.fromFile(child),
                    name = child.name,
                    ext = ext,
                    type = type,
                    size = child.length(),
                    modified = child.lastModified(),
                ),
            )
        }
    }

    private fun walkTree(treeUri: Uri, into: MutableMap<String, DocFile>, depth: Int = 0) {
        if (depth > MAX_WALK_DEPTH) return
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return
        fun visit(node: DocumentFile, level: Int) {
            if (level > MAX_WALK_DEPTH) return
            node.listFiles().forEach { child ->
                if (child.isDirectory) {
                    visit(child, level + 1)
                } else {
                    val name = child.name ?: return@forEach
                    val ext = name.substringAfterLast('.', "").lowercase()
                    val type = DocType.ofExtension(ext) ?: return@forEach
                    into.putIfAbsent(
                        child.uri.toString(),
                        DocFile(
                            key = child.uri.toString(),
                            uri = child.uri,
                            name = name,
                            ext = ext,
                            type = type,
                            size = child.length(),
                            modified = child.lastModified(),
                        ),
                    )
                }
            }
        }
        visit(tree, depth)
    }

    private fun readDocument(uri: Uri): DocFile? {
        val doc = DocumentFile.fromSingleUri(context, uri) ?: return null
        val name = doc.name ?: return null
        val ext = name.substringAfterLast('.', "").lowercase()
        val type = DocType.ofExtension(ext) ?: return null
        return DocFile(
            key = uri.toString(),
            uri = uri,
            name = name,
            ext = ext,
            type = type,
            size = doc.length(),
            modified = doc.lastModified(),
        )
    }

    private companion object {
        /** Deep enough for Download/<app>/<folder>/… without walking a whole SD card forever. */
        const val MAX_WALK_DEPTH = 6
    }
}
