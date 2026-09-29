package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.shell

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ads.AdConsent
import com.whats.web.scan.webscan.pdfreaderpdffileedit.billing.BillingManager
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.FileIndex
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.FileRepository
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.IncomingFile
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.StorageAccess
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Whole-app concerns that outlive any one screen: consent, entitlement refresh, the index. */
@HiltViewModel
class ShellViewModel @Inject constructor(
    private val adConsent: AdConsent,
    private val billing: BillingManager,
    private val storageAccess: StorageAccess,
    private val index: FileIndex,
    private val incomingFile: IncomingFile,
    val repository: FileRepository,
) : ViewModel() {
    val canShowAds: StateFlow<Boolean> = adConsent.canShowAds

    /** FR-021: a file handed to us by another app, to be opened as soon as the shell is up. */
    val incomingFiles = incomingFile.files

    /** Opens a file the app just wrote (the editor's output) the same way. */
    fun open(uri: android.net.Uri, mimeType: String?) {
        viewModelScope.launch { incomingFile.offer(uri, mimeType) }
    }

    fun onStart(activity: Activity) {
        adConsent.gatherConsent(activity)
        billing.refresh()
    }

    fun onResume() {
        storageAccess.refresh()
        index.refresh()
    }
}
