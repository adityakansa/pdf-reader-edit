package check
import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.*
import java.io.File
fun main(args: Array<String>) {
    val samples = File(args[0]); val outDir = File(args[1]).apply { mkdirs() }
    for (name in listOf("sample.docx", "sample.xlsx", "sample.pptx")) {
        val parts = File(samples, name).inputStream().use { OoxmlZip.read(it) }
        val media = OoxmlZip.extractMedia(parts, File(outDir, "media-$name").apply { mkdirs() })
        val html = when (name.substringAfterLast('.')) {
            "docx" -> DocxToHtml.convert(parts, media)
            "xlsx" -> XlsxToHtml.convert(parts, "Showing the first 5,000 rows")
            else -> PptxToHtml.convert(parts, media)
        }
        File(outDir, "$name.html").writeText(html)
        println("$name -> ${html.length} chars")
    }
}
