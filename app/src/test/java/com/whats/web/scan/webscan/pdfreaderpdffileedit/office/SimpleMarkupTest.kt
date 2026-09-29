package com.whats.web.scan.webscan.pdfreaderpdffileedit.office

import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.SimpleMarkup.Kind
import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.SimpleMarkup.Run
import org.junit.Assert.assertEquals
import org.junit.Test

class SimpleMarkupTest {

    @Test
    fun `line kinds`() {
        val p = SimpleMarkup.parse("# Title\n## Sub\n- item\n1. first\n2) second\nplain")
        assertEquals(
            listOf(Kind.HEADING1, Kind.HEADING2, Kind.BULLET, Kind.NUMBERED, Kind.NUMBERED, Kind.NORMAL),
            p.map { it.kind },
        )
        assertEquals("first", p[3].plain)
    }

    @Test
    fun `bold and italic spans`() {
        assertEquals(
            listOf(Run("a "), Run("big", bold = true), Run(" and "), Run("slanted", italic = true), Run(" word")),
            SimpleMarkup.runs("a **big** and _slanted_ word"),
        )
    }

    @Test
    fun `unmatched marks and snake_case stay as typed`() {
        assertEquals(listOf(Run("2 ** 3")), SimpleMarkup.runs("2 ** 3"))
        assertEquals(listOf(Run("file_name_here")), SimpleMarkup.runs("file_name_here"))
    }

    @Test
    fun `plain text keeps numbering and bullets`() {
        assertEquals("Shop\n• milk\n1. one\n2. two", SimpleMarkup.plainText("# Shop\n- milk\n1. one\n1. two"))
    }
}
