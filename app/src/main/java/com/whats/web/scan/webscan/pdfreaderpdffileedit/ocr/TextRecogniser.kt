package com.whats.web.scan.webscan.pdfreaderpdffileedit.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * FR-063. ML Kit's bundled Latin recogniser: it runs in-process, needs no Play services and makes no
 * network call, so a scanned page can be read in airplane mode. Ported from pdfscanner `core/ocr`.
 *
 * *Limit:* Latin script only in v1. A scanned page in another script comes back empty, which the AI
 * screens report as "No readable text on this page".
 */
@Singleton
class TextRecogniser @Inject constructor() {

    private val recogniser by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun read(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        recogniser.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result -> continuation.resume(result.text) }
            .addOnFailureListener { error -> continuation.resumeWithException(error) }
    }
}
