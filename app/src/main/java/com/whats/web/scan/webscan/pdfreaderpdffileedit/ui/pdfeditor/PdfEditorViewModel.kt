package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.pdfeditor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.DocFile
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.FileRepository
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.IncomingFile
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.OutputFolder
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.prefs.AppPreferences
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.EditBox
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.PdfAccess
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.PdfEdit
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.PdfPageDimensions
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.PdfPageEditor
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.PdfRenderSession
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.retypeBox
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.text.EditFont
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.text.PageText
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.text.TextLine
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.text.TextLines
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

/** The editor's tools, as in the bottom bar of One Read's PDF editor. */
enum class EditorTool { EDIT_TEXT, ADD_TEXT, ADD_IMAGE }

/** Where the user goes after saving: stay (open the result) or continue in another tool. */
enum class AfterSave { OPEN, ANNOTATE, FILL_SIGN }

data class SavedPdf(val key: String?, val uri: Uri, val name: String, val then: AfterSave)

data class PdfEditorState(
    val file: DocFile? = null,
    val loading: Boolean = true,
    val failed: Boolean = false,
    val passwordRequired: Boolean = false,
    val wrongPassword: Boolean = false,
    val pages: List<PdfPageDimensions> = emptyList(),
    val tool: EditorTool = EditorTool.EDIT_TEXT,
    val edits: List<PdfEdit> = emptyList(),
    val selectedId: Long? = null,
    /** Text lines of the pages read so far (Edit text shows them as dashed boxes). */
    val lines: Map<Int, List<TextLine>> = emptyMap(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    val saved: SavedPdf? = null,
    val showIntro: Boolean = false,
    /** The last Edit-text tap landed on a page with no text layer (a scan). */
    val noTextHint: Boolean = false,
) {
    val selected: PdfEdit? get() = edits.firstOrNull { it.id == selectedId }
    val dirty: Boolean get() = edits.any { !(it is PdfEdit.Text && (it.unchanged || it.text.isBlank())) }
}

/**
 * Step 12b. The PDF editor: retype existing text, add text, add pictures, then write a copy with
 * [PdfPageEditor]. Every position is a fraction of the page, so what is placed on screen lands in the same
 * spot in the file at any zoom.
 */
@HiltViewModel
class PdfEditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: FileRepository,
    private val access: PdfAccess,
    private val outputFolder: OutputFolder,
    private val incoming: IncomingFile,
    private val prefs: AppPreferences,
) : ViewModel() {
    private val _state = MutableStateFlow(PdfEditorState())
    val state: StateFlow<PdfEditorState> = _state.asStateFlow()

    private var session: PdfRenderSession? = null
    private var pageText: PageText? = null
    private var password: String? = null
    private var nextId = 1L
    private val undo = ArrayDeque<List<PdfEdit>>()
    private val redo = ArrayDeque<List<PdfEdit>>()

    /** The edit whose text is being typed; typing pushes one undo step per box, not one per letter. */
    private var typingId: Long? = null

    fun load(key: String, password: String? = null) {
        val file = repository.find(key) ?: run {
            _state.value = _state.value.copy(loading = false, failed = true)
            return
        }
        this.password = password
        _state.value = _state.value.copy(file = file, loading = true, failed = false, wrongPassword = false)
        viewModelScope.launch {
            val intro = !prefs.editorIntroShown.first()
            when (val result = access.open(file.uri, password)) {
                is PdfAccess.OpenResult.Success -> {
                    session?.close()
                    session = result.session
                    val local = result.source.decryptedCopy ?: access.localCopy(file.uri)
                    pageText?.close()
                    // The decrypted copy needs no password; the original opens without one here.
                    pageText = runCatching { withContext(Dispatchers.IO) { PageText(local, null, access.scratchDir) } }.getOrNull()
                    _state.value = _state.value.copy(
                        loading = false,
                        passwordRequired = false,
                        pages = result.session.pageSizes(),
                        showIntro = intro,
                    )
                }
                PdfAccess.OpenResult.PasswordRequired -> _state.value = _state.value.copy(loading = false, passwordRequired = true)
                PdfAccess.OpenResult.WrongPassword -> _state.value = _state.value.copy(loading = false, passwordRequired = true, wrongPassword = true)
                is PdfAccess.OpenResult.Failed -> _state.value = _state.value.copy(loading = false, failed = true)
            }
        }
    }

    fun dismissIntro() {
        _state.value = _state.value.copy(showIntro = false)
        viewModelScope.launch { prefs.setEditorIntroShown() }
    }

    suspend fun render(index: Int, widthPixels: Int): Bitmap? =
        runCatching { session?.renderPage(index, widthPixels) }.getOrNull()

    /** Reads a page's text lines once, for the dashed boxes Edit text shows. */
    fun ensureLines(page: Int) {
        if (page in _state.value.lines) return
        val reader = pageText ?: return
        viewModelScope.launch {
            val lines = withContext(Dispatchers.IO) { reader.lines(page) }
            _state.value = _state.value.copy(lines = _state.value.lines + (page to lines))
        }
    }

    fun selectTool(tool: EditorTool) {
        finishTyping()
        _state.value = _state.value.copy(tool = tool, selectedId = null, noTextHint = false)
    }

    /**
     * A tap on page [page] at ([x], [y]). Edit text: the line under the finger becomes an editable box
     * (or the box already made for it is selected). Add text: a new empty box starts there. Anywhere
     * else a tap just clears the selection.
     */
    fun tapPage(page: Int, x: Float, y: Float) {
        finishTyping()
        val state = _state.value
        state.edits.lastOrNull { it.page == page && it.box.contains(x, y, TAP_SLOP) }?.let {
            _state.value = state.copy(selectedId = it.id, noTextHint = false)
            return
        }
        when (state.tool) {
            EditorTool.EDIT_TEXT -> {
                val lines = state.lines[page]
                val line = lines?.let { TextLines.at(it, x, y) }
                if (line == null) {
                    _state.value = state.copy(selectedId = null, noTextHint = lines != null && lines.isEmpty())
                    return
                }
                val pageHeight = state.pages.getOrNull(page)?.heightPoints?.toFloat() ?: return
                val size = if (line.fontSize > 0f) line.fontSize else (line.bottom - line.top) * pageHeight / GLYPH_HEIGHT
                val cover = EditBox(line.left, line.top, line.right, line.bottom)
                push()
                val edit = PdfEdit.Text(
                    id = nextId++,
                    page = page,
                    // Room to type a little more than was there.
                    box = retypeBox(line.left, minOf(1f, line.right + (line.right - line.left) * 0.25f + 0.02f), line.bottom, size, pageHeight),
                    text = line.text,
                    fontSize = size,
                    font = line.family,
                    bold = line.bold,
                    italic = line.italic,
                    cover = cover,
                    original = line.text,
                )
                _state.value = _state.value.copy(edits = _state.value.edits + edit, selectedId = edit.id, noTextHint = false)
            }
            EditorTool.ADD_TEXT -> {
                val dims = state.pages.getOrNull(page) ?: return
                val size = DEFAULT_SIZE
                val width = NEW_BOX_WIDTH
                val height = size * PdfPageEditor.LINE_SPACING / dims.heightPoints
                push()
                val edit = PdfEdit.Text(
                    id = nextId++,
                    page = page,
                    box = EditBox(
                        x.coerceIn(0f, 1f - width),
                        y.coerceIn(0f, 1f - height),
                        (x.coerceIn(0f, 1f - width) + width),
                        (y.coerceIn(0f, 1f - height) + height),
                    ),
                    text = "",
                    fontSize = size,
                )
                _state.value = _state.value.copy(edits = _state.value.edits + edit, selectedId = edit.id)
            }
            EditorTool.ADD_IMAGE -> _state.value = state.copy(selectedId = null)
        }
    }

    fun select(id: Long?) {
        finishTyping()
        _state.value = _state.value.copy(selectedId = id)
    }

    fun updateText(id: Long, text: String) {
        if (typingId != id) {
            push()
            typingId = id
        }
        replace(id) { if (it is PdfEdit.Text) it.copy(text = text) else it }
    }

    /** Moves a box by fractions of the page, keeping it on the page. */
    fun move(id: Long, dx: Float, dy: Float) = replace(id) { edit ->
        val b = edit.box
        val nx = (b.left + dx).coerceIn(0f, 1f - b.width)
        val ny = (b.top + dy).coerceIn(0f, 1f - b.height)
        val moved = EditBox(nx, ny, nx + b.width, ny + b.height)
        when (edit) {
            is PdfEdit.Text -> edit.copy(box = moved)
            is PdfEdit.Image -> edit.copy(box = moved)
        }
    }

    /** Drags the bottom-right handle; pictures keep their shape. */
    fun resize(id: Long, dx: Float, dy: Float) = replace(id) { edit ->
        val b = edit.box
        when (edit) {
            is PdfEdit.Text -> edit.copy(
                box = b.copy(
                    right = (b.right + dx).coerceIn(b.left + MIN_BOX, 1f),
                    bottom = (b.bottom + dy).coerceIn(b.top + MIN_BOX / 2, 1f),
                ),
            )
            is PdfEdit.Image -> {
                val width = (b.width + dx).coerceIn(MIN_BOX, 1f - b.left)
                // Height follows width through the picture's own aspect ratio.
                val height = (width * b.height / b.width).coerceAtMost(1f - b.top)
                edit.copy(box = b.copy(right = b.left + width, bottom = b.top + height))
            }
        }
    }

    /** Called when a drag starts, so one drag is one undo step. */
    fun beginGesture() {
        finishTyping()
        push()
    }

    fun changeSize(step: Float) = restyle { it.copy(fontSize = (it.fontSize + step).coerceIn(MIN_SIZE, MAX_SIZE)) }

    fun setColor(argb: Int) = restyle { it.copy(colorArgb = argb) }

    fun cycleFont() = restyle { it.copy(font = EditFont.entries[(it.font.ordinal + 1) % EditFont.entries.size]) }

    fun toggleBold() = restyle { it.copy(bold = !it.bold) }

    fun toggleItalic() = restyle { it.copy(italic = !it.italic) }

    private fun restyle(change: (PdfEdit.Text) -> PdfEdit.Text) {
        val id = _state.value.selectedId ?: return
        finishTyping()
        push()
        replace(id) { if (it is PdfEdit.Text) change(it).copy(restyled = true) else it }
    }

    fun deleteSelected() {
        val id = _state.value.selectedId ?: return
        finishTyping()
        push()
        _state.value = _state.value.copy(edits = _state.value.edits.filterNot { it.id == id }, selectedId = null)
    }

    fun undo() {
        finishTyping()
        val previous = undo.removeLastOrNull() ?: return
        redo.addLast(_state.value.edits)
        _state.value = _state.value.copy(edits = previous, selectedId = null)
        refreshHistory()
    }

    fun redo() {
        finishTyping()
        val next = redo.removeLastOrNull() ?: return
        undo.addLast(_state.value.edits)
        _state.value = _state.value.copy(edits = next, selectedId = null)
        refreshHistory()
    }

    /** A picture from the gallery, shrunk to a sensible size, placed in the middle of [page]. */
    fun addImage(uri: Uri, page: Int) {
        val dims = _state.value.pages.getOrNull(page) ?: return
        viewModelScope.launch {
            val picture = withContext(Dispatchers.IO) { runCatching { decodeForPdf(uri) }.getOrNull() } ?: return@launch
            val (bytes, w, h) = picture
            val pageAspect = dims.widthPoints.toFloat() / dims.heightPoints
            val width = IMAGE_WIDTH
            val height = width * (h.toFloat() / w) * pageAspect
            val top = ((1f - height) / 2f).coerceAtLeast(0f)
            push()
            val edit = PdfEdit.Image(
                id = nextId++,
                page = page,
                box = EditBox((1f - width) / 2f, top, (1f + width) / 2f, (top + height).coerceAtMost(1f)),
                bytes = bytes,
            )
            _state.value = _state.value.copy(edits = _state.value.edits + edit, selectedId = edit.id)
        }
    }

    /** JPEG for photos (small), PNG when the picture has transparency (logos, stamps). */
    private fun decodeForPdf(uri: Uri): Triple<ByteArray, Int, Int>? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_IMAGE_PX) sample *= 2
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val out = ByteArrayOutputStream()
        if (bitmap.hasAlpha()) bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        else bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        val result = Triple(out.toByteArray(), bitmap.width, bitmap.height)
        bitmap.recycle()
        return result
    }

    fun submitPassword(password: String) {
        val key = _state.value.file?.key ?: return
        load(key, password)
    }

    /** Writes `<name>_edited.pdf`; the original is never touched. */
    fun save(then: AfterSave = AfterSave.OPEN) {
        val file = _state.value.file ?: return
        if (_state.value.saving) return
        finishTyping()
        val edits = _state.value.edits.filterNot { it is PdfEdit.Text && it.text.isBlank() && it.cover == null }
        _state.value = _state.value.copy(saving = true, saveFailed = false, selectedId = null)
        viewModelScope.launch {
            val result = try {
                val output = outputFolder.write(OutputFolder.derivedName(file.name, "edited")) { out ->
                    access.openStream(file.uri).use { input ->
                        PdfPageEditor.apply(input, password, edits, out, access.scratchDir, fallbackFont())
                    }
                }
                val key = incoming.register(output.uri, "application/pdf")?.key
                SavedPdf(key, output.uri, output.name, then)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            _state.value = _state.value.copy(saving = false, saved = result, saveFailed = result == null)
        }
    }

    fun savedHandled() {
        _state.value = _state.value.copy(saved = null, saveFailed = false)
    }

    private fun push() {
        undo.addLast(_state.value.edits)
        if (undo.size > HISTORY) undo.removeFirst()
        redo.clear()
        refreshHistory()
    }

    private fun refreshHistory() {
        _state.value = _state.value.copy(canUndo = undo.isNotEmpty(), canRedo = redo.isNotEmpty())
    }

    private fun finishTyping() {
        typingId = null
    }

    private fun replace(id: Long, change: (PdfEdit) -> PdfEdit) {
        _state.value = _state.value.copy(edits = _state.value.edits.map { if (it.id == id) change(it) else it })
    }

    /** A system font with Latin, Greek and Cyrillic, for text the PDF's built-in fonts cannot hold. */
    private fun fallbackFont(): File? = FALLBACK_FONTS.map(::File).firstOrNull { it.exists() }

    override fun onCleared() {
        session?.close()
        pageText?.close()
    }

    companion object {
        const val DEFAULT_SIZE = 14f
        const val MIN_SIZE = 4f
        const val MAX_SIZE = 96f
        private const val NEW_BOX_WIDTH = 0.4f
        private const val IMAGE_WIDTH = 0.4f
        private const val MIN_BOX = 0.03f
        private const val TAP_SLOP = 0.005f
        private const val GLYPH_HEIGHT = 0.7f
        private const val HISTORY = 50
        private const val MAX_IMAGE_PX = 1600
        private const val JPEG_QUALITY = 88
        private val FALLBACK_FONTS = listOf(
            "/system/fonts/RobotoStatic-Regular.ttf",
            "/system/fonts/Roboto-Regular.ttf",
            "/system/fonts/NotoSans-Regular.ttf",
            "/system/fonts/DroidSans.ttf",
        )

        /** The text colours on offer, as in One Read's colour button. */
        val COLORS = listOf(
            0xFF000000.toInt(),
            0xFF1E6FD9.toInt(),
            0xFFD32F2F.toInt(),
            0xFF1E9E5A.toInt(),
            0xFFF4731F.toInt(),
            0xFF7B3FB5.toInt(),
            0xFFFFFFFF.toInt(),
        )
    }
}
