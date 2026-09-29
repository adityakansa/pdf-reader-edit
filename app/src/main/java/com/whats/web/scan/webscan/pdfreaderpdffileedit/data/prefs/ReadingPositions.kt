package com.whats.web.scan.webscan.pdfreaderpdffileedit.data.prefs

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The last page read in each PDF, so a document reopens where the user stopped (as Acrobat does).
 * A plain SharedPreferences file: one int per file key, written on page change, read once on open.
 */
@Singleton
class ReadingPositions @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("reading_positions", Context.MODE_PRIVATE)

    /** Zero-based page, or 0 for a document never opened before. */
    fun lastPage(key: String): Int = prefs.getInt(key, 0)

    fun save(key: String, page: Int) {
        prefs.edit {
            // Keep the file small: forget everything once it holds more documents than anyone rereads.
            if (prefs.all.size >= MAX_DOCUMENTS && !prefs.contains(key)) clear()
            putInt(key, page)
        }
    }

    private companion object {
        const val MAX_DOCUMENTS = 500
    }
}
