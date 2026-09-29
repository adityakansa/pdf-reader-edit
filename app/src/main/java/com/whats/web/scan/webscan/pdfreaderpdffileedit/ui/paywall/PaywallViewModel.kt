package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.paywall

import android.app.Activity
import androidx.lifecycle.ViewModel
import com.whats.web.scan.webscan.pdfreaderpdffileedit.billing.BillingManager
import com.whats.web.scan.webscan.pdfreaderpdffileedit.billing.Entitlement
import com.whats.web.scan.webscan.pdfreaderpdffileedit.billing.PlanOffer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val billing: BillingManager,
    entitlement: Entitlement,
) : ViewModel() {
    val offers: StateFlow<List<PlanOffer>> = billing.offers
    val isPro: StateFlow<Boolean> = entitlement.isPro

    fun refresh() = billing.refresh()

    fun purchase(activity: Activity, offer: PlanOffer) = billing.purchase(activity, offer)
}
