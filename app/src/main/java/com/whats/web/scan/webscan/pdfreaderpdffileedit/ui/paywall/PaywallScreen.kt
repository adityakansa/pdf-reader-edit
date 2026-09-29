package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.paywall

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whats.web.scan.webscan.pdfreaderpdffileedit.BuildConfig
import com.whats.web.scan.webscan.pdfreaderpdffileedit.R
import com.whats.web.scan.webscan.pdfreaderpdffileedit.billing.PlanOffer
import com.whats.web.scan.webscan.pdfreaderpdffileedit.billing.Products
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.components.Intents
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.CrownGold
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.PremiumAccent
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.PremiumCard
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.PremiumDark

/**
 * FR-084, laid out in Step 12d like One Read's "Get Premium" (screen 70): a dark page, what Pro gives,
 * the plans with the trial on top and "Save N%", one white button, and the small print. Claims are only
 * ones that are true of this app — no invented reviews or user counts.
 */
@Composable
fun PaywallScreen(onClose: () -> Unit, viewModel: PaywallViewModel = hiltViewModel()) {
    val offers by viewModel.offers.collectAsStateWithLifecycle()
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    var selected by remember { mutableStateOf<PlanOffer?>(null) }

    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(offers) {
        if (selected == null) {
            selected = offers.firstOrNull { it.basePlanId == Products.PLAN_YEARLY } ?: offers.firstOrNull()
        }
    }
    LaunchedEffect(isPro) { if (isPro) onClose() }

    val yearly = offers.firstOrNull { it.basePlanId == Products.PLAN_YEARLY }
    val monthly = offers.firstOrNull { it.basePlanId != Products.PLAN_YEARLY }
    val saving = if (yearly != null && monthly != null) {
        PlanMath.savingPercent(yearly.priceMicros, yearly.billingPeriod, monthly.priceMicros, monthly.billingPeriod)
    } else {
        0
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF3A2418), PremiumDark, PremiumDark))),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable(role = Role.Button, onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close), tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.paywall_restore),
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable(role = Role.Button) { viewModel.refresh() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }

            Text(
                stringResource(R.string.premium_title),
                color = Color.White,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp,
                modifier = Modifier.padding(top = 28.dp),
            )
            Column(Modifier.padding(top = 14.dp)) {
                listOf(
                    R.string.premium_benefit_ads,
                    R.string.premium_benefit_ai,
                    R.string.premium_benefit_sign,
                    R.string.premium_benefit_files,
                ).forEach { Benefit(stringResource(it)) }
            }

            Spacer(Modifier.height(24.dp))
            if (offers.isEmpty()) {
                Text(
                    stringResource(R.string.paywall_unavailable),
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                )
            }
            offers.sortedByDescending { it.basePlanId == Products.PLAN_YEARLY }.forEach { offer ->
                PlanRow(
                    offer = offer,
                    selected = offer == selected,
                    badge = if (offer == yearly && saving > 0) stringResource(R.string.premium_save, saving) else null,
                    onClick = { selected = offer },
                )
            }

            PrivacyBadge()
            Spacer(Modifier.height(140.dp))
        }

        // The button and small print stay in reach while the page scrolls, as in the reference.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, PremiumDark, PremiumDark)))
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            val current = selected
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(if (current != null) Color.White else Color.White.copy(alpha = 0.4f))
                    .clickable(enabled = current != null, role = Role.Button) {
                        current?.let { offer -> activity?.let { viewModel.purchase(it, offer) } }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(if (current?.trialPeriod != null) R.string.premium_start_trial else R.string.get_pro),
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Text(
                stringResource(R.string.premium_cancel_anytime),
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 10.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 6.dp)) {
                SmallLink(stringResource(R.string.settings_terms)) { Intents.openUrl(context, BuildConfig.TERMS_URL) }
                Text("|", color = Color.White.copy(alpha = 0.5f))
                SmallLink(stringResource(R.string.settings_privacy)) { Intents.openUrl(context, BuildConfig.PRIVACY_POLICY_URL) }
                Text("|", color = Color.White.copy(alpha = 0.5f))
                SmallLink(stringResource(R.string.paywall_manage)) {
                    Intents.openUrl(context, "https://play.google.com/store/account/subscriptions")
                }
            }
        }
    }
}

@Composable
private fun Benefit(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
        Icon(Icons.Filled.Check, contentDescription = null, tint = PremiumAccent, modifier = Modifier.size(22.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp))
    }
}

/** "3-Day Free Trial / Then only ₹11.37/day ····· ₹4,150.00/year" with the "Save 74%" tag on its corner. */
@Composable
private fun PlanRow(offer: PlanOffer, selected: Boolean, badge: String?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val days = PlanMath.days(offer.billingPeriod)
    val perDay = PlanMath.perDay(offer.priceMicros, days, offer.currencyCode)
    Box(Modifier.padding(top = 14.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(PremiumCard)
                .border(if (selected) 2.dp else 1.dp, if (selected) PremiumAccent else Color.White.copy(alpha = 0.12f), shape)
                .clickable(role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        offer.trialPeriod != null -> stringResource(R.string.premium_trial_title, trialWords(offer.trialPeriod))
                        offer.basePlanId == Products.PLAN_YEARLY -> stringResource(R.string.paywall_yearly)
                        else -> stringResource(R.string.paywall_monthly)
                    },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                )
                if (perDay != null) {
                    Text(
                        stringResource(if (offer.trialPeriod != null) R.string.premium_then_per_day else R.string.premium_per_day, perDay),
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Text(
                "${offer.formattedPrice}/${periodWord(offer.billingPeriod)}",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        badge?.let {
            Text(
                it,
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-8).dp, y = (-10).dp)
                    .background(CrownGold, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

/** Where the reference shows a laurel and a user count, this app says what is true: nothing leaves the phone. */
@Composable
private fun PrivacyBadge() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, tint = CrownGold, modifier = Modifier.size(20.dp))
        Column(Modifier.padding(start = 10.dp)) {
            Text(stringResource(R.string.premium_private_title), color = CrownGold, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.premium_private_body), color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SmallLink(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = Color.White.copy(alpha = 0.75f),
        textDecoration = TextDecoration.Underline,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

private fun periodWord(iso: String): String = when (iso.lastOrNull()) {
    'Y' -> "year"
    'M' -> "month"
    'W' -> "week"
    'D' -> "day"
    else -> iso
}

private fun trialWords(iso: String): String {
    val amount = iso.filter { it.isDigit() }.toIntOrNull() ?: return iso
    val days = when (iso.lastOrNull()) {
        'W' -> amount * 7
        'M' -> amount * 30
        else -> amount
    }
    return "$days-Day"
}
