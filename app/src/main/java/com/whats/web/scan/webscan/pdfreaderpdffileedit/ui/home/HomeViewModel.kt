package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.home

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.RecycleBin
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.viewModelScope
import com.whats.web.scan.webscan.pdfreaderpdffileedit.billing.Entitlement
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.DocFile
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.DocType
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.FileIndex
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.FileRepository
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.LibraryFile
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.StorageAccess
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.FolderNames
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.prefs.AppPreferences
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.prefs.LibraryView
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.prefs.SortOrder
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.IncomingFile
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.OutputFolder
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.PdfAccess
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.PdfToolRunner
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.WrongPdfPasswordException
import kotlinx.coroutines.CancellationException
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
    val tab: HomeTab = HomeTab.HOME,
    /** Set on a list screen (Home → a category card); null on the Home tabs. */
    val category: LibraryCategory? = null,
    val filter: DocType? = null,
    val files: List<LibraryFile> = emptyList(),
    val selected: Set<String> = emptySet(),
    val selectionMode: Boolean = false,
    val isPro: Boolean = false,
    val hasStorageAccess: Boolean = false,
    val sort: SortOrder = SortOrder(),
    /** The phone is still being searched for documents. */
    val scanning: Boolean = false,
    val view: LibraryView = LibraryView.LIST,
    /** In the Directories list: the folders, or null while one is open (then [files] are its files). */
    val folders: List<FolderItem>? = null,
    val openFolder: String? = null,
    /** File count per Home card. */
    val counts: Map<LibraryCategory, Int> = emptyMap(),
    /** Bytes of every document together, shown on the Directories card. */
    val totalSize: Long = 0L,
    /** Home's "Set as default reader" banner: this app is not the PDF default and the banner was not closed. */
    val showDefaultBanner: Boolean = false,
) {
    val grid: Boolean get() = view == LibraryView.GRID
}

data class FolderItem(val id: String, val name: String, val path: String, val count: Int)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: FileRepository,
    private val index: FileIndex,
    private val storageAccess: StorageAccess,
    private val pdfAccess: PdfAccess,
    private val prefs: AppPreferences,
    private val tools: PdfToolRunner,
    private val incoming: IncomingFile,
    private val recycleBin: RecycleBin,
    entitlement: Entitlement,
) : ViewModel() {
    private val tab = MutableStateFlow(HomeTab.HOME)
    private val category = MutableStateFlow<LibraryCategory?>(null)
    private val filter = MutableStateFlow<DocType?>(null)
    private val selection = MutableStateFlow(emptySet<String>())
    private val selectionMode = MutableStateFlow(false)
    private val openFolder = MutableStateFlow<String?>(null)
    private val isDefaultReader = MutableStateFlow(true)

    private data class ViewArgs(
        val tab: HomeTab,
        val category: LibraryCategory?,
        val filter: DocType?,
        val view: LibraryView,
        val folder: String?,
    )

    private fun folderItems(files: List<LibraryFile>): List<FolderItem> =
        files.groupBy { FolderNames.parentOf(it.file.key) }
            .filterKeys { it != null }
            .map { (id, list) ->
                val folder = id!!
                FolderItem(folder, FolderNames.displayName(folder), FolderNames.displayPath(folder), list.size)
            }
            .sortedWith(compareByDescending<FolderItem> { it.count }.thenBy { it.name.lowercase() })

    fun openFolder(id: String?) {
        openFolder.value = id
        clearSelection()
    }

    private val banner = combine(prefs.defaultBannerDismissed, isDefaultReader) { dismissed, isDefault ->
        !dismissed && !isDefault
    }

    val state: StateFlow<HomeUiState> = combine(
        combine(repository.all, repository.recents, repository.favourites) { all, recents, favourites ->
            Triple(all, recents, favourites)
        },
        combine(tab, category, filter, prefs.libraryView, openFolder) { t, c, f, v, o -> ViewArgs(t, c, f, v, o) },
        combine(selection, selectionMode) { s, m -> s to m },
        combine(entitlement.isPro, banner) { pro, showBanner -> pro to showBanner },
        combine(storageAccess.state, index.scanning) { access, scanning -> access to scanning },
    ) { lists, args, sel, proBanner, accessScanning ->
        val (access, scanning) = accessScanning
        val (all, recents, favourites) = lists
        val source = when {
            args.category == LibraryCategory.FAVOURITES -> favourites
            args.category == null && args.tab == HomeTab.RECENT -> recents
            else -> all
        }
        // A type card fixes the family; Recent's tabs and the All list's chips pick one.
        val type = args.category?.type ?: args.filter
        val ofType = source.filter { type == null || it.file.type == type }
        val showFolders = args.category == LibraryCategory.FOLDERS
        HomeUiState(
            tab = args.tab,
            category = args.category,
            filter = type,
            files = ofType.filter { args.folder == null || FolderNames.parentOf(it.file.key) == args.folder },
            selected = sel.first,
            selectionMode = sel.second,
            isPro = proBanner.first,
            hasStorageAccess = access.hasFullAccess || access.grantedTrees.isNotEmpty() ||
                access.grantedFiles.isNotEmpty(),
            scanning = scanning,
            // The Folders view became the Directories card, so the stored view only chooses list or grid.
            view = if (args.view == LibraryView.GRID) LibraryView.GRID else LibraryView.LIST,
            openFolder = args.folder,
            folders = if (showFolders && args.folder == null) folderItems(ofType) else null,
            counts = LibraryCategory.entries.associateWith { c ->
                when (c) {
                    LibraryCategory.ALL -> all.size
                    LibraryCategory.FAVOURITES -> favourites.size
                    LibraryCategory.FOLDERS -> all.mapNotNull { FolderNames.parentOf(it.file.key) }.distinct().size
                    else -> all.count { it.file.type == c.type }
                }
            },
            totalSize = all.sumOf { it.file.size },
            showDefaultBanner = proBanner.second,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun selectTab(value: HomeTab) {
        tab.value = value
        filter.value = null
        openFolder.value = null
        clearSelection()
    }

    /** Makes this instance the list screen for [value] (Home → a card). */
    fun showCategory(value: LibraryCategory) {
        if (category.value == value) return
        category.value = value
        filter.value = null
        openFolder.value = null
    }

    fun dismissDefaultBanner() = viewModelScope.launch { prefs.dismissDefaultBanner() }

    /** True when a PDF tapped anywhere already opens here, so the banner has nothing to offer. */
    private fun checkDefaultReader() {
        val probe = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://probe/file.pdf"), "application/pdf")
        val resolved = runCatching {
            context.packageManager.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)
        }.getOrNull()
        isDefaultReader.value = resolved?.activityInfo?.packageName == context.packageName
    }

    fun setFilter(value: DocType?) {
        filter.value = value
    }

    fun toggleFavourite(key: String) = viewModelScope.launch { repository.toggleFavourite(key) }

    fun markOpened(key: String) = viewModelScope.launch { repository.markOpened(key) }

    fun setView(view: LibraryView) = viewModelScope.launch {
        openFolder.value = null
        prefs.setLibraryView(view)
    }

    fun setSort(order: SortOrder) = viewModelScope.launch { repository.setSortOrder(order) }

    suspend fun currentSort(): SortOrder = repository.sortOrder()

    fun startSelection(key: String? = null) {
        selectionMode.value = true
        if (key != null) selection.value = setOf(key)
    }

    fun toggleSelected(key: String) {
        selection.value = if (key in selection.value) selection.value - key else selection.value + key
    }

    /** Selects every file in the current tab and chip; a second tap clears them again. */
    fun toggleSelectAll() {
        val visible = state.value.files.map { it.file.key }.toSet()
        selection.value = if (selection.value.containsAll(visible)) emptySet() else visible
    }

    fun clearSelection() {
        selection.value = emptySet()
        selectionMode.value = false
    }

    fun selectedFiles(): List<DocFile> =
        state.value.files.filter { it.file.key in selection.value }.map { it.file }

    /** Moves [files] to the recycle bin, where they can be restored for 30 days. */
    fun delete(files: List<DocFile>, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        val ok = files.map { recycleBin.moveToBin(it) }.all { it }
        clearSelection()
        onResult(ok)
    }

    fun refresh() {
        storageAccess.refresh()
        index.refresh()
        checkDefaultReader()
    }

    fun shareableUri(file: DocFile) = repository.shareableUri(file)

    fun locationOf(file: DocFile) = repository.locationOf(file)

    /** Merge is offered when two or more PDFs are selected. */
    fun canMergeSelection(): Boolean {
        val files = selectedFiles()
        return files.size >= 2 && files.all { it.type == DocType.PDF }
    }

    /** Result of a PDF tool, for the screen to announce. */
    sealed interface ToolResult {
        data class Saved(val name: String) : ToolResult
        data object WrongPassword : ToolResult
        data object Failed : ToolResult
    }

    private val _toolBusy = MutableStateFlow(false)
    val toolBusy: StateFlow<Boolean> = _toolBusy

    private fun runTool(
        openResult: Boolean,
        onResult: (ToolResult) -> Unit,
        work: suspend () -> OutputFolder.Output,
    ) {
        if (_toolBusy.value) return
        _toolBusy.value = true
        viewModelScope.launch {
            val result = try {
                val output = work()
                if (openResult) incoming.offer(output.uri, "application/pdf")
                ToolResult.Saved(output.name)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: WrongPdfPasswordException) {
                ToolResult.WrongPassword
            } catch (_: Exception) {
                ToolResult.Failed
            } finally {
                _toolBusy.value = false
            }
            onResult(result)
        }
    }

    /** Merges the selected PDFs in the order the list shows them, then opens the result. */
    fun mergeSelected(name: String, onResult: (ToolResult) -> Unit) {
        val files = selectedFiles()
        if (files.size < 2) return
        clearSelection()
        runTool(openResult = true, onResult = onResult) { tools.merge(files, name) }
    }

    fun protect(file: DocFile, password: String, onResult: (ToolResult) -> Unit) =
        runTool(openResult = false, onResult = onResult) { tools.protect(file, password) }

    fun unlock(file: DocFile, password: String, onResult: (ToolResult) -> Unit) =
        runTool(openResult = true, onResult = onResult) { tools.unlock(file, password) }

    fun suggestedMergeName(): String = OutputFolder.mergedName()

    /** Create sheet → "Merge PDFs": show only PDFs and start picking. */
    fun startMergePicking() {
        selection.value = emptySet()
        selectionMode.value = true
    }

    /** FR-018: page count for the File info dialog; null for anything that is not a readable PDF. */
    suspend fun pageCount(file: DocFile): Int? =
        if (file.type == DocType.PDF) runCatching { pdfAccess.pageCount(file.uri) }.getOrNull() else null
}
