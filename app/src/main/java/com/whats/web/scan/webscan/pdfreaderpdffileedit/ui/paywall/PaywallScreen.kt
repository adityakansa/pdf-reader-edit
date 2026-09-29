package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.paywall

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GTranslate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whats.web.scan.webscan.pdfreaderpdffileedit.BuildConfig
import com.whats.web.scan.webscan.pdfreaderpdffileedit.R
import com.whats.web.scan.webscan.pdfreaderpdffileedit.billing.PlanOffer
import com.whats.web.scan.webscan.pdfreaderpdffileedit.billing.Products
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.components.Intents
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.BrandRed

/** FR-085 / FR-103. Price, period, trial length, renewal and the cancel path, all before the button. */
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

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(Modifier.fillMaxWidth()) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_back))
            }
        }
        Text(
            stringResource(R.string.pro_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.pro_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            textAlign = TextAlign.Center,
        )

        Column(Modifier.padding(24.dp)) {
            Benefit(stringResource(R.string.feature_ai_summary), Icons.Filled.AutoAwesome)
            Benefit(stringResource(R.string.feature_ai_translate), Icons.Filled.GTranslate)
            Benefit(stringResource(R.string.feature_ads_free), Icons.Filled.Block)
            Benefit(stringResource(R.string.feature_edit), Icons.Filled.Edit)
        }

        if (offers.isEmpty()) {
            Text(
                stringResource(R.string.paywall_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                textAlign = TextAlign.Center,
            )
        }

        offers.forEach { offer ->
            PlanCard(offer = offer, selected = offer == selected, onClick = { selected = offer })
        }

        val current = selected
        Text(
            text = when {
                current == null -> ""
                current.trialPeriod != null -> stringResource(
                    R.string.paywall_trial_text,
                    humanPeriod(current.trialPeriod),
                    "${current.formattedPrice} / ${humanPeriod(current.billingPeriod)}",
                )
                else -> stringResource(R.string.paywall_renews)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            textAlign = TextAlign.Center,
        )

        Button(
            onClick = { current?.let { offer -> activity?.let { viewModel.purchase(it, offer) } } },
            enabled = current != null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 8.dp),
        ) {
            Text(
                stringResource(
                    if (current?.trialPeriod != null) R.string.free_trial else R.string.get_pro,
                ),
                fontWeight = FontWeight.Bold,
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            TextButton(onClick = { viewModel.refresh() }) {
                Text(stringResource(R.string.paywall_restore))
            }
            TextButton(
                onClick = {
                    Intents.openUrl(context, "https://play.google.com/store/account/subscriptions")
                },
            ) { Text(stringResource(R.string.paywall_manage)) }
            TextButton(onClick = { Intents.openUrl(context, BuildConfig.PRIVACY_POLICY_URL) }) {
                Text(stringResource(R.string.settings_privacy))
            }
        }
    }
}

@Composable
private fun Benefit(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        Modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = BrandRed, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun PlanCard(offer: PlanOffer, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) BrandRed else MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(
                        if (offer.basePlanId == Products.PLAN_YEARLY) R.string.paywall_yearly
                        else R.string.paywall_monthly,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "${offer.formattedPrice} / ${humanPeriod(offer.billingPeriod)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = BrandRed)
        }
    }
}

/** Play returns ISO-8601 periods ("P1Y", "P7D"); the paywall has to say them in words. */
private fun humanPeriod(iso: String): String {
    val amount = iso.filter { it.isDigit() }.toIntOrNull() ?: return iso
    return when (iso.last()) {
        'Y' -> if (amount == 1) "year" else "$amount years"
        'M' -> if (amount == 1) "month" else "$amount months"
        'W' -> if (amount == 1) "week" else "$amount weeks"
        'D' -> if (amount == 1) "day" else "$amount days"
        else -> iso
    }
}
