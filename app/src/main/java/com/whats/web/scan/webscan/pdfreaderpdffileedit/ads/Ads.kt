package com.whats.web.scan.webscan.pdfreaderpdffileedit.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.whats.web.scan.webscan.pdfreaderpdffileedit.BuildConfig
import com.whats.web.scan.webscan.pdfreaderpdffileedit.billing.Entitlement
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FR-087 / FR-088. Consent first, then — and only for a user who is not Pro — the SDK. Nothing in the
 * app may request an ad before [canShowAds] is true.
 */
@Singleton
class AdConsent @Inject constructor(
    @ApplicationContext private val context: Context,
    private val entitlement: Entitlement,
) {
    private val initialised = AtomicBoolean(false)
    private val _canShowAds = MutableStateFlow(false)
    val canShowAds: StateFlow<Boolean> = _canShowAds.asStateFlow()

    private val consentInformation: ConsentInformation by lazy {
        UserMessagingPlatform.getConsentInformation(context)
    }

    val privacyOptionsRequired: Boolean
        get() = consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    fun gatherConsent(activity: Activity) {
        if (entitlement.isPro.value) return
        val params = ConsentRequestParameters.Builder()
            .apply {
                if (BuildConfig.DEBUG) {
                    setConsentDebugSettings(
                        ConsentDebugSettings.Builder(activity)
                            .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                            .build(),
                    )
                }
            }
            .build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { initialiseIfAllowed() }
            },
            { initialiseIfAllowed() },
        )
        initialiseIfAllowed()
    }

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { }
    }

    private fun initialiseIfAllowed() {
        if (entitlement.isPro.value) return
        if (!consentInformation.canRequestAds()) return
        if (initialised.compareAndSet(false, true)) {
            MobileAds.initialize(context) { }
            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder()
                    .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_PG)
                    .setTagForChildDirectedTreatment(
                        RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE,
                    )
                    .build(),
            )
        }
        _canShowAds.value = true
    }
}

object AdUnits {
    val banner: String = BuildConfig.AD_BANNER_UNIT
    val native: String = BuildConfig.AD_NATIVE_UNIT
}
