package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.sign

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
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.PdfAccess
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.PdfOverlay
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.PdfPageDimensions
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.PdfRenderSession
import com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf.PdfSigner
import com.whats.web.scan.webscan.pdfreaderpdffileedit.sign.Placement
import com.whats.web.scan.webscan.pdfreaderpdffileedit.sign.PlacementBounds
import com.whats.web.scan.webscan.pdfreaderpdffileedit.sign.SignatureInk
import com.whats.web.scan.webscan.pdfreaderpdffileedit.sign.SignatureStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class PlaceOnPdfUiState(
    val file: DocFile? = null,
    val pages: List<PdfPageDimensions> = emptyList(),
    val currentPage: Int = 0,
    val placements: List<Placement> = emptyList(),
    val selectedId: String? = null,
    val signatures: List<File> = emptyList(),
    val signatureLimitReached: Boolean = false,
    val noInkFound: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val failed: Boolean = false,
)

@HiltViewModel
class PlaceOnPdfViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: FileRepository,
    private val access: PdfAccess,
    private val store: SignatureStore,
    private val outputFolder: OutputFolder,
    private val incoming: IncomingFile,
) : ViewModel() {
    private val _state = MutableStateFlow(PlaceOnPdfUiState())
    val state: StateFlow<PlaceOnPdfUiState> = _state.asStateFlow()

    private var session: PdfRenderSession? = null

    init {
        viewModelScope.launch {
            store.signatures.collect { files -> _state.value = _state.value.copy(signatures = files) }
        }
    }

    fun load(key: String) {
        val file = repository.find(key) ?: run {
            _state.value = _state.value.copy(failed = true)
            return
        }
        _state.value = _state.value.copy(file = file)
        viewModelScope.launch {
            when (val result = access.open(file.uri)) {
                is PdfAccess.OpenResult.Success -> {
                    session?.close()
                    session = result.session
                    _state.value = _state.value.copy(pages = result.session.pageSizes())
                }

                else -> _state.value = _state.value.copy(failed = true)
            }
        }
    }

    suspend fun render(index: Int, widthPixels: Int): Bitmap? =
        runCatching { session?.renderPage(index, widthPixels) }.getOrNull()

    fun selectPage(index: Int) {
        _state.value = _state.value.copy(currentPage = index, selectedId = null)
    }

    fun select(id: String?) {
        _state.value = _state.value.copy(selectedId = id)
    }

    fun addSignature(file: File) {
        val placement = Placement(
            id = UUID.randomUUID().toString(),
            page = _state.value.currentPage,
            x = 0.3f,
            y = 0.6f,
            width = 0.35f,
            // Fractions are per-axis: a box 0.35 of the width is 0.35 × (imgH/imgW) × (pageW/pageH)
            // of the height, or the signature comes out stretched on a page that is not square.
            height = 0.35f * aspectOf(file) * pageAspect(),
            signature = file,
        )
        _state.value = _state.value.copy(
            placements = _state.value.placements + placement,
            selectedId = placement.id,
        )
    }

    fun addText(text: String) {
        if (text.isBlank()) return
        val placement = Placement(
            id = UUID.randomUUID().toString(),
            page = _state.value.currentPage,
            x = 0.3f,
            y = 0.4f,
            width = 0.35f,
            height = 0.05f,
            text = text,
        )
        _state.value = _state.value.copy(
            placements = _state.value.placements + placement,
            selectedId = placement.id,
        )
    }

    fun gesture(id: String, dx: Float, dy: Float, zoom: Float, rotation: Float) {
        _state.value = _state.value.copy(
            placements = _state.value.placements.map {
                if (it.id == id) PlacementBounds.applyGesture(it, dx, dy, zoom, rotation) else it
            },
        )
    }

    fun deleteSelected() {
        val id = _state.value.selectedId ?: return
        _state.value = _state.value.copy(
            placements = _state.value.placements.filterNot { it.id == id },
            selectedId = null,
        )
    }

    fun saveSignature(bitmap: Bitmap) = viewModelScope.launch {
        if (store.save(bitmap) == null) {
            _state.value = _state.value.copy(signatureLimitReached = true)
        }
    }

    /** FR-051: a photo of ink on paper becomes a stored transparent signature. */
    fun importSignature(uri: Uri) = viewModelScope.launch {
        if (store.isFull) {
            _state.value = _state.value.copy(signatureLimitReached = true)
            return@launch
        }
        val ink = withContext(Dispatchers.Default) {
            runCatching {
                val bitmap = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 2 })
                } ?: return@runCatching null
                SignatureInk.extract(bitmap)
            }.getOrNull()
        }
        if (ink == null) {
            _state.value = _state.value.copy(noInkFound = true)
        } else {
            store.save(ink)
        }
    }

    fun deleteSignature(file: File) = store.delete(file)

    fun messageShown() {
        _state.value = _state.value.copy(signatureLimitReached = false, noInkFound = false)
    }

    /** FR-053: writes `<name>_signed.pdf`; the original file is not touched. */
    fun save() {
        val file = _state.value.file ?: return
        val placements = _state.value.placements
        if (placements.isEmpty() || _state.value.saving) return
        _state.value = _state.value.copy(saving = true)
        viewModelScope.launch {
            val output = runCatching {
                val overlays = withContext(Dispatchers.IO) {
                    placements.groupBy { it.page }.mapValues { (_, list) -> list.map(::toOverlay) }
                }
                outputFolder.write(OutputFolder.signedName(file.name)) { out ->
                    access.openStream(file.uri).use { input ->
                        PdfSigner.sign(input, null, overlays, out, access.scratchDir)
                    }
                }
            }.getOrNull()
            output?.let { incoming.offer(it.uri, "application/pdf") }
            _state.value = _state.value.copy(saving = false, saved = output != null)
        }
    }

    private fun toOverlay(placement: Placement): PdfOverlay = when {
        placement.signature != null -> PdfOverlay.Image(
            png = placement.signature.readBytes(),
            x = placement.x,
            y = placement.y,
            width = placement.width,
            height = placement.height,
            rotation = placement.rotation,
            opacity = placement.opacity,
        )

        else -> PdfOverlay.Text(
            text = placement.text.orEmpty(),
            colorArgb = placement.textColorArgb,
            x = placement.x,
            y = placement.y,
            width = placement.width,
            height = placement.height,
            rotation = placement.rotation,
            opacity = placement.opacity,
        )
    }

    /** width ÷ height of the page being placed on. */
    private fun pageAspect(): Float =
        _state.value.pages.getOrNull(_state.value.currentPage)?.aspectRatio ?: 0.707f

    private fun aspectOf(file: File): Float {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0) return 0.4f
        return bounds.outHeight.toFloat() / bounds.outWidth
    }

    override fun onCleared() {
        session?.close()
    }
}
