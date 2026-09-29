package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ai.AiJob
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ai.AiResultHolder
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.IncomingFile
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.TextToPdf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AiResultUiState(
    val job: AiJob = AiJob.TRANSLATE,
    val sourceName: String = "",
    val text: String = "",
    val saving: Boolean = false,
)

@HiltViewModel
class AiResultViewModel @Inject constructor(
    private val holder: AiResultHolder,
    private val textToPdf: TextToPdf,
    private val incoming: IncomingFile,
) : ViewModel() {
    private val _state = MutableStateFlow(
        holder.result?.let { AiResultUiState(it.job, it.sourceName, it.text) } ?: AiResultUiState(),
    )
    val state: StateFlow<AiResultUiState> = _state.asStateFlow()

    fun saveAsPdf() {
        val current = _state.value
        if (current.text.isBlank() || current.saving) return
        _state.value = current.copy(saving = true)
        viewModelScope.launch {
            val title = "${current.sourceName.substringBeforeLast('.')} - " +
                if (current.job == AiJob.TRANSLATE) "translation" else "summary"
            val output = runCatching { textToPdf.save(title, current.text) }.getOrNull()
            output?.let { incoming.offer(it.uri, "application/pdf") }
            _state.value = _state.value.copy(saving = false)
        }
    }
}
