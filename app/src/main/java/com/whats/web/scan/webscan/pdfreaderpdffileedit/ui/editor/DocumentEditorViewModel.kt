package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.editor

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.DocFile
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.FileRepository
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.OutputFolder
import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.DocxWriter
import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.SimpleMarkup
import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.TextToHtml
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.TextToPdf
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class SaveFormat(val extension: String, val mimeType: String) {
    WORD("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    PDF("pdf", "application/pdf"),
    TEXT("txt", "text/plain"),
}

data class EditorUiState(
    /** The .txt being edited, or null for a new document. */
    val file: DocFile? = null,
    val initialText: String = "",
    val loading: Boolean = false,
    val saving: Boolean = false,
    /** The file just written; the screen hands it to navigation, which closes the editor and opens it. */
    val saved: OutputFolder.Output? = null,
    val savedMime: String? = null,
    val error: Boolean = false,
)

/**
 * New documents and editing text files. New documents save as Word, PDF or text into the app's
 * output folder; an existing .txt saves back over itself (or, if it cannot be written, as a copy).
 */
@HiltViewModel
class DocumentEditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: FileRepository,
    private val outputFolder: OutputFolder,
    private val textToPdf: TextToPdf,
) : ViewModel() {
    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()
    private var loadedKey: String? = null

    fun load(key: String?) {
        if (key == null || key == loadedKey) return
        loadedKey = key
        val file = repository.find(key) ?: return
        _state.value = EditorUiState(file = file, loading = true)
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(file.uri)?.use { TextToHtml.decode(it.readBytes()) }
                }.getOrNull()
            }
            _state.value = _state.value.copy(loading = false, initialText = text.orEmpty(), error = text == null)
        }
    }

    fun suggestedName(): String = OutputFolder.documentName()

    /** A new document, in the chosen format. */
    fun saveNew(text: String, name: String, format: SaveFormat) = save {
        when (format) {
            SaveFormat.PDF -> textToPdf.save(name, styled(text))
            SaveFormat.WORD -> outputFolder.write(name, format.extension, format.mimeType) { out ->
                DocxWriter.write(SimpleMarkup.parse(text), name, out)
            }
            SaveFormat.TEXT -> outputFolder.write(name, format.extension, format.mimeType) { out ->
                out.write(SimpleMarkup.plainText(text).toByteArray(Charsets.UTF_8))
            }
        }
    }

    /** Saves an edited .txt over the original; a file that cannot be written is saved as a copy. */
    fun saveText(text: String) {
        val file = _state.value.file ?: return
        save {
            val overwritten = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(file.uri, "wt")?.use {
                        it.write(text.toByteArray(Charsets.UTF_8))
                    } != null
                }.getOrDefault(false)
            }
            if (overwritten) {
                OutputFolder.Output(file.uri, file.name)
            } else {
                outputFolder.write(OutputFolder.derivedName(file.name, "edited"), "txt", "text/plain") { out ->
                    out.write(text.toByteArray(Charsets.UTF_8))
                }
            }
        }
    }

    private fun save(work: suspend () -> OutputFolder.Output) {
        if (_state.value.saving) return
        _state.value = _state.value.copy(saving = true, error = false)
        viewModelScope.launch {
            val output = try {
                work()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            _state.value = _state.value.copy(
                saving = false,
                saved = output,
                savedMime = output?.name?.substringAfterLast('.')?.let(::mimeOf),
                error = output == null,
            )
        }
    }

    fun messageShown() {
        _state.value = _state.value.copy(error = false)
    }

    private fun mimeOf(extension: String) = SaveFormat.entries.firstOrNull { it.extension == extension }?.mimeType

    /** Headings larger and bold, bold/italic runs, bullets and numbers — for the PDF export. */
    private fun styled(text: String): CharSequence {
        val out = SpannableStringBuilder()
        var number = 0
        SimpleMarkup.parse(text).forEach { p ->
            number = if (p.kind == SimpleMarkup.Kind.NUMBERED) number + 1 else 0
            val start = out.length
            when (p.kind) {
                SimpleMarkup.Kind.BULLET -> out.append("•  ")
                SimpleMarkup.Kind.NUMBERED -> out.append("$number.  ")
                else -> Unit
            }
            p.runs.forEach { run ->
                val runStart = out.length
                out.append(run.text)
                val style = when {
                    run.bold && run.italic -> Typeface.BOLD_ITALIC
                    run.bold -> Typeface.BOLD
                    run.italic -> Typeface.ITALIC
                    else -> null
                }
                style?.let { out.setSpan(StyleSpan(it), runStart, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
            }
            if (p.kind == SimpleMarkup.Kind.HEADING1 || p.kind == SimpleMarkup.Kind.HEADING2) {
                val scale = if (p.kind == SimpleMarkup.Kind.HEADING1) HEADING1_SCALE else HEADING2_SCALE
                out.setSpan(RelativeSizeSpan(scale), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            out.append('\n')
        }
        return out
    }

    private companion object {
        const val HEADING1_SCALE = 1.6f
        const val HEADING2_SCALE = 1.3f
    }
}
