package com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files

import org.junit.Assert.assertEquals
import org.junit.Test

class FileNamesTest {

    @Test
    fun `keeps an ordinary name`() {
        assertEquals("Lease 2026", FileNames.sanitize("Lease 2026", "Scan"))
    }

    @Test
    fun `replaces characters file systems reject`() {
        assertEquals("a b c d", FileNames.sanitize("a/b:c?d", "Scan"))
    }

    @Test
    fun `drops a typed extension and hidden-file dots`() {
        assertEquals("Invoice", FileNames.sanitize("  ..Invoice.pdf ", "Scan"))
    }

    @Test
    fun `blank falls back to the default`() {
        assertEquals("Scan 1", FileNames.sanitize("  ***  ", "Scan 1"))
    }

    @Test
    fun `long names are capped`() {
        assertEquals(FileNames.MAX_LENGTH, FileNames.sanitize("x".repeat(500), "Scan").length)
    }
}
