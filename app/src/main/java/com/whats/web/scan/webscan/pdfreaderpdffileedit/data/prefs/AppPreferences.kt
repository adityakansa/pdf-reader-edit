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

/** How the library shows files: rows, page-preview cards, or grouped by folder. */
enum class LibraryView { LIST, GRID, FOLDERS }

/** Settings → App theme. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

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
    private val viewKey = stringPreferencesKey("library_view")
    private val defaultBannerKey = booleanPreferencesKey("default_banner_dismissed")
    private val keepScreenOnKey = booleanPreferencesKey("keep_screen_on")
    private val themeKey = stringPreferencesKey("theme_mode")
    private val ratingKey = booleanPreferencesKey("rating_asked")
    private val editorIntroKey = booleanPreferencesKey("editor_intro_shown")

    val sortOrder: Flow<SortOrder> = context.dataStore.data.map { p ->
        SortOrder(
            field = p[sortFieldKey]?.let { runCatching { SortField.valueOf(it) }.getOrNull() } ?: SortField.DATE,
            ascending = p[sortAscKey] ?: false,
        )
    }

    val storageExplainerShown: Flow<Boolean> = context.dataStore.data.map { it[explainerKey] ?: false }

    /** Cached entitlement so Pro survives an offline start (FR-084). */
    val isProCached: Flow<Boolean> = context.dataStore.data.map { it[proKey] ?: false }

    /** List (default), grid of page previews, or folders. */
    val libraryView: Flow<LibraryView> = context.dataStore.data.map { p ->
        p[viewKey]?.let { runCatching { LibraryView.valueOf(it) }.getOrNull() } ?: LibraryView.LIST
    }

    suspend fun setLibraryView(view: LibraryView) = context.dataStore.edit { it[viewKey] = view.name }

    val samplesInstalled: Flow<Boolean> = context.dataStore.data.map { it[samplesKey] ?: false }

    suspend fun setSortOrder(order: SortOrder) = context.dataStore.edit {
        it[sortFieldKey] = order.field.name
        it[sortAscKey] = order.ascending
    }

    suspend fun setStorageExplainerShown(shown: Boolean) = context.dataStore.edit { it[explainerKey] = shown }

    suspend fun setProCached(isPro: Boolean) = context.dataStore.edit { it[proKey] = isPro }

    suspend fun setSamplesInstalled(installed: Boolean) = context.dataStore.edit { it[samplesKey] = installed }

    /** Home's "Set as default reader" banner, once closed, stays closed. */
    val defaultBannerDismissed: Flow<Boolean> = context.dataStore.data.map { it[defaultBannerKey] ?: false }

    suspend fun dismissDefaultBanner() = context.dataStore.edit { it[defaultBannerKey] = true }

    /** Settings → Keep screen on: the screen stays awake while the app is in front. */
    val keepScreenOn: Flow<Boolean> = context.dataStore.data.map { it[keepScreenOnKey] ?: false }

    suspend fun setKeepScreenOn(on: Boolean) = context.dataStore.edit { it[keepScreenOnKey] = on }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { p ->
        p[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[themeKey] = mode.name }

    /** The "Are you satisfied?" sheet is asked once, after a successful conversion or save. */
    val ratingAsked: Flow<Boolean> = context.dataStore.data.map { it[ratingKey] ?: false }

    suspend fun setRatingAsked() = context.dataStore.edit { it[ratingKey] = true }

    /** The PDF editor's one-page introduction is shown the first time only. */
    val editorIntroShown: Flow<Boolean> = context.dataStore.data.map { it[editorIntroKey] ?: false }

    suspend fun setEditorIntroShown() = context.dataStore.edit { it[editorIntroKey] = true }
}
