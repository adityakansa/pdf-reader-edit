package com.whats.web.scan.webscan.pdfreaderpdffileedit.pdf

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/** The password given for an encrypted PDF was wrong, or none was given. */
class WrongPdfPasswordException(cause: Throwable? = null) : IOException(cause)

/**
 * FR-030. Everything between a content URI and something the PDF code can work with: a descriptor for
 * the platform renderer, a stream for PdfBox, and a decrypted cache copy when the file needs a password.
 */
@Singleton
class PdfAccess @Inject constructor(@ApplicationContext private val context: Context) {

    /** App-private scratch for PdfBox spill files and decrypted copies. */
    val scratchDir: File get() = File(context.cacheDir, "pdf").apply { mkdirs() }

    sealed interface OpenResult {
        data class Success(val session: PdfRenderSession, val source: PdfSource) : OpenResult
        data object PasswordRequired : OpenResult
        data object WrongPassword : OpenResult
        data class Failed(val cause: Throwable) : OpenResult
    }

    /** Where the PDF bytes actually are: the original, or the decrypted copy we made of it. */
    data class PdfSource(val uri: Uri, val decryptedCopy: File?)

    suspend fun open(uri: Uri, password: String? = null): OpenResult = withContext(Dispatchers.IO) {
        if (password == null) {
            val direct = runCatching { PdfRenderSession.open(descriptor(uri)) }
            direct.getOrNull()?.let { return@withContext OpenResult.Success(it, PdfSource(uri, null)) }
            val error = direct.exceptionOrNull()
            // The platform renderer answers "encrypted" with a SecurityException and nothing else.
            if (error !is SecurityException) {
                return@withContext OpenResult.Failed(error ?: IOException("Cannot open $uri"))
            }
            return@withContext OpenResult.PasswordRequired
        }

        val copy = File(scratchDir, "decrypted-${uri.hashCode()}.pdf")
        try {
            openStream(uri).use { input -> decryptTo(input, password, copy) }
        } catch (e: WrongPdfPasswordException) {
            copy.delete()
            return@withContext OpenResult.WrongPassword
        } catch (e: IOException) {
            copy.delete()
            return@withContext OpenResult.Failed(e)
        }
        runCatching {
            PdfRenderSession.open(
                ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY),
            )
        }.fold(
            onSuccess = { OpenResult.Success(it, PdfSource(Uri.fromFile(copy), copy)) },
            onFailure = { OpenResult.Failed(it) },
        )
    }

    fun openStream(uri: Uri): InputStream =
        context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot read $uri")

    private fun descriptor(uri: Uri): ParcelFileDescriptor =
        context.contentResolver.openFileDescriptor(uri, "r") ?: throw IOException("Cannot open $uri")

    /** PdfBox needs a real [File] for a few operations; content URIs get copied into the cache. */
    suspend fun localCopy(uri: Uri): File = withContext(Dispatchers.IO) {
        uri.path?.let { path -> if (uri.scheme == "file") return@withContext File(path) }
        val copy = File(scratchDir, "work-${uri.hashCode()}.pdf")
        if (!copy.exists() || copy.length() == 0L) {
            openStream(uri).use { input -> copy.outputStream().use(input::copyTo) }
        }
        copy
    }

    fun loadDocument(input: InputStream, password: String? = null): PDDocument {
        val memory = MemoryUsageSetting.setupMixed(MAIN_MEMORY_BYTES).setTempDir(scratchDir)
        return if (password == null) PDDocument.load(input, memory)
        else PDDocument.load(input, password, memory)
    }

    /** Writes a plaintext copy of an encrypted PDF into app-private cache (FR-030). */
    private fun decryptTo(input: InputStream, password: String, target: File) {
        val document = try {
            loadDocument(input, password)
        } catch (e: InvalidPasswordException) {
            throw WrongPdfPasswordException(e)
        }
        document.use {
            it.isAllSecurityToBeRemoved = true
            target.parentFile?.mkdirs()
            it.save(target)
        }
    }

    /** The page count without rendering anything — used by File info (FR-018). */
    suspend fun pageCount(uri: Uri): Int? = withContext(Dispatchers.IO) {
        runCatching {
            descriptor(uri).use { fd -> android.graphics.pdf.PdfRenderer(fd).use { it.pageCount } }
        }.getOrNull()
    }

    companion object {
        const val MAIN_MEMORY_BYTES = 16L * 1024 * 1024
    }
}
