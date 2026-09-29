package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.FileIndex
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.StorageAccess
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StorageAccessViewModel @Inject constructor(
    private val storageAccess: StorageAccess,
    private val index: FileIndex,
) : ViewModel() {
    val allFilesAccessAvailable: Boolean get() = storageAccess.allFilesAccessAvailable

    fun markShown() = viewModelScope.launch { storageAccess.markExplainerShown() }

    fun persist(uri: Uri) {
        storageAccess.persist(uri)
        index.refresh()
    }

    fun refresh() {
        storageAccess.refresh()
        index.refresh()
    }
}
