package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whats.web.scan.webscan.pdfreaderpdffileedit.billing.Entitlement
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.DocFile
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.DocType
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.FileIndex
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.FileRepository
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.LibraryFile
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.StorageAccess
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.prefs.SortOrder
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.shell.HomeTab
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val tab: HomeTab = HomeTab.DOCUMENT,
    val filter: DocType? = null,
    val files: List<LibraryFile> = emptyList(),
    val selected: Set<String> = emptySet(),
    val selectionMode: Boolean = false,
    val isPro: Boolean = false,
    val hasStorageAccess: Boolean = false,
    val sort: SortOrder = SortOrder(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: FileRepository,
    private val index: FileIndex,
    private val storageAccess: StorageAccess,
    entitlement: Entitlement,
) : ViewModel() {
    private val tab = MutableStateFlow(HomeTab.DOCUMENT)
    private val filter = MutableStateFlow<DocType?>(null)
    private val selection = MutableStateFlow(emptySet<String>())
    private val selectionMode = MutableStateFlow(false)

    val state: StateFlow<HomeUiState> = combine(
        combine(repository.all, repository.recents, repository.favourites) { all, recents, favourites ->
            Triple(all, recents, favourites)
        },
        combine(tab, filter) { t, f -> t to f },
        combine(selection, selectionMode) { s, m -> s to m },
        entitlement.isPro,
        storageAccess.state,
    ) { lists, tabFilter, sel, isPro, access ->
        val (all, recents, favourites) = lists
        val (currentTab, currentFilter) = tabFilter
        val source = when (currentTab) {
            HomeTab.RECENT -> recents
            HomeTab.FAVOURITE -> favourites
            else -> all
        }
        HomeUiState(
            tab = currentTab,
            filter = currentFilter,
            files = source.filter { currentFilter == null || it.file.type == currentFilter },
            selected = sel.first,
            selectionMode = sel.second,
            isPro = isPro,
            hasStorageAccess = access.hasFullAccess || access.grantedTrees.isNotEmpty() ||
                access.grantedFiles.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun selectTab(value: HomeTab) {
        tab.value = value
        clearSelection()
    }

    fun setFilter(value: DocType?) {
        filter.value = value
    }

    fun toggleFavourite(key: String) = viewModelScope.launch { repository.toggleFavourite(key) }

    fun markOpened(key: String) = viewModelScope.launch { repository.markOpened(key) }

    fun setSort(order: SortOrder) = viewModelScope.launch { repository.setSortOrder(order) }

    suspend fun currentSort(): SortOrder = repository.sortOrder()

    fun startSelection(key: String? = null) {
        selectionMode.value = true
        if (key != null) selection.value = setOf(key)
    }

    fun toggleSelected(key: String) {
        selection.value = if (key in selection.value) selection.value - key else selection.value + key
    }

    fun clearSelection() {
        selection.value = emptySet()
        selectionMode.value = false
    }

    fun selectedFiles(): List<DocFile> =
        state.value.files.filter { it.file.key in selection.value }.map { it.file }

    fun delete(files: List<DocFile>, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        val ok = files.map { repository.delete(it) }.all { it }
        clearSelection()
        onResult(ok)
    }

    fun refresh() {
        storageAccess.refresh()
        index.refresh()
    }

    fun shareableUri(file: DocFile) = repository.shareableUri(file)

    fun locationOf(file: DocFile) = repository.locationOf(file)
}
