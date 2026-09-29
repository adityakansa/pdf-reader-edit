package com.whats.web.scan.webscan.pdfreaderpdffileedit.office

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** FR-034. Blank cells are missing from the XML; the column in the cell reference is what places a value. */
class XlsxColumnTest {

    @Test
    fun `single letters are zero based`() {
        assertEquals(0, XlsxToHtml.columnIndex("A1"))
        assertEquals(2, XlsxToHtml.columnIndex("C12"))
        assertEquals(25, XlsxToHtml.columnIndex("Z3"))
    }

    @Test
    fun `double letters continue after Z`() {
        assertEquals(26, XlsxToHtml.columnIndex("AA7"))
        assertEquals(27, XlsxToHtml.columnIndex("ab7"))
        assertEquals(701, XlsxToHtml.columnIndex("ZZ1"))
    }

    @Test
    fun `missing or malformed references give null`() {
        assertNull(XlsxToHtml.columnIndex(null))
        assertNull(XlsxToHtml.columnIndex("12"))
    }
}
