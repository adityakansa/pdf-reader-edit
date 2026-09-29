package com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FolderNamesTest {

    @Test
    fun `paths from the scan`() {
        assertEquals("/storage/emulated/0/Download", FolderNames.parentOf("/storage/emulated/0/Download/a.pdf"))
        assertEquals("Download", FolderNames.displayName("/storage/emulated/0/Download"))
        assertEquals("Internal storage › Download", FolderNames.displayPath("/storage/emulated/0/Download"))
    }

    @Test
    fun `saf document ids map onto the same folders`() {
        assertEquals("/storage/emulated/0/Download/Invoices", FolderNames.parentOf("primary:Download/Invoices/a.pdf"))
    }

    @Test
    fun `root, sd card and keys without a folder`() {
        assertEquals("Internal storage", FolderNames.displayName("/storage/emulated/0"))
        assertEquals("SD card › Docs", FolderNames.displayPath("/storage/1A2B-3C4D/Docs"))
        assertNull(FolderNames.parentOf("sample:sample.pdf"))
        assertNull(FolderNames.parentOf("content://com.android.providers.downloads.documents/document/12"))
    }
}
