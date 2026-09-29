package com.whats.web.scan.webscan.pdfreaderpdffileedit.office

/**
 * What the editor's formatting buttons do to the text and selection, as pure functions so they are
 * unit-tested. Line styles ([SimpleMarkup]'s `# `, `## `, `- `, `1. `) apply to every line the
 * selection touches and toggle off when all of them already have it; bold/italic wrap the selection.
 */
object EditorActions {

    data class Edit(val text: String, val start: Int, val end: Int)

    enum class LineStyle(val prefix: String) { HEADING1("# "), HEADING2("## "), BULLET("- "), NUMBERED("1. ") }

    private val ANY_PREFIX = Regex("^(## |# |- |• |\\d+[.)] )")

    fun toggleLine(text: String, start: Int, end: Int, style: LineStyle): Edit {
        val s = start.coerceIn(0, text.length)
        val e = end.coerceIn(s, text.length)
        val lineStart = text.lastIndexOf('\n', (s - 1).coerceAtLeast(0)).let { if (s == 0 || it < 0) 0 else it + 1 }
            .let { if (s > 0 && text.getOrNull(s - 1) == '\n') s else it }
        val lineEnd = text.indexOf('\n', e).let { if (it < 0) text.length else it }
        val lines = text.substring(lineStart, lineEnd).split('\n')
        val allHave = lines.all { lineHas(it, style) }
        var n = 0
        val replaced = lines.joinToString("\n") { line ->
            val bare = line.replaceFirst(ANY_PREFIX, "")
            if (allHave) bare else {
                n++
                if (style == LineStyle.NUMBERED) "$n. $bare" else style.prefix + bare
            }
        }
        val newText = text.substring(0, lineStart) + replaced + text.substring(lineEnd)
        val delta = replaced.length - (lineEnd - lineStart)
        return Edit(newText, lineStart, (lineEnd + delta).coerceAtLeast(lineStart))
    }

    private fun lineHas(line: String, style: LineStyle): Boolean = when (style) {
        LineStyle.NUMBERED -> Regex("^\\d+[.)] ").containsMatchIn(line)
        LineStyle.HEADING1 -> line.startsWith("# ")
        else -> line.startsWith(style.prefix)
    }

    /** Wraps the selection in [mark] (`**` or `_`), or removes it when already wrapped. */
    fun wrap(text: String, start: Int, end: Int, mark: String): Edit {
        val s = start.coerceIn(0, text.length)
        val e = end.coerceIn(s, text.length)
        if (s == e) {
            val newText = text.substring(0, s) + mark + mark + text.substring(s)
            return Edit(newText, s + mark.length, s + mark.length)
        }
        val before = text.substring(0, s)
        val after = text.substring(e)
        if (before.endsWith(mark) && after.startsWith(mark)) {
            val newText = before.dropLast(mark.length) + text.substring(s, e) + after.drop(mark.length)
            return Edit(newText, s - mark.length, e - mark.length)
        }
        return Edit(before + mark + text.substring(s, e) + mark + after, s + mark.length, e + mark.length)
    }

    fun wordCount(text: String): Int = text.split(Regex("\\s+")).count { it.any(Char::isLetterOrDigit) }
}
