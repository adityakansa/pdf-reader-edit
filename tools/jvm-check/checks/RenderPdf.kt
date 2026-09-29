package check

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import java.io.File
import javax.imageio.ImageIO

/** Renders every page of each PDF given to `<pdf>-<n>.png` (72 dpi), to look at print output by eye. */
fun main(args: Array<String>) {
    args.forEach { path ->
        PDDocument.load(File(path)).use { doc ->
            val renderer = PDFRenderer(doc)
            for (i in 0 until doc.numberOfPages) {
                ImageIO.write(renderer.renderImageWithDPI(i, 60f), "png", File("${path.removeSuffix(".pdf")}-${i + 1}.png"))
            }
            println("$path: ${doc.numberOfPages} pages")
        }
    }
}
