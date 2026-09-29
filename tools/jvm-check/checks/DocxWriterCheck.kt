package check

import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.DocxToHtml
import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.DocxWriter
import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.OoxmlZip
import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.SimpleMarkup
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DocxWriterCheck {
    @Test fun written_docx_round_trips_through_the_viewer() {
        val text = "# Meeting notes\nWe agreed on **three** things & <nothing> else:\n1. Budget\n2. Hiring\n- extra point\n1. restart\n## Next\n_Friday_ at 10"
        val file = File("office/written.docx").apply { parentFile.mkdirs() }
        file.outputStream().use { DocxWriter.write(SimpleMarkup.parse(text), "Meeting notes", it) }
        val parts = file.inputStream().use { OoxmlZip.read(it) }
        val html = DocxToHtml.convert(parts, emptyMap())
        File("office/written.docx.html").writeText(html)
        assertTrue(html.contains("<h1"))
        assertTrue(html.contains("Meeting notes"))
        assertTrue(html.contains("&amp; &lt;nothing&gt;"))
        assertTrue(html.contains(">1.</span>Budget") || html.contains("1.</span>"))
        assertTrue(html.contains(">2.</span>"))
        assertTrue(html.contains("font-weight:bold"))
    }
}
