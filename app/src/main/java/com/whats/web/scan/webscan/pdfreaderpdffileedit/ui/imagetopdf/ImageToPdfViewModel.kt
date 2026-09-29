package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.imagetopdf

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.IncomingFile
import com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging.model.PageMargin
import com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging.model.PdfPageSize
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.ImagesToPdf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ImageToPdfUiState(
    val images: List<Uri> = emptyList(),
    val pageSize: PdfPageSize = PdfPageSize.A4,
    val margin: PageMargin = PageMargin.SMALL,
    val saving: Boolean = false,
    /** Set once the PDF is written; the shell has already been handed the file to open. */
    val saved: Boolean = false,
)

@HiltViewModel
class ImageToPdfViewModel @Inject constructor(
    private val imagesToPdf: ImagesToPdf,
    private val incoming: IncomingFile,
) : ViewModel() {
    private val _state = MutableStateFlow(ImageToPdfUiState())
    val state: StateFlow<ImageToPdfUiState> = _state.asStateFlow()

    fun add(uris: List<Uri>) {
        _state.value = _state.value.copy(
            images = (_state.value.images + uris).distinct().take(MAX_IMAGES),
        )
    }

    fun remove(uri: Uri) {
        _state.value = _state.value.copy(images = _state.value.images - uri)
    }

    /** Reordering on a phone grid: nudge a page one place later, tap again to keep going. */
    fun moveLater(uri: Uri) {
        val list = _state.value.images.toMutableList()
        val index = list.indexOf(uri)
        if (index < 0 || index == list.lastIndex) return
        list[index] = list[index + 1]
        list[index + 1] = uri
        _state.value = _state.value.copy(images = list)
    }

    fun setPageSize(size: PdfPageSize) {
        _state.value = _state.value.copy(pageSize = size)
    }

    fun setMargin(margin: PageMargin) {
        _state.value = _state.value.copy(margin = margin)
    }

    fun save() {
        val current = _state.value
        if (current.images.isEmpty() || current.saving) return
        _state.value = current.copy(saving = true)
        viewModelScope.launch {
            val output = runCatching {
                imagesToPdf.convert(current.images, current.pageSize, current.margin)
            }.getOrNull()
            // The new file is not in the index yet, so it is handed over the same way an "Open with"
            // document is: the shell opens whatever lands there.
            output?.let { incoming.offer(it.uri, "application/pdf") }
            _state.value = _state.value.copy(saving = false, saved = output != null)
        }
    }

    companion object {
        const val MAX_IMAGES = ImagesToPdf.MAX_IMAGES
    }
}
