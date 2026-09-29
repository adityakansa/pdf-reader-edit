package com.whats.web.scan.webscan.pdfreaderpdffileedit.ads

import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.whats.web.scan.webscan.pdfreaderpdffileedit.R

/**
 * FR-090 (S01). A small native ad under Select page: a grey skeleton while it loads, the ad once it
 * arrives, nothing at all if it fails. The "Ad" badge is required — a native ad must not look like
 * part of the app.
 */
@Composable
fun NativeAdSlot(visible: Boolean, modifier: Modifier = Modifier) {
    if (!visible) return
    val context = LocalContext.current
    var ad by remember { mutableStateOf<NativeAd?>(null) }
    var failed by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val loader = AdLoader.Builder(context, AdUnits.native)
            .forNativeAd { loaded ->
                ad?.destroy()
                ad = loaded
            }
            .withAdListener(object : com.google.android.gms.ads.AdListener() {
                override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                    failed = true
                }
            })
            .build()
        loader.loadAd(AdRequest.Builder().build())
        onDispose { ad?.destroy() }
    }

    if (failed) return
    val loaded = ad
    if (loaded == null) {
        // The skeleton in the screenshot: the space is reserved so the button above does not jump.
        androidx.compose.foundation.layout.Box(
            modifier
                .fillMaxWidth()
                .height(SLOT_HEIGHT)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        return
    }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(SLOT_HEIGHT),
        factory = { ctx ->
            val view = android.view.LayoutInflater.from(ctx).inflate(R.layout.native_ad_small, null)
                as NativeAdView
            view.iconView = view.findViewById<ImageView>(R.id.ad_icon)
            view.headlineView = view.findViewById<TextView>(R.id.ad_headline)
            view.callToActionView = view.findViewById<Button>(R.id.ad_cta)
            view
        },
        update = { view ->
            (view.headlineView as TextView).text = loaded.headline
            (view.callToActionView as Button).apply {
                text = loaded.callToAction
                visibility = if (loaded.callToAction == null) android.view.View.GONE else android.view.View.VISIBLE
            }
            (view.iconView as ImageView).apply {
                val icon = loaded.icon
                if (icon == null) {
                    visibility = android.view.View.GONE
                } else {
                    setImageDrawable(icon.drawable)
                    visibility = android.view.View.VISIBLE
                }
            }
            view.setNativeAd(loaded)
        },
    )
}

private val SLOT_HEIGHT = 80.dp
