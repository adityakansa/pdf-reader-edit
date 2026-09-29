package check

import com.whats.web.scan.webscan.pdfreaderpdffileedit.search.DocumentText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DocumentTextCheck {
    private val samples = File(System.getProperty("samples"))
    private val scratch = File("scratch").apply { mkdirs() }

    private fun text(name: String) = File(samples, name).inputStream().use {
        DocumentText.of(name.substringAfterLast('.'), it, scratch)
    }

    @Test fun pdf_has_one_entry_per_page_with_its_words() {
        val pages = text("sample.pdf")
        assertEquals(2, pages.size)
        assertTrue(pages[0].contains("Welcome to PDF Reader"))
        assertTrue(pages[1].contains("How it works"))
    }

    @Test fun office_files_and_text_are_searchable() {
        assertTrue(text("sample.docx").single().contains("Merged total row").not())
        assertTrue(text("sample.docx").single().contains("What you can do"))
        assertTrue(text("sample.xlsx").single().contains("Groceries"))
        assertTrue(text("sample.xlsx").single().contains("Scan receipts"))
        assertTrue(text("sample.pptx").single().contains("Every file type in one place"))
        assertTrue(text("sample.txt").single().contains("CSV files"))
    }

    @Test fun snippets_mark_the_match_and_trim_context() {
        val s = DocumentText.snippets("The quick brown fox jumps over the lazy dog", "BROWN")
        assertEquals(1, s.size)
        assertTrue(s[0].contains("${DocumentText.MARK_START}brown${DocumentText.MARK_END}"))
        assertEquals(2, DocumentText.count("a fox and a Fox", "fox"))
    }
}
