package com.whats.web.scan.webscan.pdfreaderpdffileedit.office

import org.junit.Assert.assertEquals
import org.junit.Test

/** FR-033/035: a picture is found only if its relationship target resolves to the right part. */
class OoxmlPathTest {

    @Test
    fun `parent references climb out of the owning folder`() {
        // How PowerPoint stores every slide picture; stripping "../" used to lose the "ppt/".
        assertEquals("ppt/media/image1.png", OoxmlZip.normalise("../media/image1.png", "ppt/slides/"))
    }

    @Test
    fun `plain targets are relative to the owning folder`() {
        assertEquals("word/media/image1.png", OoxmlZip.normalise("media/image1.png", "word/"))
        assertEquals("xl/worksheets/sheet1.xml", OoxmlZip.normalise("worksheets/sheet1.xml", "xl/"))
    }

    @Test
    fun `absolute targets start at the package root`() {
        assertEquals("xl/worksheets/sheet2.xml", OoxmlZip.normalise("/xl/worksheets/sheet2.xml", "xl/"))
    }

    @Test
    fun `dot segments are ignored and cannot climb above the root`() {
        assertEquals("ppt/media/a.png", OoxmlZip.normalise("./../media/./a.png", "ppt/slides/"))
        assertEquals("a.png", OoxmlZip.normalise("../../../a.png", "ppt/slides/"))
    }
}
