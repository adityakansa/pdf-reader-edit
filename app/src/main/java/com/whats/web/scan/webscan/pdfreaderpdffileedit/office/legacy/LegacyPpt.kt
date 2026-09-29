package com.whats.web.scan.webscan.pdfreaderpdffileedit.office.legacy

import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.legacy.Cfb.Companion.le16
import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.legacy.Cfb.Companion.le32
import java.io.IOException

/**
 * PowerPoint 97–2003 (.ppt) slide text: title and body per slide, in slide order. Read from the
 * slide list (`SlideListWithText`); files that keep text inside each slide's drawing instead are read
 * from the slide containers, skipping masters and notes. Layout, pictures and styles are not read.
 */
object LegacyPpt {

    data class Slide(val title: String?, val body: List<String>)

    fun read(cfb: Cfb): List<Slide> {
        val doc = cfb.stream("PowerPoint Document") ?: throw IOException("No PowerPoint Document stream")
        val fromList = fromSlideList(doc)
        if (fromList.any { it.title != null || it.body.isNotEmpty() }) return fromList
        return fromSlideContainers(doc)
    }

    private class Header(val version: Int, val instance: Int, val type: Int, val length: Int)

    private fun header(d: ByteArray, p: Int): Header? {
        if (p + 8 > d.size) return null
        val verInst = le16(d, p)
        val length = le32(d, p + 4)
        if (length < 0 || p + 8 + length.toLong() > d.size) return null
        return Header(verInst and 0x0F, verInst ushr 4, le16(d, p + 2), length)
    }

    /** Walks every record under [start, end), depth-first, calling [visit] with header and data offset. */
    private fun walk(d: ByteArray, start: Int, end: Int, depth: Int = 0, visit: (Header, Int) -> Boolean) {
        var p = start
        while (p + 8 <= end) {
            val h = header(d, p) ?: return
            val descend = visit(h, p + 8)
            if (h.version == CONTAINER && descend && depth < MAX_DEPTH) walk(d, p + 8, p + 8 + h.length, depth + 1, visit)
            p += 8 + h.length
        }
    }

    private fun fromSlideList(d: ByteArray): List<Slide> {
        val slides = mutableListOf<MutableSlide>()
        walk(d, 0, d.size) { h, data ->
            when (h.type) {
                // Instance 0 is the slides; 1 is master text, 2 is notes.
                // "Fast save" appends a whole new document to the file on every save; the last list is current.
                SLIDE_LIST_WITH_TEXT -> h.instance == 0 && run {
                    slides.clear()
                    collectList(d, data, data + h.length, slides)
                    false
                }
                else -> true
            }
        }
        return slides.map { it.toSlide() }
    }

    private fun collectList(d: ByteArray, start: Int, end: Int, slides: MutableList<MutableSlide>) {
        var textType = BODY
        walk(d, start, end) { h, data ->
            when (h.type) {
                SLIDE_PERSIST_ATOM -> slides += MutableSlide()
                TEXT_HEADER_ATOM -> textType = le32(d, data)
                TEXT_CHARS_ATOM, TEXT_BYTES_ATOM -> slides.lastOrNull()?.add(textType, atomText(d, h, data))
            }
            true
        }
    }

    private fun fromSlideContainers(d: ByteArray): List<Slide> {
        val slides = mutableListOf<MutableSlide>()
        walk(d, 0, d.size) { h, data ->
            when (h.type) {
                MAIN_MASTER, NOTES, HANDOUT -> false
                SLIDE -> {
                    val slide = MutableSlide()
                    var textType = BODY
                    walk(d, data, data + h.length) { inner, innerData ->
                        when (inner.type) {
                            TEXT_HEADER_ATOM -> textType = le32(d, innerData)
                            TEXT_CHARS_ATOM, TEXT_BYTES_ATOM -> slide.add(textType, atomText(d, inner, innerData))
                        }
                        true
                    }
                    slides += slide
                    false
                }
                else -> true
            }
        }
        return slides.map { it.toSlide() }
    }

    private fun atomText(d: ByteArray, h: Header, data: Int): String =
        if (h.type == TEXT_CHARS_ATOM) String(d, data, h.length, Charsets.UTF_16LE)
        else String(d, data, h.length, Charsets.ISO_8859_1)

    private class MutableSlide {
        var title: String? = null
        val body = mutableListOf<String>()

        fun add(textType: Int, text: String) {
            val lines = text.split('\r', '\u000B').map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) return
            if ((textType == TITLE || textType == CENTER_TITLE) && title == null) title = lines.joinToString(" ")
            else body.addAll(lines)
        }

        fun toSlide() = Slide(title, body.toList())
    }

    private const val CONTAINER = 0x0F
    private const val MAX_DEPTH = 16
    private const val SLIDE_LIST_WITH_TEXT = 0x0FF0
    private const val SLIDE_PERSIST_ATOM = 0x03F3
    private const val TEXT_HEADER_ATOM = 0x0F9F
    private const val TEXT_CHARS_ATOM = 0x0FA0
    private const val TEXT_BYTES_ATOM = 0x0FA8
    private const val SLIDE = 0x03EE
    private const val MAIN_MASTER = 0x03F8
    private const val NOTES = 0x03F0
    private const val HANDOUT = 0x0FC9
    private const val TITLE = 0
    private const val BODY = 1
    private const val CENTER_TITLE = 6
}
