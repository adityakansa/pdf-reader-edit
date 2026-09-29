package com.whats.web.scan.webscan.pdfreaderpdffileedit.office

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextToHtmlTest {

    @Test
    fun `quoted fields keep commas, quotes and line breaks`() {
        val rows = TextToHtml.parseCsv("name,note\n\"Smith, J\",\"said \"\"hi\"\"\nthen left\"\nx,y")
        assertEquals(listOf("name", "note"), rows[0])
        assertEquals(listOf("Smith, J", "said \"hi\"\nthen left"), rows[1])
        assertEquals(listOf("x", "y"), rows[2])
    }

    @Test
    fun `windows line endings and a missing final newline`() {
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), TextToHtml.parseCsv("a,b\r\n1,2"))
    }

    @Test
    fun `semicolon files from European Excel are detected`() {
        assertEquals(';', TextToHtml.delimiterOf("Item;Price;Qty\nPen;1,50;3"))
        assertEquals(',', TextToHtml.delimiterOf("single column"))
        assertEquals('\t', TextToHtml.delimiterOf("a\tb\tc"))
    }

    @Test
    fun `utf8 with bom, utf16 and ansi all decode`() {
        val utf8Bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "café".toByteArray(Charsets.UTF_8)
        assertEquals("café", TextToHtml.decode(utf8Bom))
        val utf16 = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "héllo".toByteArray(Charsets.UTF_16LE)
        assertEquals("héllo", TextToHtml.decode(utf16))
        val ansi = "naïve £5".toByteArray(charset("windows-1252"))
        assertEquals("naïve £5", TextToHtml.decode(ansi))
    }

    @Test
    fun `csv becomes a grid with column letters and row numbers`() {
        val html = TextToHtml.csv("a,b\n1,2", "cap")
        assertTrue(html.contains("<th>A</th><th>B</th>"))
        assertTrue(html.contains("<th class=\"rownum\">2</th><td class=\"num\">1</td>"))
    }

    @Test
    fun `plain text is escaped`() {
        assertTrue(TextToHtml.plain("<b>not bold</b>", "cap").contains("&lt;b&gt;not bold&lt;/b&gt;"))
    }

    @Test
    fun `column names follow Excel`() {
        assertEquals("A", XlsxToHtml.columnName(0))
        assertEquals("Z", XlsxToHtml.columnName(25))
        assertEquals("AA", XlsxToHtml.columnName(26))
        assertEquals("ZZ", XlsxToHtml.columnName(701))
        assertEquals("AAA", XlsxToHtml.columnName(702))
    }
}
