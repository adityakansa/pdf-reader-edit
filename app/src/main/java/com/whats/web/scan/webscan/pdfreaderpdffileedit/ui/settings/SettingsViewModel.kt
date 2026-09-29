package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.settings

import android.app.Activity
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModel
import com.google.android.play.core.review.ReviewManagerFactory
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ads.AdConsent
import com.whats.web.scan.webscan.pdfreaderpdffileedit.billing.Entitlement
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.components.Intents
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val adConsent: AdConsent,
    entitlement: Entitlement,
) : ViewModel() {
    val isPro: StateFlow<Boolean> = entitlement.isPro

    val storeUrl: String = "https://play.google.com/store/apps/details?id=${context.packageName}"

    /** The locale the user picked, or the system one until they pick. */
    val languageLabel: String
        get() {
            val locales = AppCompatDelegate.getApplicationLocales()
            val locale = if (locales.isEmpty) Locale.getDefault() else locales[0] ?: Locale.ENGLISH
            return locale.getDisplayLanguage(locale).replaceFirstChar { it.uppercase() }
        }

    val privacyOptionsRequired: Boolean get() = adConsent.privacyOptionsRequired

    fun showPrivacyOptions(activity: Activity) = adConsent.showPrivacyOptions(activity)

    /** FR-081. In-app review when Play offers it, the store page when it does not. */
    fun requestReview(activity: Activity) {
        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    manager.launchReviewFlow(activity, task.result)
                } else {
                    Intents.openUrl(activity, "market://details?id=${context.packageName}")
                }
            }
    }
}
