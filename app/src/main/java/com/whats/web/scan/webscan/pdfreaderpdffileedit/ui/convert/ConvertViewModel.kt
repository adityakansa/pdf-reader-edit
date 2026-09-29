package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.convert

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whats.web.scan.webscan.pdfreaderpdffileedit.R
import com.whats.web.scan.webscan.pdfreaderpdffileedit.convert.OfficeToPdf
import com.whats.web.scan.webscan.pdfreaderpdffileedit.convert.PasswordProtectedException
import com.whats.web.scan.webscan.pdfreaderpdffileedit.convert.PdfToImages
import com.whats.web.scan.webscan.pdfreaderpdffileedit.convert.PdfToWord
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.DocFile
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.DocType
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.FileNames
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.FileRepository
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.OutputFolder
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.prefs.AppPreferences
import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.OfficeHtmlLoader
import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.PrintCss
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.PdfAccess
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.PdfCompressor
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.home.Tool
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

enum class ConvertPhase { READY, WORKING, DONE, FAILED }

enum class ConvertError { PASSWORD, FAILED, NOT_SMALLER, PRINT_UNAVAILABLE }

/** What a finished conversion made. [outputs] has one entry, or one per page for PDF to Image. */
data class ConvertResult(
    val outputs: List<OutputFolder.Output>,
    val mimeType: String,
    val location: String,
    val sizeBefore: Long = 0L,
    val sizeAfter: Long = 0L,
) {
    val first: OutputFolder.Output get() = outputs.first()
}

data class ConvertState(
    val file: DocFile? = null,
    val tool: Tool = Tool.PDF_TO_WORD,
    val phase: ConvertPhase = ConvertPhase.READY,
    val progress: Float = 0f,
    val level: PdfCompressor.Level = PdfCompressor.Level.MEDIUM,
    val result: ConvertResult? = null,
    val error: ConvertError? = null,
    /** "Are you satisfied with …?" is asked once, after the first success. */
    val askRating: Boolean = false,
    /** Kept for the system-print fallback when direct printing is not possible. */
    val printHtml: String? = null,
    val printBaseUrl: String? = null,
)

/**
 * Step 12c. Runs one conversion — PDF to Word, PDF to Image, Word / PowerPoint / Excel / text to PDF, or
 * Compress — and holds its result for the "Converted successfully" page.
 */
@HiltViewModel
class ConvertViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: FileRepository,
    private val access: PdfAccess,
    private val output: OutputFolder,
    private val pdfToWord: PdfToWord,
    private val pdfToImages: PdfToImages,
    private val officeToPdf: OfficeToPdf,
    private val prefs: AppPreferences,
) : ViewModel() {
    private val _state = MutableStateFlow(ConvertState())
    val state: StateFlow<ConvertState> = _state.asStateFlow()
    private var job: Job? = null

    fun load(key: String, tool: Tool) {
        if (_state.value.file != null) return
        val file = repository.find(key) ?: run {
            _state.value = _state.value.copy(tool = tool, phase = ConvertPhase.FAILED, error = ConvertError.FAILED)
            return
        }
        _state.value = _state.value.copy(file = file, tool = tool)
        // Compress asks how hard first; everything else starts at once, as One Read does.
        if (tool != Tool.COMPRESS_PDF) start()
    }

    fun setLevel(level: PdfCompressor.Level) {
        _state.value = _state.value.copy(level = level)
    }

    fun start() {
        val file = _state.value.file ?: return
        if (_state.value.phase == ConvertPhase.WORKING) return
        _state.value = _state.value.copy(phase = ConvertPhase.WORKING, progress = 0f, error = null)
        job = viewModelScope.launch {
            val outcome = try {
                Result.success(run(file, _state.value.tool))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Result.failure(e)
            }
            outcome.fold(
                onSuccess = { result ->
                    val ask = !prefs.ratingAsked.first()
                    _state.value = _state.value.copy(phase = ConvertPhase.DONE, progress = 1f, result = result, askRating = ask)
                },
                onFailure = { e ->
                    val error = when (e) {
                        is PasswordProtectedException -> ConvertError.PASSWORD
                        is NotSmallerException -> ConvertError.NOT_SMALLER
                        is PrintUnavailableException -> ConvertError.PRINT_UNAVAILABLE
                        else -> ConvertError.FAILED
                    }
                    _state.value = _state.value.copy(phase = ConvertPhase.FAILED, error = error)
                },
            )
        }
    }

    fun cancel() {
        job?.cancel()
        _state.value = _state.value.copy(phase = ConvertPhase.READY, progress = 0f)
    }

    private fun progress(value: Float) {
        _state.value = _state.value.copy(progress = value.coerceIn(0f, 1f))
    }

    private suspend fun run(file: DocFile, tool: Tool): ConvertResult {
        val base = file.name.substringBeforeLast('.')
        return when (tool) {
            Tool.PDF_TO_WORD -> {
                val temp = File(context.cacheDir, "word-${System.currentTimeMillis()}.docx")
                val saved = try {
                    temp.outputStream().use { out -> pdfToWord.convert(file, out) { progress(it * 0.95f) } }
                    output.write(base, "docx", DOCX_MIME) { out -> temp.inputStream().use { it.copyTo(out) } }
                } finally {
                    temp.delete()
                }
                ConvertResult(listOf(saved), DOCX_MIME, OutputFolder.DISPLAY_LOCATION)
            }
            Tool.PDF_TO_IMAGE -> {
                val images = pdfToImages.convert(file) { progress(it) }
                ConvertResult(images, "image/jpeg", OutputFolder.PICTURES_LOCATION)
            }
            Tool.COMPRESS_PDF -> compress(file, base)
            else -> officeToPdfFile(file, base)
        }
    }

    private suspend fun compress(file: DocFile, base: String): ConvertResult {
        val scratch = File(context.cacheDir, "compress").apply { mkdirs() }
        val temp = File(scratch, "out.pdf")
        progress(0.1f)
        val shrunk = withContext(Dispatchers.IO) {
            access.openStream(file.uri).use { input ->
                temp.outputStream().use { out -> PdfCompressor.compress(input, _state.value.level, out, scratch) }
            }
        }
        progress(0.8f)
        val before = file.size.takeIf { it > 0 } ?: withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.openInputStream(file.uri)?.use { it.available().toLong() } }.getOrNull() ?: 0L
        }
        val after = temp.length()
        if (shrunk == 0 || (before > 0 && after >= before)) {
            temp.delete()
            throw NotSmallerException()
        }
        val saved = output.write(OutputFolder.derivedName(file.name, "compressed")) { out ->
            temp.inputStream().use { it.copyTo(out) }
        }
        temp.delete()
        return ConvertResult(listOf(saved), PDF_MIME, OutputFolder.DISPLAY_LOCATION, sizeBefore = before, sizeAfter = after)
    }

    private suspend fun officeToPdfFile(file: DocFile, base: String): ConvertResult {
        val mediaDir = File(context.cacheDir, "convert-media").apply { deleteRecursively(); mkdirs() }
        progress(0.1f)
        val html = withContext(Dispatchers.IO) {
            OfficeHtmlLoader.load(
                context,
                file,
                mediaDir,
                context.getString(R.string.xlsx_row_cap),
                context.getString(R.string.text_cap),
            )
        } ?: throw IllegalStateException("Unreadable document")
        val kind = when (file.type) {
            DocType.WORD -> PrintCss.Kind.DOCUMENT
            DocType.EXCEL -> PrintCss.Kind.SHEET
            DocType.PPT -> PrintCss.Kind.SLIDES
            DocType.TEXT, DocType.PDF -> PrintCss.Kind.TEXT
        }
        val printable = PrintCss.forPrint(html, kind)
        val baseUrl = "file://${mediaDir.absolutePath}/"
        progress(0.35f)
        val temp = File(context.cacheDir, "print-${System.currentTimeMillis()}.pdf")
        val ok = officeToPdf.print(printable, baseUrl, kind == PrintCss.Kind.SLIDES, base, temp)
        if (!ok) {
            temp.delete()
            _state.value = _state.value.copy(printHtml = printable, printBaseUrl = baseUrl)
            throw PrintUnavailableException()
        }
        progress(0.9f)
        val saved = output.write(base) { out -> temp.inputStream().use { it.copyTo(out) } }
        temp.delete()
        return ConvertResult(listOf(saved), PDF_MIME, OutputFolder.DISPLAY_LOCATION)
    }

    /** The ✏ next to the name on the result page. Returns false when the new name could not be applied. */
    fun rename(newName: String, onDone: (Boolean) -> Unit) {
        val result = _state.value.result ?: return
        val current = result.first
        val ext = current.name.substringAfterLast('.', "")
        val clean = FileNames.sanitize(newName.removeSuffix(".$ext"), "")
        if (clean.isBlank()) return onDone(false)
        val target = if (ext.isEmpty()) clean else "$clean.$ext"
        viewModelScope.launch {
            val renamed = withContext(Dispatchers.IO) { rename(current.uri, target) }
            if (renamed != null) {
                val outputs = listOf(OutputFolder.Output(renamed, target)) + result.outputs.drop(1)
                _state.value = _state.value.copy(result = result.copy(outputs = outputs))
            }
            onDone(renamed != null)
        }
    }

    private fun rename(uri: Uri, name: String): Uri? = runCatching {
        if (uri.scheme == "file") {
            val file = File(requireNotNull(uri.path))
            val next = File(file.parentFile, name)
            if (next.exists() || !file.renameTo(next)) null else Uri.fromFile(next)
        } else {
            val values = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, name) }
            if (context.contentResolver.update(uri, values, null, null) > 0) uri else null
        }
    }.getOrNull()

    fun ratingAnswered() {
        _state.value = _state.value.copy(askRating = false)
        viewModelScope.launch { prefs.setRatingAsked() }
    }

    private class NotSmallerException : Exception()
    private class PrintUnavailableException : Exception()

    companion object {
        const val PDF_MIME = "application/pdf"
        const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    }
}
