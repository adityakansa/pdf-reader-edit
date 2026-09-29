package com.whats.web.scan.webscan.pdfreaderpdffileedit.data.prefs

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Bookmarked pages per PDF (zero-based), stored as "3,10,42" under the file key. */
@Singleton
class Bookmarks @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("bookmarks", Context.MODE_PRIVATE)

    fun pages(key: String): Set<Int> =
        prefs.getString(key, null)?.split(',')?.mapNotNull { it.toIntOrNull() }?.toSortedSet() ?: emptySet()

    /** Adds or removes [page]; returns the new set. */
    fun toggle(key: String, page: Int): Set<Int> {
        val current = pages(key).toMutableSet()
        if (!current.add(page)) current.remove(page)
        prefs.edit {
            if (current.isEmpty()) remove(key) else putString(key, current.sorted().joinToString(","))
        }
        return current.toSortedSet()
    }
}
