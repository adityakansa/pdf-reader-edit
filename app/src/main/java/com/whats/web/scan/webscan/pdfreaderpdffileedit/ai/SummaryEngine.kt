package com.whats.web.scan.webscan.pdfreaderpdffileedit.ai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FR-068. Where the summary model file is. It ships **inside the app** as an install-time Play asset
 * pack (`:ai_summary_model`), so there is no download card and no first-run wait.
 *
 * Install-time packs are merged into the app's assets, so the file is read through [Context.getAssets]
 * — `AssetPackManager.getPackLocation` returns null for them. llama.cpp opens models by path, so the
 * asset is copied once to `noBackupFilesDir` (the pack stores it uncompressed; the copy is a plain
 * byte stream) and that copy is reused on every later run.
 */
@Singleton
class SummaryModelDelivery @Inject constructor(@ApplicationContext private val context: Context) {

    private val installed: File get() = File(context.noBackupFilesDir, "models/$MODEL_FILE")

    /** True when the pack is in this install (a bare debug APK built without the model has no pack). */
    val isInstalled: Boolean by lazy {
        installed.length() == MODEL_BYTES ||
            runCatching { context.assets.openFd(MODEL_FILE).use { it.length == MODEL_BYTES } }.getOrDefault(false) ||
            runCatching { context.assets.list("")?.contains(MODEL_FILE) == true }.getOrDefault(false)
    }

    /** The `.gguf` on disk, copying it out of the pack the first time. Null when the pack is missing. */
    suspend fun modelFile(): File? = withContext(Dispatchers.IO) {
        synchronized(this@SummaryModelDelivery) {
            if (installed.length() == MODEL_BYTES) return@synchronized installed
            val partial = File(installed.parentFile, "$MODEL_FILE.part")
            try {
                installed.parentFile?.mkdirs()
                context.assets.open(MODEL_FILE).use { input -> partial.outputStream().use(input::copyTo) }
                if (partial.length() != MODEL_BYTES || !partial.renameTo(installed)) {
                    partial.delete()
                    return@synchronized null
                }
                installed
            } catch (_: IOException) {
                partial.delete()
                null
            }
        }
    }

    companion object {
        const val PACK_NAME = "ai_summary_model"
        const val MODEL_FILE = "Minueza-2-96M-Instruct-Variant-04.Q2_K.gguf"
        const val MODEL_BYTES = 65_518_432L
    }
}

/** One step of a running summary: the text so far, and whether it is the final, clamped answer. */
data class SummaryUpdate(val text: String, val finished: Boolean)

/**
 * FR-069. The summary itself, generated on the CPU by llama.cpp through [LlamaBridge].
 *
 * The model is small and freely invents detail; the client accepted that. What is *not* left to it is
 * the shape of the answer: it does not obey "write three paragraphs" (2–11 were seen in testing), so
 * [SummaryParagraphs.clamp] does it afterwards.
 */
@Singleton
class SummaryEngine @Inject constructor(
    private val delivery: SummaryModelDelivery,
    private val limits: AiLimits,
) {
    val available: Boolean
        get() = limits.summarySupported && LlamaBridge.isAvailable && delivery.isInstalled

    /**
     * Streams the answer as it decodes, then emits the clamped result with `finished = true`.
     * Cancelling the collector makes the next token callback return false, which ends the native loop.
     */
    fun summarise(pageText: String): Flow<SummaryUpdate> = channelFlow {
        val model = delivery.modelFile() ?: error("Summary model is not installed")
        val scope = currentCoroutineContext()
        val raw = ByteArrayOutputStream()
        val status = LlamaBridge.generate(
            modelPath = model.absolutePath,
            prompt = promptFor(pageText),
            maxTokens = MAX_NEW_TOKENS,
            temperature = TEMPERATURE,
            repeatPenalty = REPEAT_PENALTY,
            contextTokens = CONTEXT_TOKENS,
            threads = threads(),
        ) { bytes ->
            raw.write(bytes)
            trySend(SummaryUpdate(SummaryParagraphs.decodeComplete(raw.toByteArray()), finished = false))
            scope.isActive
        }
        check(status == 0) { "llama.cpp failed with status $status" }
        send(SummaryUpdate(SummaryParagraphs.clamp(raw.toString(Charsets.UTF_8.name())), finished = true))
    }.flowOn(Dispatchers.Default)

    private fun promptFor(pageText: String): String {
        val trimmed = pageText.split(WHITESPACE).take(AiLimits.SUMMARY_MAX_WORDS).joinToString(" ")
        return "<|im_start|>system\nYou summarise documents in flowing prose.<|im_end|>\n" +
            "<|im_start|>user\nWrite exactly three paragraphs of prose summarising the text below. " +
            "Do not use lists or headings.\n\n$trimmed<|im_end|>\n<|im_start|>assistant\n"
    }

    private fun threads(): Int = Runtime.getRuntime().availableProcessors().coerceIn(1, MAX_THREADS)

    private companion object {
        val WHITESPACE = Regex("\\s+")
        const val MAX_NEW_TOKENS = 400
        const val TEMPERATURE = 0.4f
        const val REPEAT_PENALTY = 1.2f
        const val CONTEXT_TOKENS = 4_096
        const val MAX_THREADS = 4
    }
}

/** Receives each decoded piece as raw bytes; returning false stops generation. */
fun interface TokenSink {
    fun onToken(bytes: ByteArray): Boolean
}

/**
 * The JNI surface of the llama.cpp build (MIT, submodule at tag `b11259`, `app/src/main/cpp`), arm64-v8a.
 * When the build has no native library (sources not checked out, or a 32-bit phone) [isAvailable] is
 * false, so the app says "AI Summary isn't supported on this device" instead of crashing.
 */
object LlamaBridge {

    val isAvailable: Boolean by lazy { runCatching { System.loadLibrary("llama_jni") }.isSuccess }

    /** Blocks until done; returns 0 on success, a negative status from `llama_jni.cpp` otherwise. */
    @Suppress("LongParameterList")
    fun generate(
        modelPath: String,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        repeatPenalty: Float,
        contextTokens: Int,
        threads: Int,
        sink: TokenSink,
    ): Int {
        check(isAvailable) { "llama.cpp native library is not in this build" }
        return nativeGenerate(modelPath, prompt, maxTokens, temperature, repeatPenalty, contextTokens, threads, sink)
    }

    @Suppress("LongParameterList")
    private external fun nativeGenerate(
        modelPath: String,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        repeatPenalty: Float,
        contextTokens: Int,
        threads: Int,
        sink: TokenSink,
    ): Int
}
