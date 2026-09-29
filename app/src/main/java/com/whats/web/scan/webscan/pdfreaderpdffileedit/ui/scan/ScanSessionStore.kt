package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.scan

import android.content.Context
import com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging.model.PageFilter
import com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging.model.PageRotation
import com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging.model.Quad
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** One captured page in a scan session. The capture bytes stay on disk; this is the recipe over them. */
@Serializable
data class ScanPage(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val crop: Quad = Quad.FULL_IMAGE,
    val filter: PageFilter = PageFilter.ORIGINAL,
    val rotationDegrees: Int = 0,
) {
    val rotation: PageRotation get() = PageRotation.ofDegrees(rotationDegrees)
}

/**
 * FR-043. The pages captured so far, with their crops and filters, on disk in `noBackupFilesDir/scan`
 * so a session survives the camera being killed in the background. Plain JSON — these are the user's
 * own pages on their own phone, and they are deleted as soon as the PDF is written or discarded.
 */
@Singleton
class ScanSessionStore @Inject constructor(@ApplicationContext private val context: Context) {

    private val directory: File get() = File(context.noBackupFilesDir, "scan").apply { mkdirs() }
    private val manifest: File get() = File(directory, "session.json")
    private val json = Json { ignoreUnknownKeys = true }

    private val _pages = MutableStateFlow(load())
    val pages: StateFlow<List<ScanPage>> = _pages.asStateFlow()

    private fun load(): List<ScanPage> = runCatching {
        if (!manifest.exists()) return@runCatching emptyList()
        json.decodeFromString<List<ScanPage>>(manifest.readText())
            .filter { File(directory, it.fileName).exists() }
    }.getOrDefault(emptyList())

    fun fileOf(page: ScanPage): File = File(directory, page.fileName)

    suspend fun add(jpeg: ByteArray, crop: Quad): ScanPage = withContext(Dispatchers.IO) {
        val page = ScanPage(fileName = "page-${System.currentTimeMillis()}.jpg", crop = crop)
        File(directory, page.fileName).writeBytes(jpeg)
        update(_pages.value + page)
        page
    }

    fun replace(page: ScanPage) {
        update(_pages.value.map { if (it.id == page.id) page else it })
    }

    fun remove(page: ScanPage) {
        update(_pages.value.filterNot { it.id == page.id })
    }

    /** Puts a removed page back where it was — the undo behind FR-043's delete. */
    fun insert(page: ScanPage, at: Int) {
        val list = _pages.value.toMutableList()
        list.add(at.coerceIn(0, list.size), page)
        update(list)
    }

    fun move(from: Int, to: Int) {
        val list = _pages.value.toMutableList()
        if (from !in list.indices || to !in list.indices) return
        list.add(to, list.removeAt(from))
        update(list)
    }

    fun clear() {
        directory.listFiles()?.forEach { it.delete() }
        _pages.value = emptyList()
    }

    private fun update(pages: List<ScanPage>) {
        _pages.value = pages
        runCatching {
            manifest.writeText(json.encodeToString(pages))
            // A page file nobody references any more is dead weight in app storage.
            val referenced = pages.map { it.fileName }.toSet() + manifest.name
            directory.listFiles()?.filterNot { it.name in referenced }?.forEach { it.delete() }
        }
    }
}
