package com.whats.web.scan.webscan.pdfreaderpdffileedit.ai

/** Pure text work on the model's output, kept free of Android types so it is unit-tested on the JVM. */
object SummaryParagraphs {
    private val PARAGRAPH_BREAK = Regex("\\n\\s*\\n")
    private val SENTENCE_END = Regex("[.!?](\\s|$)")
    private const val MAX_PARAGRAPHS = 3

    /**
     * Exactly two or three paragraphs, whatever the model produced: drop exact repeats, keep the first
     * three, and if only one survived, split it at the sentence boundary nearest its middle.
     */
    fun clamp(raw: String): String {
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

    /** The running text with any trailing, not-yet-complete UTF-8 sequence left out. */
    fun decodeComplete(bytes: ByteArray): String = String(bytes, 0, completeUtf8Length(bytes), Charsets.UTF_8)

    /** How many leading bytes form whole UTF-8 characters (a token can stop mid-character). */
    fun completeUtf8Length(bytes: ByteArray): Int {
        val size = bytes.size
        // Walk back over at most three continuation bytes to the lead byte of the last character.
        var index = size - 1
        var continuation = 0
        while (index >= 0 && continuation < 3 && (bytes[index].toInt() and 0xC0) == 0x80) {
            index--
            continuation++
        }
        if (index < 0) return size
        val lead = bytes[index].toInt() and 0xFF
        val needed = when {
            lead < 0x80 -> 1
            lead >= 0xF0 -> 4
            lead >= 0xE0 -> 3
            lead >= 0xC0 -> 2
            else -> 1
        }
        return if (continuation + 1 >= needed) size else index
    }
}
