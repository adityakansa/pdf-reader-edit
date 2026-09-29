package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.ai

import androidx.annotation.StringRes
import com.whats.web.scan.webscan.pdfreaderpdffileedit.R
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ai.AiJob

/** One place that names each job on screen, so adding a job cannot leave a screen saying "Summary". */
@get:StringRes
val AiJob.titleRes: Int
    get() = when (this) {
        AiJob.TRANSLATE -> R.string.translate_title
        AiJob.SUMMARY -> R.string.summary_title
        AiJob.EXTRACT_TEXT -> R.string.extract_title
    }

@get:StringRes
val AiJob.actionRes: Int
    get() = when (this) {
        AiJob.TRANSLATE -> R.string.action_translate
        AiJob.SUMMARY -> R.string.action_summarize
        AiJob.EXTRACT_TEXT -> R.string.action_extract_text
    }

@get:StringRes
val AiJob.quitRes: Int
    get() = when (this) {
        AiJob.TRANSLATE -> R.string.quit_translating
        AiJob.SUMMARY -> R.string.quit_summarizing
        AiJob.EXTRACT_TEXT -> R.string.quit_extracting
    }

/** Used in the name of a saved result PDF: "<file> - translation.pdf". */
val AiJob.fileSuffix: String
    get() = when (this) {
        AiJob.TRANSLATE -> "translation"
        AiJob.SUMMARY -> "summary"
        AiJob.EXTRACT_TEXT -> "text"
    }
