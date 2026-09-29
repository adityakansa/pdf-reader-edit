package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whats.web.scan.webscan.pdfreaderpdffileedit.billing.Entitlement
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.DocType
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.FileRepository
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.LibraryFile
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.prefs.ReadingPositions
import com.whats.web.scan.webscan.pdfreaderpdffileedit.search.ContentHit
import com.whats.web.scan.webscan.pdfreaderpdffileedit.search.ContentSearch
import com.whats.web.scan.webscan.pdfreaderpdffileedit.search.ContentSearchEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: FileRepository,
    private val contentSearch: ContentSearch,
    private val positions: ReadingPositions,
    entitlement: Entitlement,
) : ViewModel() {

    /** Inside-files search: hits so far and how many files have been read. */
    data class ContentState(
        val hits: List<ContentHit> = emptyList(),
        val done: Int = 0,
        val total: Int = 0,
        val running: Boolean = false,
    )

    private val _insideFiles = MutableStateFlow(false)
    val insideFiles: StateFlow<Boolean> = _insideFiles.asStateFlow()

    private val _content = MutableStateFlow(ContentState())
    val content: StateFlow<ContentState> = _content.asStateFlow()
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val onlyType = MutableStateFlow<DocType?>(null)

    val isPro: StateFlow<Boolean> = entitlement.isPro

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val results: StateFlow<List<LibraryFile>> =
        combine(repository.all, _query.debounce(150), onlyType) { files, q, type ->
            files
                .filter { type == null || it.file.type == type }
                .filter { q.isBlank() || it.file.name.contains(q, ignoreCase = true) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setInsideFiles(on: Boolean) {
        _insideFiles.value = on
    }

    /** Opens a PDF hit at the page the text is on (the reader resumes at the saved page). */
    fun prepareOpen(hit: ContentHit) {
        if (hit.file.type == DocType.PDF && hit.page > 0) positions.save(hit.file.key, hit.page)
    }

    init {
        viewModelScope.launch {
            combine(_insideFiles, _query.debounce(CONTENT_DEBOUNCE_MS), repository.all) { inside, q, files ->
                Triple(inside, q.trim(), files)
            }.collectLatest { (inside, q, files) ->
                if (!inside || q.length < MIN_QUERY) {
                    _content.value = ContentState()
                    return@collectLatest
                }
                _content.value = ContentState(running = true)
                contentSearch.search(q, files.map { it.file }).collect { event ->
                    _content.value = when (event) {
                        is ContentSearchEvent.Hit -> _content.value.copy(hits = _content.value.hits + event.hit)
                        is ContentSearchEvent.Progress -> _content.value.copy(
                            done = event.done,
                            total = event.total,
                            running = event.done < event.total,
                        )
                    }
                }
                _content.value = _content.value.copy(running = false)
            }
        }
    }

    /** Picking a file for a tool: only that family is listed. */
    fun setOnlyType(value: DocType?) {
        onlyType.value = value
    }

    fun toggleFavourite(key: String) = viewModelScope.launch { repository.toggleFavourite(key) }

    private companion object {
        const val CONTENT_DEBOUNCE_MS = 400L
        const val MIN_QUERY = 2
    }
}
