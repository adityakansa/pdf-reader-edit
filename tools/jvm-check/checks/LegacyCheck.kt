package check

import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.legacy.Cfb
import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.legacy.LegacyDoc
import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.legacy.LegacyPpt
import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.legacy.LegacyToHtml
import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.legacy.LegacyXls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Real Office 97–2003 files from Apache POI's test data (Apache-2.0). */
class LegacyCheck {
    private val dir = File(System.getProperty("fixtures"), "legacy")
    private val out = File("office").apply { mkdirs() }
    private fun cfb(name: String) = Cfb(File(dir, name).readBytes())

    @Test fun every_fixture_is_a_compound_file_with_its_streams() {
        assertTrue(cfb("SampleDoc.doc").names().contains("WordDocument"))
        assertTrue(cfb("SampleSS.xls").names().contains("Workbook"))
        assertTrue(cfb("SampleShow.ppt").names().contains("PowerPoint Document"))
    }

    @Test fun word_text_comes_out_readable() {
        listOf("SampleDoc.doc", "simple-table.doc").forEach { name ->
            val blocks = LegacyDoc.read(cfb(name))
            val text = blocks.joinToString("\n") { it.toString() }
            println("--- $name: ${blocks.size} blocks\n" + text.take(700))
            assertTrue(blocks.isNotEmpty())
            File(out, "$name.html").writeText(LegacyToHtml.convert("doc", File(dir, name).readBytes(), "cap"))
        }
        assertTrue(LegacyDoc.read(cfb("simple-table.doc")).any { it is LegacyDoc.Block.Table })
    }

    @Test fun excel_cells_and_sheets() {
        listOf("SampleSS.xls", "SimpleMultiCell.xls").forEach { name ->
            val sheets = LegacyXls.read(cfb(name))
            println("--- $name: " + sheets.joinToString { "${it.name}(${it.rows.size} rows) ${it.rows.take(4)}" })
            assertTrue(sheets.isNotEmpty())
            File(out, "$name.html").writeText(LegacyToHtml.convert("xls", File(dir, name).readBytes(), "cap"))
        }
    }

    @Test fun powerpoint_slides_have_text() {
        listOf("SampleShow.ppt", "basic_test_ppt_file.ppt").forEach { name ->
            val slides = LegacyPpt.read(cfb(name))
            println("--- $name: ${slides.size} slides " + slides.take(3))
            assertTrue(slides.isNotEmpty())
            assertTrue(slides.any { it.title != null || it.body.isNotEmpty() })
            File(out, "$name.html").writeText(LegacyToHtml.convert("ppt", File(dir, name).readBytes(), "cap"))
        }
    }

    @Test fun legacy_files_are_searchable() {
        val doc = com.whats.web.scan.webscan.pdfreaderpdffileedit.search.DocumentText.legacy("doc", File(dir, "SampleDoc.doc").readBytes())
        assertTrue(doc.contains("This is page two"))
        val xls = com.whats.web.scan.webscan.pdfreaderpdffileedit.search.DocumentText.legacy("xls", File(dir, "SampleSS.xls").readBytes())
        assertTrue(xls.contains("Start of 2nd sheet"))
        val ppt = com.whats.web.scan.webscan.pdfreaderpdffileedit.search.DocumentText.legacy("ppt", File(dir, "SampleShow.ppt").readBytes())
        assertTrue(ppt.contains("It has bullet points on it"))
    }

    @Test fun rk_numbers() {
        assertEquals(3.0, LegacyXls.rk((3 shl 2) or 2), 0.0)
        assertEquals(0.03, LegacyXls.rk((3 shl 2) or 3), 1e-12)
        assertEquals("92.4", LegacyXls.number(92.4))
    }

    @Test fun field_codes_are_dropped_and_results_kept() {
        assertEquals("See page 4.", LegacyDoc.cleanFields("See page \u0013 PAGEREF _Toc1 \\h \u00144\u0015."))
    }
}
