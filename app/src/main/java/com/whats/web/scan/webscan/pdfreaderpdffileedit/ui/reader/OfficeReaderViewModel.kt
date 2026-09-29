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
import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.legacy.LegacyToHtml
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
            if (file.isLegacyOffice) {
                // Office 97–2003: read in-app; only a file we cannot read goes to "open with another app".
                val legacyHtml = withContext(Dispatchers.IO) {
                    runCatching {
                        val bytes = context.contentResolver.openInputStream(file.uri)?.use { it.readBytes() }
                            ?: return@runCatching null
                        LegacyToHtml.convert(file.ext, bytes, rowCapNotice)
                    }.getOrNull()
                }
                _state.value = if (legacyHtml != null) {
                    _state.value.copy(loading = false, html = legacyHtml, baseUrl = null)
                } else {
                    _state.value.copy(loading = false, legacy = true)
                }
                return@launch
            }
            val mediaDir = File(context.cacheDir, "office-media")
            val html = withContext(Dispatchers.IO) {
                runCatching {
                    // CSV and TXT are text, not zip packages: they never reach the OOXML reader.
                    when (file.ext.lowercase()) {
                        "csv", "txt" -> {
                            val bytes = context.contentResolver.openInputStream(file.uri)
                                ?.use { it.readBytes() } ?: return@runCatching null
                            val text = TextToHtml.decode(bytes)
                            return@runCatching if (file.ext.equals("csv", ignoreCase = true)) {
                                TextToHtml.csv(text, rowCapNotice)
                            } else {
                                TextToHtml.plain(text, textCapNotice)
                            }
                        }
                    }
                    val parts = context.contentResolver.openInputStream(file.uri)
                        ?.use(OoxmlZip::read)
                        ?: return@runCatching null
                    val media = OoxmlZip.extractMedia(parts, mediaDir)
                    when (file.type) {
                        DocType.WORD -> DocxToHtml.convert(parts, media)
                        DocType.EXCEL -> XlsxToHtml.convert(parts, rowCapNotice)
                        DocType.PPT -> PptxToHtml.convert(parts, media)
                        DocType.PDF, DocType.TEXT -> null
                    }
                }.getOrNull()
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
