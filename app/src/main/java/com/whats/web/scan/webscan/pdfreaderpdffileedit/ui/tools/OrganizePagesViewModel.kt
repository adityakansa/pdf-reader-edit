package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.tools

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.DocFile
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.FileRepository
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.IncomingFile
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.OutputFolder
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.PdfAccess
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.PdfRenderSession
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.PdfToolRunner
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.PdfToolRunner.PageAction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OrganizeUiState(
    val file: DocFile? = null,
    val pageCount: Int = 0,
    /** Selected pages in the order the user tapped them — Extract keeps that order. */
    val selected: List<Int> = emptyList(),
    val loading: Boolean = true,
    val working: Boolean = false,
    val locked: Boolean = false,
    val failed: Boolean = false,
    /** Name of the file just written, for the "Saved" message. */
    val savedName: String? = null,
    val error: Boolean = false,
)

/** Extract, rotate or delete pages of one PDF; every action writes a new file and opens it. */
@HiltViewModel
class OrganizePagesViewModel @Inject constructor(
    private val repository: FileRepository,
    private val access: PdfAccess,
    private val tools: PdfToolRunner,
    private val incoming: IncomingFile,
) : ViewModel() {
    private val _state = MutableStateFlow(OrganizeUiState())
    val state: StateFlow<OrganizeUiState> = _state.asStateFlow()
    private var session: PdfRenderSession? = null

    fun load(key: String) {
        if (_state.value.file?.key == key) return
        val file = repository.find(key) ?: run {
            _state.value = OrganizeUiState(loading = false, failed = true)
            return
        }
        _state.value = OrganizeUiState(file = file)
        viewModelScope.launch {
            _state.value = when (val result = access.open(file.uri)) {
                is PdfAccess.OpenResult.Success -> {
                    session?.close()
                    session = result.session
                    _state.value.copy(loading = false, pageCount = result.session.pageCount)
                }
                PdfAccess.OpenResult.PasswordRequired, PdfAccess.OpenResult.WrongPassword ->
                    _state.value.copy(loading = false, locked = true)
                is PdfAccess.OpenResult.Failed -> _state.value.copy(loading = false, failed = true)
            }
        }
    }

    suspend fun thumbnail(index: Int): Bitmap? =
        runCatching { session?.renderPage(index, THUMB_WIDTH_PIXELS) }.getOrNull()

    fun toggle(index: Int) {
        val selected = _state.value.selected
        _state.value = _state.value.copy(
            selected = if (index in selected) selected - index else selected + index,
        )
    }

    fun selectAll() {
        val all = (0 until _state.value.pageCount).toList()
        _state.value = _state.value.copy(selected = if (_state.value.selected.size == all.size) emptyList() else all)
    }

    fun suggestedName(action: PageAction): String {
        val name = _state.value.file?.name ?: "Document"
        return OutputFolder.derivedName(
            name,
            when (action) {
                PageAction.EXTRACT -> "pages"
                PageAction.DELETE -> "edited"
                PageAction.ROTATE_LEFT, PageAction.ROTATE_RIGHT -> "rotated"
            },
        )
    }

    fun run(action: PageAction, name: String) {
        val current = _state.value
        val file = current.file ?: return
        if (current.selected.isEmpty() || current.working) return
        if (action == PageAction.DELETE && current.selected.size >= current.pageCount) return
        _state.value = current.copy(working = true, error = false)
        viewModelScope.launch {
            val output = try {
                tools.pages(file, current.selected, action, name)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            _state.value = _state.value.copy(
                working = false,
                selected = if (output != null) emptyList() else _state.value.selected,
                savedName = output?.name,
                error = output == null,
            )
            output?.let { incoming.offer(it.uri, "application/pdf") }
        }
    }

    fun messageShown() {
        _state.value = _state.value.copy(savedName = null, error = false)
    }

    override fun onCleared() {
        session?.close()
    }

    private companion object {
        const val THUMB_WIDTH_PIXELS = 240
    }
}
