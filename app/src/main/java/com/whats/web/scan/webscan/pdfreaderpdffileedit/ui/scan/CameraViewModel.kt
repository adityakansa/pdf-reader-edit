package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging.DocumentDetector
import com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging.QuadSmoother
import com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging.StabilityTracker
import com.whats.web.scan.webscan.pdfreaderpdffileedit.imaging.model.Quad
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class CameraUiState(
    val quad: Quad? = null,
    val autoCaptureProgress: Float = 0f,
    val autoCapture: Boolean = true,
    val flashOn: Boolean = false,
    val capturing: Boolean = false,
    val pageCount: Int = 0,
    /** Set when a capture fired by itself, so the screen can shoot without the user pressing anything. */
    val fireCapture: Boolean = false,
)

/**
 * FR-042. Document mode only — pdfscanner's camera view model carries seven modes and 1,100 lines;
 * this keeps the parts a document scanner needs: live edges on the analysis stream, a stability
 * tracker driving auto-capture, flash, and the session the review screen reads.
 */
@HiltViewModel
class CameraViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: ScanSessionStore,
) : ViewModel() {

    private val detector by lazy { DocumentDetector(context) }
    private val smoother = QuadSmoother()
    private val stability = StabilityTracker()

    private val _state = MutableStateFlow(CameraUiState())
    val state: StateFlow<CameraUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            session.pages.collect { pages -> _state.value = _state.value.copy(pageCount = pages.size) }
        }
    }

    /** Called per analysis frame. The proxy is closed by the caller. */
    fun onFrame(image: ImageProxy, inFocus: Boolean) {
        val plane = image.planes.firstOrNull() ?: return
        val detected = runCatching {
            detector.detect(
                luma = plane.buffer,
                width = image.width,
                height = image.height,
                rowStride = plane.rowStride,
                rotationDegrees = image.imageInfo.rotationDegrees,
            )
        }.getOrNull()
        val now = System.currentTimeMillis()
        val smoothed = smoother.update(detected, now)
        val signal = stability.update(smoothed, inFocus, now)
        _state.value = _state.value.copy(
            quad = smoothed,
            autoCaptureProgress = signal.progress,
            fireCapture = _state.value.autoCapture && signal.fire && !_state.value.capturing,
        )
    }

    fun toggleFlash() {
        _state.value = _state.value.copy(flashOn = !_state.value.flashOn)
    }

    fun toggleAutoCapture() {
        _state.value = _state.value.copy(autoCapture = !_state.value.autoCapture)
        stability.reset()
    }

    fun captureStarted() {
        _state.value = _state.value.copy(capturing = true, fireCapture = false)
    }

    /** The still, with the model-quality detector run over the full frame rather than the preview. */
    fun onCaptured(jpeg: ByteArray) {
        viewModelScope.launch {
            val quad = withContext(Dispatchers.Default) {
                runCatching {
                    val bitmap = decodeForDetection(jpeg) ?: return@runCatching null
                    detector.detect(bitmap).also { bitmap.recycle() }
                }.getOrNull()
            } ?: Quad.FULL_IMAGE
            session.add(jpeg, quad)
            smoother.reset()
            stability.reset()
            _state.value = _state.value.copy(capturing = false)
        }
    }

    fun captureFailed() {
        _state.value = _state.value.copy(capturing = false)
    }

    /** FR-042 gallery import: a picked photo joins the session exactly like a capture. */
    fun importPhotos(uris: List<Uri>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                uris.forEach { uri ->
                    val bytes = runCatching {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }.getOrNull() ?: return@forEach
                    val quad = runCatching {
                        decodeForDetection(bytes)?.let { bitmap ->
                            detector.detect(bitmap).also { _ -> bitmap.recycle() }
                        }
                    }.getOrNull() ?: Quad.FULL_IMAGE
                    session.add(bytes, quad)
                }
            }
        }
    }

    fun discard() = session.clear()

    /** Detection does not need the full 12 MP frame, and decoding one costs 48 MB. */
    private fun decodeForDetection(jpeg: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = generateSequence(1) { it * 2 }.first { longest / it <= DETECTION_MAX_SIDE }
        }
        return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, options)
    }

    private companion object {
        const val DETECTION_MAX_SIDE = 1_280
    }
}
