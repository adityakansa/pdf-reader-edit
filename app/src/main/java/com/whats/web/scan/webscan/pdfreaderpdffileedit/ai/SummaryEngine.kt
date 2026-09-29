package com.whats.web.scan.webscan.pdfreaderpdffileedit.ai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FR-068. Where the summary model file is. It ships **inside the app** as an install-time Play asset
 * pack, so there is no download card and no first-run wait: after installing, the file is simply there.
 */
@Singleton
class SummaryModelDelivery @Inject constructor(@ApplicationContext private val context: Context) {

    /** The `.gguf` on this device, or null when the pack is missing (e.g. a bare debug APK). */
    fun modelFile(): File? {
        // An install-time pack is unpacked beside the app's other assets; the pack name is the folder.
        val candidates = listOf(
            File(context.filesDir.parentFile, "$PACK_NAME/$MODEL_FILE"),
            File(context.getExternalFilesDir(null), "$PACK_NAME/$MODEL_FILE"),
        )
        return candidates.firstOrNull { it.exists() && it.length() == MODEL_BYTES }
    }

    companion object {
        const val PACK_NAME = "ai_summary_model"
        const val MODEL_FILE = "Minueza-2-96M-Instruct-Variant-04.Q2_K.gguf"
        const val MODEL_BYTES = 65_518_432L
    }
}

/**
 * FR-069. The summary itself, generated on the CPU by llama.cpp through [LlamaBridge].
 *
 * The model is small and freely invents detail; the client accepted that. What is *not* left to it is
 * the shape of the answer: it does not obey "write three paragraphs" (2–11 were seen in testing), so
 * [clampParagraphs] does it afterwards.
 */
@Singleton
class SummaryEngine @Inject constructor(
    private val delivery: SummaryModelDelivery,
    private val limits: AiLimits,
) {
    val available: Boolean
        get() = limits.summarySupported && delivery.modelFile() != null && LlamaBridge.isAvailable

    /** Streams the answer as it decodes; collecting can be cancelled, which stops the decode loop. */
    fun summarise(pageText: String): Flow<String> = flow {
        val model = delivery.modelFile() ?: error("Summary model is not installed")
        val prompt = promptFor(pageText)
        val builder = StringBuilder()
        LlamaBridge.generate(
            modelPath = model.absolutePath,
            prompt = prompt,
            maxTokens = MAX_NEW_TOKENS,
            temperature = TEMPERATURE,
            repeatPenalty = REPEAT_PENALTY,
            contextTokens = CONTEXT_TOKENS,
        ) { token ->
            builder.append(token)
        }
        emit(clampParagraphs(builder.toString()))
    }.flowOn(Dispatchers.Default)

    private fun promptFor(pageText: String): String {
        val trimmed = pageText.split(WHITESPACE).take(AiLimits.SUMMARY_MAX_WORDS).joinToString(" ")
        return "<|im_start|>system\nYou summarise documents in flowing prose.<|im_end|>\n" +
            "<|im_start|>user\nWrite exactly three paragraphs of prose summarising the text below. " +
            "Do not use lists or headings.\n\n$trimmed<|im_end|>\n<|im_start|>assistant\n"
    }

    /**
     * Exactly two or three paragraphs, whatever the model produced: drop exact repeats, keep the first
     * three, and if only one survived, split it at the sentence boundary nearest its middle.
     */
    fun clampParagraphs(raw: String): String {
        val paragraphs = raw.split(PARAGRAPH_BREAK)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_PARAGRAPHS)
        if (paragraphs.size >= 2) return paragraphs.joinToString("\n\n")
        val single = paragraphs.firstOrNull().orEmpty()
        if (single.isEmpty()) return single
        val breaks = SENTENCE_END.findAll(single).map { it.range.last + 1 }.toList()
        val middle = single.length / 2
        val split = breaks.minByOrNull { kotlin.math.abs(it - middle) } ?: return single
        if (split <= 0 || split >= single.length) return single
        return single.substring(0, split).trim() + "\n\n" + single.substring(split).trim()
    }

    private companion object {
        val PARAGRAPH_BREAK = Regex("\\n\\s*\\n")
        val WHITESPACE = Regex("\\s+")
        val SENTENCE_END = Regex("[.!?](\\s|$)")
        const val MAX_PARAGRAPHS = 3
        const val MAX_NEW_TOKENS = 400
        const val TEMPERATURE = 0.4f
        const val REPEAT_PENALTY = 1.2f
        const val CONTEXT_TOKENS = 4_096
    }
}

/**
 * The JNI surface of the vendored llama.cpp build (MIT, tag `b11259`), arm64-v8a.
 *
 * The native library is **not built yet**: `app/src/main/cpp` and its CMake wiring are the remaining
 * work for FR-069. Until it lands [isAvailable] is false, so the app reports "AI Summary isn't
 * supported on this device" rather than crashing, and Translate is unaffected.
 */
object LlamaBridge {

    val isAvailable: Boolean = runCatching { System.loadLibrary("llama_jni") }.isSuccess

    fun generate(
        modelPath: String,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        repeatPenalty: Float,
        contextTokens: Int,
        onToken: (String) -> Unit,
    ) {
        check(isAvailable) { "llama.cpp native library is not in this build" }
        nativeGenerate(modelPath, prompt, maxTokens, temperature, repeatPenalty, contextTokens, onToken)
    }

    @Suppress("LongParameterList")
    private external fun nativeGenerate(
        modelPath: String,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        repeatPenalty: Float,
        contextTokens: Int,
        onToken: (String) -> Unit,
    )
}
