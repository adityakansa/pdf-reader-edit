package com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files

/**
 * Folders for the "Folders" library view, worked out from file keys (pure Kotlin, JVM-tested).
 * Keys are absolute paths for files found by the scan (`/storage/emulated/0/Download/a.pdf`) or SAF
 * document ids for granted folders (`primary:Download/Invoices/a.pdf`).
 */
object FolderNames {
    private const val PRIMARY_ROOT = "/storage/emulated/0"

    /** The folder a key sits in, as a stable id, or null when it has none (samples, bare content URIs). */
    fun parentOf(key: String): String? {
        val path = when {
            key.startsWith("/") -> key
            key.startsWith("sample:") || key.startsWith("content:") || key.startsWith("file:") -> return null
            key.contains(':') -> key.substringAfter(':').let { "$PRIMARY_ROOT/$it" }
            else -> return null
        }
        val parent = path.substringBeforeLast('/', missingDelimiterValue = "")
        return parent.ifEmpty { null }
    }

    /** "Download", "WhatsApp Documents" … the last folder name; the storage root reads "Internal storage". */
    fun displayName(folder: String): String =
        if (folder == PRIMARY_ROOT) "Internal storage" else folder.substringAfterLast('/').ifEmpty { folder }

    /** "Internal storage › Android › media" — where the folder is, for the second line of a row. */
    fun displayPath(folder: String): String {
        val relative = when {
            folder == PRIMARY_ROOT -> return "Internal storage"
            folder.startsWith("$PRIMARY_ROOT/") -> "Internal storage/" + folder.removePrefix("$PRIMARY_ROOT/")
            folder.startsWith("/storage/") -> "SD card/" + folder.removePrefix("/storage/").substringAfter('/', "")
            else -> folder.trimStart('/')
        }
        return relative.split('/').filter { it.isNotEmpty() }.joinToString(" › ")
    }
}
