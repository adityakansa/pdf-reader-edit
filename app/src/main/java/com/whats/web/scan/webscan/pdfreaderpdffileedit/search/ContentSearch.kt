package com.whats.web.scan.webscan.pdfreaderpdffileedit.search

import android.content.Context
import android.util.LruCache
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.DocFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/** One document that contains the query: the first page it is on, how often, and where. */
data class ContentHit(val file: DocFile, val page: Int, val matches: Int, val snippets: List<String>)

/** Progress of a search across the library. */
sealed interface ContentSearchEvent {
    data class Hit(val hit: ContentHit) : ContentSearchEvent
    data class Progress(val done: Int, val total: Int) : ContentSearchEvent
}

/**
 * Search inside every document in the library, as the competing "document reader" apps offer.
 *
 * Text comes from [DocumentText] and is cached per file (memory LRU plus `cacheDir/text`, keyed on the
 * file key, size and modified time), so the first search reads each file once and every later search
 * is a string scan. Files over [MAX_BYTES] and legacy binary formats are skipped. Scanned PDFs have no
 * text and simply never match. Collecting is cancellable between files.
 */
@Singleton
class ContentSearch @Inject constructor(@ApplicationContext private val context: Context) {

    private val dir: File get() = File(context.cacheDir, "text").apply { mkdirs() }
    private val scratch: File get() = File(context.cacheDir, "pdf").apply { mkdirs() }
    private val memory = object : LruCache<String, List<String>>(MEMORY_CHARS) {
        override fun sizeOf(key: String, value: List<String>) = value.sumOf { it.length }.coerceAtLeast(1)
    }

    fun search(query: String, files: List<DocFile>): Flow<ContentSearchEvent> = flow {
        val searchable = files.filter { it.size in 1..MAX_BYTES && it.ext.lowercase() in SEARCHABLE }
            // Small files first: results start arriving quickly.
            .sortedBy { it.size }
        searchable.forEachIndexed { index, file ->
            coroutineContext.ensureActive()
            val pages = textOf(file)
            var first = -1
            var total = 0
            val snippets = mutableListOf<String>()
            pages.forEachIndexed { page, text ->
                val n = DocumentText.count(text, query)
                if (n > 0) {
                    if (first < 0) first = page
                    total += n
                    if (snippets.size < MAX_SNIPPETS) snippets += DocumentText.snippets(text, query, MAX_SNIPPETS - snippets.size)
                }
            }
            if (total > 0) emit(ContentSearchEvent.Hit(ContentHit(file, first, total, snippets)))
            emit(ContentSearchEvent.Progress(index + 1, searchable.size))
        }
        if (searchable.isEmpty()) emit(ContentSearchEvent.Progress(0, 0))
    }.flowOn(Dispatchers.IO)

    private fun textOf(file: DocFile): List<String> {
        val key = cacheKey(file)
        memory.get(key)?.let { return it }
        val cached = File(dir, "$key.txt")
        val pages = if (cached.exists()) {
            cached.readText().split(PAGE_BREAK)
        } else {
            val extracted = runCatching {
                context.contentResolver.openInputStream(file.uri)?.use { DocumentText.of(file.ext, it, scratch) }
            }.getOrNull().orEmpty()
            runCatching {
                val temp = File(dir, "$key.tmp")
                temp.writeText(extracted.joinToString(PAGE_BREAK.toString()))
                temp.renameTo(cached)
            }
            extracted
        }
        memory.put(key, pages)
        return pages
    }

    private fun cacheKey(file: DocFile): String =
        MessageDigest.getInstance("SHA-1").digest("${file.key}|${file.size}|${file.modified}".toByteArray())
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_BYTES = 40L * 1024 * 1024
        const val MEMORY_CHARS = 4_000_000
        const val MAX_SNIPPETS = 2
        const val PAGE_BREAK = '\u000C'
        val SEARCHABLE = setOf("pdf", "docx", "xlsx", "pptx", "txt", "csv")
    }
}
