package com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf

import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.DocFile
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.OutputFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs a [PdfTools] operation on library files and writes the result, as a new PDF, to the output folder
 * (`Documents/PDF Reader/`). Reading goes through [PdfAccess] so content URIs, SAF grants and file URIs
 * all work the same. Errors come back as exceptions for the caller to turn into a message.
 */
@Singleton
class PdfToolRunner @Inject constructor(
    private val access: PdfAccess,
    private val outputFolder: OutputFolder,
) {
    enum class PageAction { EXTRACT, DELETE, ROTATE_LEFT, ROTATE_RIGHT }

    suspend fun merge(files: List<DocFile>, name: String): OutputFolder.Output = withContext(Dispatchers.IO) {
        outputFolder.write(name) { out ->
            PdfTools.merge(files.map { file -> { access.openStream(file.uri) } }, out, access.scratchDir)
        }
    }

    suspend fun pages(file: DocFile, pages: List<Int>, action: PageAction, name: String): OutputFolder.Output =
        withContext(Dispatchers.IO) {
            outputFolder.write(name) { out ->
                access.openStream(file.uri).use { input ->
                    when (action) {
                        PageAction.EXTRACT -> PdfTools.extract(input, pages, out, access.scratchDir)
                        PageAction.DELETE -> PdfTools.delete(input, pages, out, access.scratchDir)
                        PageAction.ROTATE_LEFT -> PdfTools.rotate(input, pages, -QUARTER_TURN, out, access.scratchDir)
                        PageAction.ROTATE_RIGHT -> PdfTools.rotate(input, pages, QUARTER_TURN, out, access.scratchDir)
                    }
                }
            }
        }

    suspend fun protect(file: DocFile, password: String): OutputFolder.Output = withContext(Dispatchers.IO) {
        outputFolder.write(OutputFolder.derivedName(file.name, "protected")) { out ->
            access.openStream(file.uri).use { PdfTools.protect(it, password, out, access.scratchDir) }
        }
    }

    /**
     * Throws [WrongPdfPasswordException] for a wrong password. [OutputFolder.write] deletes its pending
     * file when the writer throws, so a wrong password leaves nothing behind.
     */
    suspend fun unlock(file: DocFile, password: String): OutputFolder.Output = withContext(Dispatchers.IO) {
        outputFolder.write(OutputFolder.derivedName(file.name, "unlocked")) { out ->
            access.openStream(file.uri).use { PdfTools.unlock(it, password, out, access.scratchDir) }
        }
    }

    private companion object {
        const val QUARTER_TURN = 90
    }
}
