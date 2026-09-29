package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.scan.review

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.IncomingFile
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.OutputFolder
import com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging.PageProcessor
import com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging.model.PageFilter
import com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging.model.Quad
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.PdfOptions
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.PdfWriter
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.scan.ScanPage
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.scan.ScanSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class ReviewUiState(
    val pages: List<ScanPage> = emptyList(),
    val current: Int = 0,
    val saving: Boolean = false,
    val saved: Boolean = false,
    /** The page the user just deleted, kept so "Undo" can put it back where it was. */
    val undoable: Pair<ScanPage, Int>? = null,
)

@HiltViewModel
class PageReviewViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: ScanSessionStore,
    private val outputFolder: OutputFolder,
    private val incoming: IncomingFile,
) : ViewModel() {
    private val _state = MutableStateFlow(ReviewUiState())
    val state: StateFlow<ReviewUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            session.pages.collect { pages ->
                _state.value = _state.value.copy(
                    pages = pages,
                    current = _state.value.current.coerceIn(0, maxOf(pages.lastIndex, 0)),
                )
            }
        }
    }

    fun select(index: Int) {
        _state.value = _state.value.copy(current = index)
    }

    private fun currentPage(): ScanPage? = _state.value.pages.getOrNull(_state.value.current)

    fun setCrop(quad: Quad) = currentPage()?.let { session.replace(it.copy(crop = quad)) }

    fun setFilter(filter: PageFilter) = currentPage()?.let { session.replace(it.copy(filter = filter)) }

    fun rotate() = currentPage()?.let {
        session.replace(it.copy(rotationDegrees = (it.rotationDegrees + 90) % 360))
    }

    /** FR-043: drag-and-drop reorder in the page strip; the selected page stays selected as it moves. */
    fun move(from: Int, to: Int) {
        if (from == to) return
        session.move(from, to)
        val current = _state.value.current
        val next = when {
            current == from -> to
            current in (from + 1)..to -> current - 1
            current in to until from -> current + 1
            else -> current
        }
        _state.value = _state.value.copy(current = next)
    }

    fun delete() {
        val page = currentPage() ?: return
        val index = _state.value.current
        session.remove(page)
        _state.value = _state.value.copy(undoable = page to index)
    }

    fun undoDelete() {
        val (page, index) = _state.value.undoable ?: return
        session.insert(page, index)
        _state.value = _state.value.copy(undoable = null, current = index)
    }

    fun undoShown() {
        _state.value = _state.value.copy(undoable = null)
    }

    fun discard() {
        session.clear()
    }

    /** The processed page image, for the big preview and the thumbnails. */
    suspend fun preview(page: ScanPage, maxSide: Int): Bitmap? = withContext(Dispatchers.Default) {
        runCatching {
            val original = session.fileOf(page).readBytes()
            val processed = PageProcessor.process(original, page.crop, page.filter, true, page.rotation)
            val options = BitmapFactory.Options().apply {
                inSampleSize = generateSequence(1) { it * 2 }
                    .first { maxOf(processed.width, processed.height) / it <= maxSide }
            }
            BitmapFactory.decodeByteArray(processed.jpeg, 0, processed.jpeg.size, options)
        }.getOrNull()
    }

    /** The untouched capture, which the crop editor draws corners over. */
    suspend fun original(page: ScanPage, maxSide: Int): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = session.fileOf(page).readBytes()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val options = BitmapFactory.Options().apply {
                inSampleSize = generateSequence(1) { it * 2 }
                    .first { maxOf(bounds.outWidth, bounds.outHeight) / it <= maxSide }
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }.getOrNull()
    }

    /** FR-044: one PDF page per scan, in order, with each page's crop, filter and rotation applied. */
    fun suggestedName(): String = OutputFolder.scanName()

    fun save(name: String) {
        val pages = _state.value.pages
        if (pages.isEmpty() || _state.value.saving) return
        _state.value = _state.value.copy(saving = true)
        viewModelScope.launch {
            val output = runCatching {
                withContext(Dispatchers.Default) {
                    val scratch = File(context.cacheDir, "pdf").apply { mkdirs() }
                    PdfWriter(scratch).begin(PdfOptions(title = name)).use { builder ->
                        pages.forEach { page ->
                            val processed = PageProcessor.process(
                                session.fileOf(page).readBytes(),
                                page.crop,
                                page.filter,
                                true,
                                page.rotation,
                            )
                            builder.addJpegPage(processed.jpeg, processed.width, processed.height)
                        }
                        outputFolder.write(name) { out -> builder.writeTo(out) }
                    }
                }
            }.getOrNull()
            output?.let {
                session.clear()
                incoming.offer(it.uri, "application/pdf")
            }
            _state.value = _state.value.copy(saving = false, saved = output != null)
        }
    }
}
