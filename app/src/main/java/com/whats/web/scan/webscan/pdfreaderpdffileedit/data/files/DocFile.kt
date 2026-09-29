package com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files

import android.net.Uri

/** FR-011. The four document families the library shows; anything else is not listed. */
enum class DocType(val extensions: Set<String>) {
    PDF(setOf("pdf")),
    WORD(setOf("doc", "docx")),
    EXCEL(setOf("xls", "xlsx", "csv")),
    PPT(setOf("ppt", "pptx")),
    ;

    companion object {
        private val byExtension = entries.flatMap { t -> t.extensions.map { it to t } }.toMap()

        fun ofExtension(ext: String): DocType? = byExtension[ext.lowercase()]

        val allExtensions: Set<String> = byExtension.keys
    }
}

/** Legacy binary Office formats have no in-app renderer (FR-036). */
val DocFile.isLegacyOffice: Boolean get() = ext in setOf("doc", "xls", "ppt")

data class DocFile(
    /** Content URI string — stable enough to key favourites and recents on. */
    val key: String,
    val uri: Uri,
    val name: String,
    val ext: String,
    val type: DocType,
    val size: Long,
    /** Epoch milliseconds. */
    val modified: Long,
    val isSample: Boolean = false,
)

val DocFile.mimeType: String
    get() = when (ext.lowercase()) {
        "pdf" -> "application/pdf"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "csv" -> "text/csv"
        "ppt" -> "application/vnd.ms-powerpoint"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        else -> "application/octet-stream"
    }
