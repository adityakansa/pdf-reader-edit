package com.whats.web.scan.webscan.pdfreaderpdffileedit.billing

import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.prefs.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FR-086. The one thing the rest of the app asks about Pro. It reads the cached value so an offline
 * start is still Pro, and [BillingManager] refreshes it from Play on every resume.
 */
@Singleton
class Entitlement @Inject constructor(private val prefs: AppPreferences) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val isPro: StateFlow<Boolean> = prefs.isProCached.stateIn(scope, SharingStarted.Eagerly, false)

    fun update(isPro: Boolean) {
        scope.launch { prefs.setProCached(isPro) }
    }
}
