package com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf

import android.graphics.Bitmap
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Step 12c, Compress PDF. Photos and scans are what make PDFs big, so each large picture is shrunk to a
 * sensible size and saved again as JPEG at the chosen quality; text, vector drawings and fonts are left
 * alone. A picture is only swapped when the new one is actually smaller, and pictures with transparency,
 * masks or one-bit scans (already compact) are skipped so nothing changes how the page looks.
 */
object PdfCompressor {

    enum class Level(val maxSide: Int, val quality: Float) {
        LOW(2400, 0.85f),
        MEDIUM(1600, 0.7f),
        HIGH(1100, 0.5f),
    }

    /** How many pictures were made smaller. */
    fun compress(input: InputStream, level: Level, target: OutputStream, scratch: File): Int {
        scratch.mkdirs()
        val memory = MemoryUsageSetting.setupMixed(MAIN_MEMORY_BYTES).setTempDir(scratch)
        PDDocument.load(input, memory).use { doc ->
            // A picture used on many pages is shrunk once and the one copy shared again.
            val replaced = HashMap<COSStream, PDImageXObject?>()
            val visitedForms = HashSet<COSStream>()

            fun shrink(image: PDImageXObject): PDImageXObject? {
                val stream = image.cosObject
                if (stream.length < MIN_BYTES || image.isStencil || image.bitsPerComponent == 1) return null
                if (stream.containsKey(COSName.SMASK) || stream.containsKey(COSName.MASK)) return null
                val bitmap = runCatching { image.image }.getOrNull() ?: return null
                val longest = maxOf(bitmap.width, bitmap.height)
                val scaled = if (longest > level.maxSide) {
                    val f = level.maxSide.toFloat() / longest
                    Bitmap.createScaledBitmap(bitmap, (bitmap.width * f).toInt().coerceAtLeast(1), (bitmap.height * f).toInt().coerceAtLeast(1), true)
                } else {
                    bitmap
                }
                val jpeg = runCatching { JPEGFactory.createFromImage(doc, scaled, level.quality) }.getOrNull()
                if (scaled !== bitmap) scaled.recycle()
                bitmap.recycle()
                return jpeg?.takeIf { it.cosObject.length < stream.length }
            }

            fun visit(resources: PDResources?) {
                resources ?: return
                resources.xObjectNames.toList().forEach { name ->
                    when (val x = runCatching { resources.getXObject(name) }.getOrNull()) {
                        is PDImageXObject -> {
                            val smaller = replaced.getOrPut(x.cosObject) { runCatching { shrink(x) }.getOrNull() }
                            if (smaller != null) resources.put(name, smaller)
                        }
                        is PDFormXObject -> if (visitedForms.add(x.cosObject)) visit(x.resources)
                        else -> Unit
                    }
                }
            }

            doc.pages.forEach { page -> visit(page.resources) }
            PdfSecurity.prepareForSave(doc, null)
            doc.save(target)
            return replaced.values.count { it != null }
        }
    }

    private const val MIN_BYTES = 40_000
    private const val MAIN_MEMORY_BYTES = 16L * 1024 * 1024
}
