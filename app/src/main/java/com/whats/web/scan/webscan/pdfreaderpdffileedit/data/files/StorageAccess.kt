package com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import com.whats.web.scan.webscan.pdfreaderpdffileedit.BuildConfig
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.prefs.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FR-010 / FR-101. Two modes: all-files access (the phone-wide library the screenshots show) and the
 * SAF fallback, where the library is whatever the user handed over plus this app's own output.
 */
@Singleton
class StorageAccess @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AppPreferences,
) {
    data class State(
        val hasFullAccess: Boolean,
        val grantedTrees: List<Uri> = emptyList(),
        val grantedFiles: List<Uri> = emptyList(),
    )

    private val _state = MutableStateFlow(read())
    val state: StateFlow<State> = _state.asStateFlow()

    /** True when the build may even ask for MANAGE_EXTERNAL_STORAGE. */
    val allFilesAccessAvailable: Boolean = BuildConfig.ALL_FILES_ACCESS

    fun refresh() {
        _state.value = read()
    }

    private fun read(): State {
        val persisted = context.contentResolver.persistedUriPermissions
        val trees = persisted.filter { it.isReadPermission && DocumentsTree.isTree(it.uri) }.map { it.uri }
        val files = persisted.filter { it.isReadPermission && !DocumentsTree.isTree(it.uri) }.map { it.uri }
        return State(hasFullAccess = hasFullAccess(), grantedTrees = trees, grantedFiles = files)
    }

    fun hasFullAccess(): Boolean = when {
        !allFilesAccessAvailable -> false
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
        else -> ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** Remember that the explainer has been shown so it never blocks the second launch. */
    suspend fun markExplainerShown() = prefs.setStorageExplainerShown(true)

    fun persist(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            .onFailure {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }
        refresh()
    }

    private object DocumentsTree {
        fun isTree(uri: Uri): Boolean = uri.path?.startsWith("/tree/") == true
    }
}
