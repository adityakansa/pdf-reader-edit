package com.whats.web.scan.webscan.pdfreaderpdffileedit.office

/**
 * The editor's lightweight formatting, stored as plain text so the text field stays simple and every
 * keyboard works: `# ` heading, `## ` sub-heading, `- ` bullet, `1. ` numbered item, `**bold**`,
 * `_italic_`. The toolbar inserts these marks; [parse] turns them into paragraphs for Word/PDF output.
 */
object SimpleMarkup {

    enum class Kind { NORMAL, HEADING1, HEADING2, BULLET, NUMBERED }

    data class Run(val text: String, val bold: Boolean = false, val italic: Boolean = false)

    data class Paragraph(val kind: Kind, val runs: List<Run>) {
        val plain: String get() = runs.joinToString("") { it.text }
    }

    fun parse(text: String): List<Paragraph> = text.replace("\r\n", "\n").split('\n').map { line ->
        when {
            line.startsWith("## ") -> Paragraph(Kind.HEADING2, runs(line.removePrefix("## ")))
            line.startsWith("# ") -> Paragraph(Kind.HEADING1, runs(line.removePrefix("# ")))
            line.startsWith("- ") || line.startsWith("• ") -> Paragraph(Kind.BULLET, runs(line.substring(2)))
            NUMBERED.containsMatchIn(line) -> Paragraph(Kind.NUMBERED, runs(line.replaceFirst(NUMBERED, "")))
            else -> Paragraph(Kind.NORMAL, runs(line))
        }
    }

    /** `**bold**` and `_italic_` spans; unmatched marks stay as typed. */
    fun runs(line: String): List<Run> {
        val out = mutableListOf<Run>()
        var bold = false
        var italic = false
        val current = StringBuilder()
        var i = 0
        fun flush() {
            if (current.isNotEmpty()) out.add(Run(current.toString(), bold, italic))
            current.setLength(0)
        }
        while (i < line.length) {
            when {
                line.startsWith("**", i) && (bold || line.indexOf("**", i + 2) > i + 2) -> {
                    flush(); bold = !bold; i += 2
                }
                line[i] == '_' && (italic || line.indexOf('_', i + 1) > i + 1) &&
                    (i == 0 || !line[i - 1].isLetterOrDigit() || italic) -> {
                    flush(); italic = !italic; i++
                }
                else -> { current.append(line[i]); i++ }
            }
        }
        flush()
        return out
    }

    /** The text with the marks removed, for .txt and search. */
    fun plainText(text: String): String = parse(text).mapIndexed { index, p ->
        when (p.kind) {
            Kind.BULLET -> "• " + p.plain
            Kind.NUMBERED -> "${numberOf(text, index)}. " + p.plain
            else -> p.plain
        }
    }.joinToString("\n")

    /** The number a numbered paragraph shows: its position in the run of numbered lines it belongs to. */
    private fun numberOf(text: String, index: Int): Int {
        val paragraphs = parse(text)
        var n = 0
        var i = index
        while (i >= 0 && paragraphs[i].kind == Kind.NUMBERED) { n++; i-- }
        return n
    }

    private val NUMBERED = Regex("^\\d+[.)] ")
}
