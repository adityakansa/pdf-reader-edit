package com.whats.web.scan.webscan.pdfreaderpdffileedit.office

import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.EditorActions.LineStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorActionsTest {

    @Test
    fun `heading on the caret line`() {
        val e = EditorActions.toggleLine("one\ntwo\nthree", 5, 5, LineStyle.HEADING1)
        assertEquals("one\n# two\nthree", e.text)
    }

    @Test
    fun `toggling again removes it`() {
        val e = EditorActions.toggleLine("one\n# two\nthree", 6, 6, LineStyle.HEADING1)
        assertEquals("one\ntwo\nthree", e.text)
    }

    @Test
    fun `numbered list over several lines, replacing bullets`() {
        val e = EditorActions.toggleLine("- a\nb\nc", 0, 6, LineStyle.NUMBERED)
        assertEquals("1. a\n2. b\n3. c", e.text)
    }

    @Test
    fun `heading one does not treat heading two as the same`() {
        assertEquals("# x", EditorActions.toggleLine("## x", 0, 0, LineStyle.HEADING1).text)
    }

    @Test
    fun `wrap and unwrap bold`() {
        val bold = EditorActions.wrap("make this bold", 5, 9, "**")
        assertEquals("make **this** bold", bold.text)
        assertEquals(7, bold.start)
        val plain = EditorActions.wrap(bold.text, bold.start, bold.end, "**")
        assertEquals("make this bold", plain.text)
    }

    @Test
    fun `empty selection inserts a pair with the caret inside`() {
        val e = EditorActions.wrap("ab", 1, 1, "_")
        assertEquals("a__b", e.text)
        assertEquals(2, e.start)
    }

    @Test
    fun `word count`() {
        assertEquals(3, EditorActions.wordCount("# Hello big\n- world  "))
    }
}
