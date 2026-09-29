package com.whats.web.scan.webscan.pdfreaderpdffileedit.sign

import android.content.Context
import android.graphics.Bitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FR-050. The saved signatures: transparent PNGs in `filesDir/signatures`, newest first, at most
 * [MAX_SIGNATURES]. No database and no encryption — they are files the user drew, and the app has
 * `allowBackup=false` so they never leave the phone.
 */
@Singleton
class SignatureStore @Inject constructor(@ApplicationContext private val context: Context) {

    private val directory: File get() = File(context.filesDir, "signatures").apply { mkdirs() }

    private val _signatures = MutableStateFlow(read())
    val signatures: StateFlow<List<File>> = _signatures.asStateFlow()

    val isFull: Boolean get() = _signatures.value.size >= MAX_SIGNATURES

    private fun read(): List<File> =
        directory.listFiles { file -> file.extension == "png" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    fun refresh() {
        _signatures.value = read()
    }

    suspend fun save(bitmap: Bitmap): File? = withContext(Dispatchers.IO) {
        if (isFull) return@withContext null
        val file = File(directory, "${UUID.randomUUID()}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        refresh()
        file
    }

    fun delete(file: File) {
        file.delete()
        refresh()
    }

    companion object {
        const val MAX_SIGNATURES = 3
    }
}
