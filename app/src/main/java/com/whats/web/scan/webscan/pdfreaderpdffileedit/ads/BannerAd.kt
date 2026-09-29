package com.whats.web.scan.webscan.pdfreaderpdffileedit.ads

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/**
 * FR-089. An anchored adaptive banner that takes zero height until it actually fills, so a failed load
 * never leaves a grey band above the nav bar. The divider is the accidental-click separation Play asks for.
 */
@Composable
fun BannerAd(visible: Boolean, modifier: Modifier = Modifier) {
    if (!visible) return
    val context = LocalContext.current
    var loaded by remember { mutableStateOf(false) }
    val adView = remember {
        AdView(context).apply {
            adUnitId = AdUnits.banner
            setAdSize(adaptiveSize(context))
        }
    }

    DisposableEffect(adView) {
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                loaded = true
            }

            override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                loaded = false
            }
        }
        adView.loadAd(AdRequest.Builder().build())
        onDispose { adView.destroy() }
    }

    if (loaded) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
    AndroidView(
        factory = { adView },
        // The view stays attached so it can finish loading, but it owns no space until it filled.
        modifier = if (loaded) modifier.fillMaxWidth() else Modifier.fillMaxWidth().height(0.dp),
        update = { },
    )
}

private fun adaptiveSize(context: Context): AdSize {
    val metrics = context.resources.displayMetrics
    val widthDp = (metrics.widthPixels / metrics.density).toInt()
    return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
}
