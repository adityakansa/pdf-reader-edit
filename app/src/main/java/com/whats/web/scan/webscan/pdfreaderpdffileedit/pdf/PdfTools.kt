package com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf

import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * The everyday PDF tools CamScanner, Acrobat and Stirling-PDF are known for, on PdfBox, which the app
 * already ships. Every tool reads its input and writes a **new** file through the caller's stream; no
 * original is ever rewritten. Page numbers are zero-based and must already be validated by the caller
 * ([pagesToKeep] and friends clamp and de-duplicate them anyway).
 */
object PdfTools {

    /** Concatenates [inputs] in order into [target]. Streams are opened lazily, one at a time. */
    fun merge(inputs: List<() -> InputStream>, target: OutputStream, scratch: File) {
        require(inputs.size >= 2) { "Merging needs at least two PDFs" }
        val merger = PDFMergerUtility()
        val opened = inputs.map { it() }
        try {
            opened.forEach { merger.addSource(it) }
            merger.destinationStream = target
            merger.mergeDocuments(memory(scratch))
        } finally {
            opened.forEach { runCatching { it.close() } }
        }
    }

    /** A new PDF holding only [pages], in the order given ("Extract pages" / split). */
    fun extract(input: InputStream, pages: List<Int>, target: OutputStream, scratch: File) {
        load(input, null, scratch).use { source ->
            val wanted = pagesToKeep(pages, source.numberOfPages)
            require(wanted.isNotEmpty()) { "No pages selected" }
            PDDocument(memory(scratch)).use { output ->
                wanted.forEach { index -> output.importPage(source.getPage(index)) }
                // importPage shares objects with the source, so the output is saved before either closes.
                output.save(target)
            }
        }
    }

    /** The same PDF with [pages] removed. Refuses to remove every page. */
    fun delete(input: InputStream, pages: List<Int>, target: OutputStream, scratch: File) {
        load(input, null, scratch).use { doc ->
            val doomed = pagesToKeep(pages, doc.numberOfPages)
            require(doomed.size < doc.numberOfPages) { "A PDF needs at least one page" }
            doomed.sortedDescending().forEach { doc.removePage(it) }
            PdfSecurity.prepareForSave(doc, null)
            doc.save(target)
        }
    }

    /** The same PDF with [pages] turned by [degrees] (a multiple of 90; negative turns left). */
    fun rotate(input: InputStream, pages: List<Int>, degrees: Int, target: OutputStream, scratch: File) {
        require(degrees % QUARTER == 0) { "Pages turn in steps of 90°" }
        load(input, null, scratch).use { doc ->
            pagesToKeep(pages, doc.numberOfPages).forEach { index ->
                val page = doc.getPage(index)
                page.rotation = normaliseRotation(page.rotation + degrees)
            }
            PdfSecurity.prepareForSave(doc, null)
            doc.save(target)
        }
    }

    /**
     * Encrypts with AES-128 and [password] as both the open and the owner password. Printing and copying
     * stay allowed: the point is keeping the file closed to others, not restricting its owner.
     */
    fun protect(input: InputStream, password: String, target: OutputStream, scratch: File) {
        require(password.isNotEmpty()) { "Empty password" }
        load(input, null, scratch).use { doc ->
            val policy = StandardProtectionPolicy(password, password, AccessPermission())
            policy.encryptionKeyLength = AES_KEY_BITS
            doc.protect(policy)
            doc.save(target)
        }
    }

    /** Opens with [password] and writes an unencrypted copy. Throws [WrongPdfPasswordException] on a wrong one. */
    fun unlock(input: InputStream, password: String, target: OutputStream, scratch: File) {
        val doc = try {
            load(input, password, scratch)
        } catch (e: InvalidPasswordException) {
            throw WrongPdfPasswordException(e)
        }
        doc.use {
            it.isAllSecurityToBeRemoved = true
            it.save(target)
        }
    }

    /** Valid, distinct page indexes in the order first given. */
    fun pagesToKeep(pages: List<Int>, pageCount: Int): List<Int> =
        pages.filter { it in 0 until pageCount }.distinct()

    /** Any multiple of 90, positive or negative, as one of 0, 90, 180, 270. */
    fun normaliseRotation(degrees: Int): Int = ((degrees % FULL_TURN) + FULL_TURN) % FULL_TURN

    private fun load(input: InputStream, password: String?, scratch: File): PDDocument =
        if (password == null) PDDocument.load(input, memory(scratch))
        else PDDocument.load(input, password, memory(scratch))

    private fun memory(scratch: File): MemoryUsageSetting {
        scratch.mkdirs()
        return MemoryUsageSetting.setupMixed(MAIN_MEMORY_BYTES).setTempDir(scratch)
    }

    private const val MAIN_MEMORY_BYTES = 16L * 1024 * 1024
    private const val AES_KEY_BITS = 128
    private const val QUARTER = 90
    private const val FULL_TURN = 360
}
