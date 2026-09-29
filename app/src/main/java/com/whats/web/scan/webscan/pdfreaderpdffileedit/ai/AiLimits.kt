package com.whats.web.scan.webscan.pdfreaderpdffileedit.ai

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** FR-071. How many pages one run may take, and whether this phone can run the summary model at all. */
@Singleton
class AiLimits @Inject constructor(@ApplicationContext private val context: Context) {

    fun pageLimit(mode: AiJob, isPro: Boolean): Int = when {
        // Reading text off a page is the everyday tool Adobe Scan and CamScanner give away; keep it generous.
        mode == AiJob.EXTRACT_TEXT -> if (isPro) PRO_EXTRACT_PAGES else FREE_EXTRACT_PAGES
        !isPro -> FREE_PAGES
        mode == AiJob.SUMMARY -> PRO_SUMMARY_PAGES
        else -> PRO_TRANSLATE_PAGES
    }

    /**
     * The summary model is a 96M-parameter LLM running on the CPU: it needs 64-bit ARM and about
     * 230 MB of working memory. Translation has no such gate and stays available either way.
     */
    val summarySupported: Boolean by lazy {
        val arm64 = Build.SUPPORTED_64_BIT_ABIS.any { it.contains("arm64") }
        val memory = ActivityManager.MemoryInfo().also {
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it)
        }
        arm64 && memory.totalMem >= MIN_RAM_BYTES
    }

    companion object {
        const val FREE_PAGES = 1
        const val PRO_SUMMARY_PAGES = 10
        const val PRO_TRANSLATE_PAGES = 50
        const val FREE_EXTRACT_PAGES = 5
        const val PRO_EXTRACT_PAGES = 100

        /** Words of page text a summary prompt is truncated to, so prompt + output fit 4,096 tokens. */
        const val SUMMARY_MAX_WORDS = 2_500

        private const val MIN_RAM_BYTES = 2L * 1024 * 1024 * 1024
    }
}

/**
 * Which job is running; the select-page, run and result screens are shared between them.
 * [EXTRACT_TEXT] is plain OCR / text-layer copy ("image to text"), so its result carries no AI label.
 */
enum class AiJob {
    TRANSLATE,
    SUMMARY,
    EXTRACT_TEXT,
    ;

    val isGenerative: Boolean get() = this != EXTRACT_TEXT
}
