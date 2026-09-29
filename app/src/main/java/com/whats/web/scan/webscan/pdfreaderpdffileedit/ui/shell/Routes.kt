package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.shell

import kotlinx.serialization.Serializable

/** Type-safe Navigation Compose routes. Keys are the file keys from `data/files`. */
object Routes {
    @Serializable
    data object Home

    @Serializable
    data object Search

    @Serializable
    data object StorageAccess

    /** [annotate] opens straight into the Annotate tools (Home → Annotate). */
    @Serializable
    data class Reader(val key: String, val annotate: Boolean = false)

    /** Home → a card. [category] is a `ui/home/LibraryCategory` name; [merge] opens it picking PDFs to merge. */
    @Serializable
    data class FileList(val category: String, val merge: Boolean = false)

    /** Home → a tool that needs a file: pick one of the tool's input type first. [tool] is a `Tool` name. */
    @Serializable
    data class ToolPicker(val tool: String)

    @Serializable
    data object RecycleBin

    @Serializable
    data class OfficeReader(val key: String)

    @Serializable
    data object ImageToPdf

    @Serializable
    data object Scan

    @Serializable
    data object ScanReview

    @Serializable
    data class PlaceOnPdf(val key: String)

    @Serializable
    data class AiFilePicker(val mode: String)

    @Serializable
    data class SelectPage(val key: String, val mode: String)

    @Serializable
    data class AiRun(val key: String, val mode: String, val pages: String)

    /** The result text is handed over in `ai/AiResultHolder`, not in the route. */
    @Serializable
    data object AiResult

    @Serializable
    data class OrganizePages(val key: String)

    /** New document when [key] is null; otherwise the text file to edit. */
    @Serializable
    data class Editor(val key: String? = null)

    @Serializable
    data object Paywall

    @Serializable
    data object Notices
}

/** The AI routes carry the job as a string; `ai/AiJob` is the type everything else uses. */
typealias AiMode = com.whats.web.scan.webscan.pdfreaderpdffileedit.ai.AiJob

fun String.toAiJob(): AiMode = AiMode.valueOf(this)
