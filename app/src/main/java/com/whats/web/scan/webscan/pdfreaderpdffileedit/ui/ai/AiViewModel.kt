package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.ai

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whats.web.scan.webscan.pdfreaderpdffileedit.R
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ai.AiJob
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ai.AiLimits
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ai.AiResultHolder
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ai.PageTextExtractor
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ai.SummaryEngine
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ai.TargetLanguage
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ai.Translator
import com.whats.web.scan.webscan.pdfreaderpdffileedit.billing.Entitlement
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.DocFile
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.FileRepository
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.PdfAccess
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.PdfRenderSession
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AiUiState(
    val file: DocFile? = null,
    val job: AiJob = AiJob.TRANSLATE,
    val pageCount: Int = 0,
    val selectedPages: Set<Int> = emptySet(),
    val pageLimit: Int = AiLimits.FREE_PAGES,
    val isPro: Boolean = false,
    val limitMessage: String? = null,
    /** FR-064 */
    val languages: List<TargetLanguage> = TargetLanguage.entries,
    val downloaded: Set<TargetLanguage> = emptySet(),
    val target: TargetLanguage? = null,
    val running: Boolean = false,
    val progress: Float = 0f,
    val error: String? = null,
    val done: Boolean = false,
    val summaryUnsupported: Boolean = false,
    /** FR-069: the summary as it decodes, shown while [running]. */
    val streamingText: String = "",
    /** FR-065: ML Kit could not tell the page's language ("und"); the user picks it. */
    val askSource: Boolean = false,
)

@HiltViewModel
class AiViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: FileRepository,
    private val access: PdfAccess,
    private val extractor: PageTextExtractor,
    private val translator: Translator,
    private val summaryEngine: SummaryEngine,
    private val limits: AiLimits,
    private val results: AiResultHolder,
    private val entitlement: Entitlement,
) : ViewModel() {
    private val _state = MutableStateFlow(AiUiState())
    val state: StateFlow<AiUiState> = _state.asStateFlow()

    private var session: PdfRenderSession? = null
    private var runJob: Job? = null

    init {
        viewModelScope.launch {
            entitlement.isPro.collect { pro ->
                _state.value = _state.value.copy(
                    isPro = pro,
                    pageLimit = limits.pageLimit(_state.value.job, pro),
                )
            }
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(downloaded = translator.downloadedLanguages())
        }
    }

    fun open(key: String, job: AiJob) {
        if (_state.value.file?.key == key && _state.value.job == job) return
        val file = repository.find(key) ?: return
        val isPro = entitlement.isPro.value
        _state.value = _state.value.copy(
            file = file,
            job = job,
            pageLimit = limits.pageLimit(job, isPro),
            selectedPages = emptySet(),
            summaryUnsupported = job == AiJob.SUMMARY && !summaryEngine.available,
        )
        viewModelScope.launch {
            when (val result = access.open(file.uri)) {
                is PdfAccess.OpenResult.Success -> {
                    session?.close()
                    session = result.session
                    _state.value = _state.value.copy(pageCount = result.session.pageCount)
                }

                else -> _state.value = _state.value.copy(error = context.getString(R.string.reader_open_failed))
            }
        }
    }

    suspend fun thumbnail(index: Int): Bitmap? =
        runCatching { session?.renderPage(index, THUMB_WIDTH_PIXELS) }.getOrNull()

    fun togglePage(index: Int, onLimit: () -> Unit) {
        val current = _state.value
        if (index in current.selectedPages) {
            _state.value = current.copy(selectedPages = current.selectedPages - index)
            return
        }
        if (current.selectedPages.size >= current.pageLimit) {
            _state.value = current.copy(
                limitMessage = if (current.isPro) {
                    context.getString(R.string.page_limit_pro, current.pageLimit)
                } else {
                    context.getString(R.string.page_limit_free)
                },
            )
            if (!current.isPro) onLimit()
            return
        }
        _state.value = current.copy(selectedPages = current.selectedPages + index)
    }

    fun setPages(pages: List<Int>) {
        _state.value = _state.value.copy(selectedPages = pages.toSet())
    }

    fun setTarget(language: TargetLanguage) {
        _state.value = _state.value.copy(target = language)
    }

    fun messageShown() {
        _state.value = _state.value.copy(limitMessage = null, error = null)
    }

    /** FR-065: downloads a language the user picked but does not have yet. */
    fun downloadLanguage(language: TargetLanguage, wifiOnly: Boolean) {
        viewModelScope.launch {
            _state.value = _state.value.copy(running = true)
            val ok = runCatching { translator.download(language, wifiOnly) }.isSuccess
            _state.value = _state.value.copy(
                running = false,
                downloaded = translator.downloadedLanguages(),
                target = if (ok) language else null,
                error = if (ok) null else context.getString(R.string.download_failed),
            )
        }
    }

    /**
     * Runs the job on the selected pages. [sourceCode] is only passed after the user answered the
     * "which language is this page in?" question that an undetectable page raises (FR-065).
     */
    fun run(onDone: () -> Unit, sourceCode: String? = null) {
        val current = _state.value
        val file = current.file ?: return
        if (current.selectedPages.isEmpty() || current.running) return
        _state.value = current.copy(
            running = true,
            progress = 0f,
            error = null,
            streamingText = "",
            askSource = false,
        )
        runJob = viewModelScope.launch {
            val text = extractor.extract(file.uri, current.selectedPages.sorted())
                .toSortedMap()
                .values
                .joinToString("\n\n")
                .trim()
            if (text.isBlank()) {
                _state.value = _state.value.copy(running = false, error = context.getString(R.string.ai_no_text))
                return@launch
            }
            val output = try {
                when (current.job) {
                    AiJob.TRANSLATE -> {
                        val target = current.target ?: return@launch
                        val source = sourceCode ?: translator.detectSource(text)
                        if (source == null) {
                            _state.value = _state.value.copy(running = false, askSource = true)
                            return@launch
                        }
                        if (source == target.code) {
                            text
                        } else {
                            translator.translate(text, source, target) { progress ->
                                _state.value = _state.value.copy(progress = progress)
                            }
                        }
                    }

                    AiJob.SUMMARY -> summaryEngine.summarise(text)
                        .onEach { update -> _state.value = _state.value.copy(streamingText = update.text) }
                        .first { it.finished }
                        .text
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }

            if (output.isNullOrBlank()) {
                _state.value = _state.value.copy(
                    running = false,
                    error = context.getString(R.string.ai_failed),
                )
                return@launch
            }
            results.result = AiResultHolder.Result(current.job, file.name, output)
            _state.value = _state.value.copy(running = false, done = true)
            onDone()
        }
    }

    fun sourceQuestionDismissed() {
        _state.value = _state.value.copy(askSource = false)
    }

    fun cancel() {
        runJob?.cancel()
        runJob = null
        _state.value = _state.value.copy(running = false, progress = 0f, streamingText = "")
    }

    override fun onCleared() {
        session?.close()
    }

    private companion object {
        const val THUMB_WIDTH_PIXELS = 240
    }
}
