package com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files

/** Turning what someone typed into a safe PDF base name. Pure Kotlin, so it is unit-tested on the JVM. */
object FileNames {
    private val ILLEGAL = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")
    private val SPACES = Regex("\\s+")
    const val MAX_LENGTH = 120

    /**
     * Characters Android or other file systems reject become spaces, runs of spaces collapse, a typed ".pdf"
     * is dropped (the writer adds it), and leading dots are removed so the file is never hidden.
     * Blank input falls back to [fallback].
     */
    fun sanitize(typed: String, fallback: String): String {
        val cleaned = typed
            .replace(ILLEGAL, " ")
            .replace(SPACES, " ")
            .trim()
            .removeSuffix(".pdf")
            .removeSuffix(".PDF")
            .trimStart('.')
            .trim()
            .take(MAX_LENGTH)
            .trim()
        return cleaned.ifEmpty { fallback }
    }
}
