package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.reader

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.DocFile
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.DocType
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.FileRepository
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.isLegacyOffice
import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.DocxToHtml
import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.OoxmlZip
import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.PptxToHtml
import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.TextToHtml
import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.XlsxToHtml
import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.OfficeHtmlLoader
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

data class OfficeUiState(
    val file: DocFile? = null,
    val loading: Boolean = true,
    val html: String? = null,
    val baseUrl: String? = null,
    /** FR-036: the file is a legacy binary format and has to be handed to another app. */
    val legacy: Boolean = false,
    val failed: Boolean = false,
    val favourite: Boolean = false,
)

@HiltViewModel
class OfficeReaderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: FileRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(OfficeUiState())
    val state: StateFlow<OfficeUiState> = _state.asStateFlow()

    fun load(key: String, rowCapNotice: String, textCapNotice: String) {
        val file = repository.find(key) ?: run {
            _state.value = OfficeUiState(loading = false, failed = true)
            return
        }
        _state.value = OfficeUiState(file = file, loading = true)
        viewModelScope.launch {
            repository.markOpened(key)
            val mediaDir = File(context.cacheDir, "office-media")
            val html = withContext(Dispatchers.IO) {
                OfficeHtmlLoader.load(context, file, mediaDir, rowCapNotice, textCapNotice)
            }
            // FR-036: a legacy file none of the readers understood goes to "open with another app".
            if (html == null && file.isLegacyOffice) {
                _state.value = _state.value.copy(loading = false, legacy = true)
                return@launch
            }
            _state.value = _state.value.copy(
                loading = false,
                html = html,
                baseUrl = "file://${mediaDir.absolutePath}/",
                failed = html == null,
            )
        }
        viewModelScope.launch {
            repository.all.collect { list ->
                _state.value = _state.value.copy(
                    favourite = list.firstOrNull { it.file.key == key }?.favourite == true,
                )
            }
        }
    }

    fun toggleFavourite() {
        val key = _state.value.file?.key ?: return
        viewModelScope.launch { repository.toggleFavourite(key) }
    }

    fun shareableUri() = _state.value.file?.let(repository::shareableUri)
}
