package com.whats.web.scan.webscan.pdfreaderpdffileedit.office

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocxFormatTest {

    @Test
    fun `list numbers in every Word format`() {
        assertEquals("4", DocxToHtml.formatNumber(4, "decimal"))
        assertEquals("d", DocxToHtml.formatNumber(4, "lowerLetter"))
        assertEquals("AA", DocxToHtml.formatNumber(27, "upperLetter"))
        assertEquals("iv", DocxToHtml.formatNumber(4, "lowerRoman"))
        assertEquals("XIX", DocxToHtml.formatNumber(19, "upperRoman"))
        assertEquals("07", DocxToHtml.formatNumber(7, "decimalZero"))
    }

    @Test
    fun `roman numerals`() {
        assertEquals("MCMXCIV", DocxToHtml.roman(1994))
        assertEquals("0", DocxToHtml.roman(0))
    }

    @Test
    fun `colours and alignment`() {
        assertEquals("1E9E5A", DocxToHtml.hexColor("1E9E5A"))
        assertNull(DocxToHtml.hexColor("auto"))
        assertEquals("justify", DocxToHtml.cssAlign("both"))
        assertEquals("right", DocxToHtml.cssAlign("end"))
        assertNull(DocxToHtml.cssAlign(null))
    }

    @Test
    fun `units`() {
        assertEquals(48, DocxToHtml.twipsToPx(720))
        assertEquals("14.7", DocxToHtml.ptToPx(11f))
    }

    @Test
    fun `run formatting overlays and css`() {
        val style = DocxToHtml.RunFormat(bold = true, halfPoints = 28, colorHex = "FF0000")
        val run = DocxToHtml.RunFormat(bold = false, strike = true)
        val css = style.overlay(run).css()
        assertTrue(css.contains("text-decoration:line-through"))
        assertTrue(css.contains("color:#FF0000"))
        assertTrue(css.contains("font-size:18.7px"))
        assertTrue(!css.contains("font-weight:bold"))
    }
}
