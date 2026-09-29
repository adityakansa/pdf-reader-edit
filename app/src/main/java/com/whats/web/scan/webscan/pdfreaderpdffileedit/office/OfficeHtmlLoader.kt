package com.whats.web.scan.webscan.pdfreaderpdffileedit.office

import android.content.Context
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.DocFile
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.DocType
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.isLegacyOffice
import com.whats.web.scan.webscan.pdfreaderpdffileedit.office.legacy.LegacyToHtml
import java.io.File

/**
 * Any document the viewer can show, as HTML: OOXML through the Docx/Xlsx/Pptx converters, Office 97–2003
 * through the legacy readers, CSV and TXT as text. Shared by the Office viewer and the "… to PDF" tools
 * so both see exactly the same document. Blocking; call from a background thread.
 */
object OfficeHtmlLoader {

    /** [mediaDir] receives the pictures the HTML refers to; use it as the page's base URL. */
    fun load(context: Context, file: DocFile, mediaDir: File, rowCapNotice: String, textCapNotice: String): String? =
        runCatching {
            val resolver = context.contentResolver
            if (file.isLegacyOffice) {
                val bytes = resolver.openInputStream(file.uri)?.use { it.readBytes() } ?: return null
                return LegacyToHtml.convert(file.ext, bytes, rowCapNotice)
            }
            when (file.ext.lowercase()) {
                "csv", "txt" -> {
                    val bytes = resolver.openInputStream(file.uri)?.use { it.readBytes() } ?: return null
                    val text = TextToHtml.decode(bytes)
                    return if (file.ext.equals("csv", ignoreCase = true)) {
                        TextToHtml.csv(text, rowCapNotice)
                    } else {
                        TextToHtml.plain(text, textCapNotice)
                    }
                }
            }
            val parts = resolver.openInputStream(file.uri)?.use(OoxmlZip::read) ?: return null
            val media = OoxmlZip.extractMedia(parts, mediaDir)
            when (file.type) {
                DocType.WORD -> DocxToHtml.convert(parts, media)
                DocType.EXCEL -> XlsxToHtml.convert(parts, rowCapNotice)
                DocType.PPT -> PptxToHtml.convert(parts, media)
                DocType.PDF, DocType.TEXT -> null
            }
        }.getOrNull()
}
