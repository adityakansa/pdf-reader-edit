package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.reader

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whats.web.scan.webscan.pdfreaderpdffileedit.billing.Entitlement
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.DocFile
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.FileRepository
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.OutputFolder
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.PdfAccess
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.PdfMarkup
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.PdfMarkupWriter
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.PdfPageDimensions
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.PdfRenderSession
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.text.PdfTextIndex
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.text.PdfWord
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.text.PdfWords
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.text.TextMatch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class ReaderUiState(
    val file: DocFile? = null,
    val loading: Boolean = true,
    val pages: List<PdfPageDimensions> = emptyList(),
    val currentPage: Int = 0,
    val passwordRequired: Boolean = false,
    val wrongPassword: Boolean = false,
    val failed: Boolean = false,
    val favourite: Boolean = false,
    val isPro: Boolean = false,
    /** FR-031 */
    val searching: Boolean = false,
    val query: String = "",
    val matches: List<TextMatch> = emptyList(),
    val matchIndex: Int = 0,
    val noTextFound: Boolean = false,
    /** FR-032 */
    val highlightMode: Boolean = false,
    val pendingHighlights: List<PdfMarkup> = emptyList(),
    val jumpTo: Int? = null,
    val savedTo: String? = null,
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val repository: FileRepository,
    private val access: PdfAccess,
    private val outputFolder: OutputFolder,
    entitlement: Entitlement,
) : ViewModel() {
    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private var session: PdfRenderSession? = null
    private var source: PdfAccess.PdfSource? = null
    private var password: String? = null
    private var textIndex: PdfTextIndex? = null
    private val wordsByPage = mutableMapOf<Int, List<PdfWord>>()

    init {
        viewModelScope.launch {
            entitlement.isPro.collect { pro -> _state.value = _state.value.copy(isPro = pro) }
        }
    }

    fun load(key: String, password: String? = null) {
        val file = repository.find(key) ?: run {
            _state.value = _state.value.copy(loading = false, failed = true)
            return
        }
        this.password = password
        _state.value = _state.value.copy(file = file, loading = true, failed = false, wrongPassword = false)
        viewModelScope.launch {
            repository.markOpened(key)
            when (val result = access.open(file.uri, password)) {
                is PdfAccess.OpenResult.Success -> {
                    session?.close()
                    session = result.session
                    source = result.source
                    val pages = result.session.pageSizes()
                    _state.value = _state.value.copy(
                        loading = false,
                        pages = pages,
                        passwordRequired = false,
                        wrongPassword = false,
                    )
                    buildTextIndex(file)
                }

                PdfAccess.OpenResult.PasswordRequired ->
                    _state.value = _state.value.copy(loading = false, passwordRequired = true)

                PdfAccess.OpenResult.WrongPassword ->
                    _state.value = _state.value.copy(loading = false, passwordRequired = true, wrongPassword = true)

                is PdfAccess.OpenResult.Failed ->
                    _state.value = _state.value.copy(loading = false, failed = true)
            }
        }
        viewModelScope.launch {
            repository.all.collect { list ->
                val fav = list.firstOrNull { it.file.key == key }?.favourite == true
                _state.value = _state.value.copy(favourite = fav)
            }
        }
    }

    private fun buildTextIndex(file: DocFile) = viewModelScope.launch(Dispatchers.IO) {
        val index = runCatching {
            access.openStream(source?.uri ?: file.uri).use {
                PdfTextIndex.of(it, access.scratchDir, if (source?.decryptedCopy != null) null else password)
            }
        }.getOrNull()
        textIndex = index
    }

    suspend fun render(index: Int, widthPixels: Int): Bitmap? =
        runCatching { session?.renderPage(index, widthPixels) }.getOrNull()

    fun onPageShown(index: Int) {
        if (index != _state.value.currentPage) _state.value = _state.value.copy(currentPage = index)
    }

    fun toggleFavourite() {
        val key = _state.value.file?.key ?: return
        viewModelScope.launch { repository.toggleFavourite(key) }
    }

    // ---- FR-031 search ----

    fun setSearching(on: Boolean) {
        _state.value = _state.value.copy(
            searching = on,
            query = if (on) _state.value.query else "",
            matches = if (on) _state.value.matches else emptyList(),
            noTextFound = false,
        )
    }

    fun search(query: String) {
        val index = textIndex
        val matches = index?.search(query).orEmpty()
        _state.value = _state.value.copy(
            query = query,
            matches = matches,
            matchIndex = 0,
            noTextFound = query.isNotBlank() && matches.isEmpty() && index?.hasAnyText != true,
            jumpTo = matches.firstOrNull()?.pageIndex,
        )
    }

    fun nextMatch() = stepMatch(1)

    fun previousMatch() = stepMatch(-1)

    private fun stepMatch(delta: Int) {
        val matches = _state.value.matches
        if (matches.isEmpty()) return
        val next = (_state.value.matchIndex + delta + matches.size) % matches.size
        _state.value = _state.value.copy(matchIndex = next, jumpTo = matches[next].pageIndex)
    }

    fun onJumped() {
        _state.value = _state.value.copy(jumpTo = null)
    }

    // ---- FR-032 highlight ----

    fun setHighlightMode(on: Boolean) {
        _state.value = _state.value.copy(highlightMode = on)
    }

    /** The words under the box the finger dragged over, turned into a pending highlight. */
    fun highlight(pageIndex: Int, left: Float, top: Float, right: Float, bottom: Float) {
        viewModelScope.launch {
            val words = wordsOn(pageIndex)
            val hit = words.filter { it.intersects(left, top, right, bottom) }
            if (hit.isEmpty()) return@launch
            val mark = PdfMarkup(
                page = pageIndex,
                left = hit.minOf { it.left },
                top = hit.minOf { it.top },
                right = hit.maxOf { it.right },
                bottom = hit.maxOf { it.bottom },
                colorArgb = HIGHLIGHT_ARGB,
                text = hit.joinToString(" ") { it.text },
            )
            _state.value = _state.value.copy(pendingHighlights = _state.value.pendingHighlights + mark)
        }
    }

    private suspend fun wordsOn(pageIndex: Int): List<PdfWord> = withContext(Dispatchers.IO) {
        wordsByPage.getOrPut(pageIndex) {
            val file = _state.value.file ?: return@getOrPut emptyList()
            runCatching {
                val local = access.localCopy(source?.uri ?: file.uri)
                PdfWords.onPage(local, pageIndex, if (source?.decryptedCopy != null) null else password)
            }.getOrDefault(emptyList())
        }
    }

    fun clearHighlights() {
        _state.value = _state.value.copy(pendingHighlights = emptyList())
    }

    /** FR-032: always writes a new `<name>_highlighted.pdf` — the original is never touched. */
    fun saveHighlights() {
        val file = _state.value.file ?: return
        val marks = _state.value.pendingHighlights
        if (marks.isEmpty()) return
        viewModelScope.launch {
            val output = runCatching {
                outputFolder.write(OutputFolder.highlightedName(file.name)) { out ->
                    val temp = File.createTempFile("highlight", ".pdf", access.scratchDir)
                    access.openStream(source?.uri ?: file.uri).use { input ->
                        PdfMarkupWriter.write(
                            input = input,
                            password = if (source?.decryptedCopy != null) null else password,
                            marks = marks,
                            target = temp,
                            scratchDirectory = access.scratchDir,
                        )
                    }
                    temp.inputStream().use { it.copyTo(out) }
                    temp.delete()
                }
            }.getOrNull()
            _state.value = _state.value.copy(
                pendingHighlights = emptyList(),
                highlightMode = false,
                savedTo = output?.let { OutputFolder.DISPLAY_LOCATION },
            )
        }
    }

    fun messageShown() {
        _state.value = _state.value.copy(savedTo = null)
    }

    fun shareableUri() = _state.value.file?.let(repository::shareableUri)

    override fun onCleared() {
        session?.close()
        source?.decryptedCopy?.delete()
    }

    private companion object {
        const val HIGHLIGHT_ARGB = 0xFFFFEB3B.toInt()
    }
}
