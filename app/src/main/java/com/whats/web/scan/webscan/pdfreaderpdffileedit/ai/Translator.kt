package com.whats.web.scan.webscan.pdfreaderpdffileedit.ai

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** FR-064: exactly the seven target languages the client asked for. English is ML Kit's pivot, not a row. */
enum class TargetLanguage(val code: String) {
    ITALIAN(TranslateLanguage.ITALIAN),
    POLISH(TranslateLanguage.POLISH),
    PORTUGUESE(TranslateLanguage.PORTUGUESE),
    DUTCH(TranslateLanguage.DUTCH),
    FRENCH(TranslateLanguage.FRENCH),
    GERMAN(TranslateLanguage.GERMAN),
    SPANISH(TranslateLanguage.SPANISH),
    ;

    /** The name in the app's language, so the sheet reads in the user's own words. */
    val displayName: String get() = java.util.Locale(code).getDisplayLanguage().replaceFirstChar { it.uppercase() }

    companion object {
        /** ML Kit ships one Portuguese model, so the two rows in the client's screenshot collapse to one. */
        const val APPROX_MODEL_SIZE = "30 MB"
    }
}

/**
 * FR-065. On-device translation through ML Kit. The only network call is the one-time download of the
 * language the user picked, from Google's ML Kit host; the document text itself never leaves the phone.
 */
@Singleton
class Translator @Inject constructor() {

    private val models = RemoteModelManager.getInstance()

    suspend fun isDownloaded(language: TargetLanguage): Boolean =
        runCatching { models.isModelDownloaded(TranslateRemoteModel.Builder(language.code).build()).await() }
            .getOrDefault(false)

    suspend fun downloadedLanguages(): Set<TargetLanguage> =
        TargetLanguage.entries.filter { isDownloaded(it) }.toSet()

    /** Downloads one language's model. Wi-Fi only unless the user said otherwise. */
    suspend fun download(language: TargetLanguage, wifiOnly: Boolean = true) {
        val conditions = DownloadConditions.Builder()
            .apply { if (wifiOnly) requireWifi() }
            .build()
        models.download(TranslateRemoteModel.Builder(language.code).build(), conditions).await()
    }

    /** The language of the page, or null when ML Kit will not commit ("und"). */
    suspend fun detectSource(text: String): String? {
        val client = LanguageIdentification.getClient()
        return try {
            val code = client.identifyLanguage(text.take(LANGUAGE_ID_CHARS)).await()
            code.takeIf { it != "und" && TranslateLanguage.fromLanguageTag(it) != null }
        } catch (_: Exception) {
            null
        } finally {
            client.close()
        }
    }

    /**
     * Translates paragraph by paragraph so blank-line structure survives; ML Kit itself works a
     * sentence at a time and pivots through English, which is the quality limit stated in the help sheet.
     */
    suspend fun translate(
        text: String,
        sourceCode: String,
        target: TargetLanguage,
        onProgress: (Float) -> Unit = {},
    ): String {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceCode)
            .setTargetLanguage(target.code)
            .build()
        val translator = Translation.getClient(options)
        return try {
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            val paragraphs = text.split(PARAGRAPH_BREAK)
            paragraphs.mapIndexed { index, paragraph ->
                onProgress((index + 1f) / paragraphs.size)
                if (paragraph.isBlank()) paragraph else translateOne(translator, paragraph)
            }.joinToString("\n\n")
        } finally {
            translator.close()
        }
    }

    private suspend fun translateOne(
        translator: com.google.mlkit.nl.translate.Translator,
        paragraph: String,
    ): String = suspendCancellableCoroutine { continuation ->
        translator.translate(paragraph)
            .addOnSuccessListener { continuation.resume(it) }
            .addOnFailureListener { continuation.resumeWithException(it) }
    }

    private companion object {
        val PARAGRAPH_BREAK = Regex("\\n\\s*\\n")
        const val LANGUAGE_ID_CHARS = 1_000
    }
}
