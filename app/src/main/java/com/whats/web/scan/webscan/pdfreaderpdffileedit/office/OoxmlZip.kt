package com.whats.web.scan.webscan.pdfreaderpdffileedit.office

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * FR-033 … FR-035. An OOXML file is a zip of XML parts. The whole archive is read into memory once —
 * these are documents a person opens on a phone, and a single pass beats reopening the zip per part —
 * and each viewer then walks the parts it needs with the platform pull parser.
 */
object OoxmlZip {

    /** Anything bigger than this is refused rather than risking an OOM on a 2 GB phone. */
    const val MAX_BYTES = 64L * 1024 * 1024

    class TooLargeException : java.io.IOException("Document is too large to open")

    fun read(input: InputStream): Map<String, ByteArray> {
        val parts = LinkedHashMap<String, ByteArray>()
        var total = 0L
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val bytes = zip.readBytes()
                total += bytes.size
                if (total > MAX_BYTES) throw TooLargeException()
                parts[entry.name] = bytes
            }
        }
        return parts
    }

    fun parser(bytes: ByteArray): XmlPullParser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        setInput(ByteArrayInputStream(bytes), "UTF-8")
    }

    /**
     * Relationship id → target part, read from a `.rels` part. Images are referenced by id from the
     * document body, so this is how a `<a:blip r:embed="rId7"/>` becomes `word/media/image1.png`.
     */
    fun relationships(parts: Map<String, ByteArray>, relsPath: String, base: String): Map<String, String> {
        val bytes = parts[relsPath] ?: return emptyMap()
        val map = mutableMapOf<String, String>()
        val parser = parser(bytes)
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "Relationship") {
                val id = parser.getAttributeValue(null, "Id") ?: continue
                val target = parser.getAttributeValue(null, "Target") ?: continue
                map[id] = normalise(target, base)
            }
        }
        return map
    }

    /**
     * Resolves a relationship target against the folder of the part that owns it: `../media/image1.png`
     * from `ppt/slides/` is `ppt/media/image1.png`. (Stripping the `../` instead lost the `ppt/` and every
     * PowerPoint picture with it.) A leading `/` means "from the package root".
     */
    internal fun normalise(target: String, base: String): String {
        if (target.startsWith("/")) return target.removePrefix("/")
        val segments = base.split('/').filter { it.isNotEmpty() }.toMutableList()
        target.split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.lastIndex)
                else -> segments += segment
            }
        }
        return segments.joinToString("/")
    }

    /**
     * Writes the images a document references into [directory] so the WebView can load them from a
     * `file://` base URL. Returns part name → file name.
     */
    fun extractMedia(parts: Map<String, ByteArray>, directory: File): Map<String, String> {
        directory.mkdirs()
        directory.listFiles()?.forEach { it.delete() }
        return parts.entries
            .filter { it.key.contains("/media/") }
            .associate { (name, bytes) ->
                val fileName = name.substringAfterLast('/')
                File(directory, fileName).writeBytes(bytes)
                name to fileName
            }
    }

    /** HTML-escapes text coming out of a document; the viewer builds a page as a string. */
    fun escape(text: String): String = buildString(text.length) {
        text.forEach { c ->
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                else -> append(c)
            }
        }
    }
}
