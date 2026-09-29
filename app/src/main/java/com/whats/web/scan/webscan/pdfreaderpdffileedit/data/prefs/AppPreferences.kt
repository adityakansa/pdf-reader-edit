package com.whats.web.scan.webscan.pdfreaderpdffileedit.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class SortField { NAME, DATE, SIZE }

data class SortOrder(val field: SortField = SortField.DATE, val ascending: Boolean = false)

private val Context.dataStore by preferencesDataStore("app_prefs")

/** FR-014, FR-010, FR-086: the handful of values that must survive a restart. */
@Singleton
class AppPreferences @Inject constructor(@ApplicationContext private val context: Context) {
    private val sortFieldKey = stringPreferencesKey("sort_field")
    private val sortAscKey = booleanPreferencesKey("sort_ascending")
    private val explainerKey = booleanPreferencesKey("storage_explainer_shown")
    private val proKey = booleanPreferencesKey("is_pro")
    private val samplesKey = booleanPreferencesKey("samples_installed")

    val sortOrder: Flow<SortOrder> = context.dataStore.data.map { p ->
        SortOrder(
            field = p[sortFieldKey]?.let { runCatching { SortField.valueOf(it) }.getOrNull() } ?: SortField.DATE,
            ascending = p[sortAscKey] ?: false,
        )
    }

    val storageExplainerShown: Flow<Boolean> = context.dataStore.data.map { it[explainerKey] ?: false }

    /** Cached entitlement so Pro survives an offline start (FR-084). */
    val isProCached: Flow<Boolean> = context.dataStore.data.map { it[proKey] ?: false }

    val samplesInstalled: Flow<Boolean> = context.dataStore.data.map { it[samplesKey] ?: false }

    suspend fun setSortOrder(order: SortOrder) = context.dataStore.edit {
        it[sortFieldKey] = order.field.name
        it[sortAscKey] = order.ascending
    }

    suspend fun setStorageExplainerShown(shown: Boolean) = context.dataStore.edit { it[explainerKey] = shown }

    suspend fun setProCached(isPro: Boolean) = context.dataStore.edit { it[proKey] = isPro }

    suspend fun setSamplesInstalled(installed: Boolean) = context.dataStore.edit { it[samplesKey] = installed }
}
