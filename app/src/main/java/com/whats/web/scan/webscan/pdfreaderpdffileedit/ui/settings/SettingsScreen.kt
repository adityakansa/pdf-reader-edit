package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AppCompatDelegate
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GTranslate
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whats.web.scan.webscan.pdfreaderpdffileedit.BuildConfig
import com.whats.web.scan.webscan.pdfreaderpdffileedit.R
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.components.Intents
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.BrandRed
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.BrandRedDark
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.CrownGold

/** FR-080 … FR-083 (S06). Rendered inside the Setting tab, so it brings its own title. */
@Composable
fun SettingsContent(
    onPaywall: () -> Unit,
    onNotices: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        ProCard(isPro = isPro, onClick = onPaywall)

        SettingRow(Icons.Filled.Translate, stringResource(R.string.settings_language), viewModel.languageLabel) {
            openLanguageSettings(context)
        }
        SettingRow(Icons.Filled.OpenInNew, stringResource(R.string.settings_set_default)) {
            openDefaultAppSettings(context)
        }
        SettingRow(Icons.Filled.StarOutline, stringResource(R.string.settings_rate)) {
            activity?.let(viewModel::requestReview)
        }
        SettingRow(Icons.AutoMirrored.Filled.Send, stringResource(R.string.settings_share)) {
            Intents.shareText(
                context,
                context.getString(R.string.share_app_text, viewModel.storeUrl),
            )
        }
        SettingRow(Icons.Filled.PrivacyTip, stringResource(R.string.settings_privacy)) {
            Intents.openUrl(context, BuildConfig.PRIVACY_POLICY_URL)
        }
        if (viewModel.privacyOptionsRequired && !isPro) {
            SettingRow(Icons.Filled.Block, stringResource(R.string.settings_privacy_options)) {
                activity?.let(viewModel::showPrivacyOptions)
            }
        }
        SettingRow(Icons.Filled.Gavel, stringResource(R.string.settings_notices), onClick = onNotices)
    }
}

@Composable
private fun ProCard(isPro: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Column(
            Modifier
                .background(Brush.horizontalGradient(listOf(BrandRed, BrandRedDark)))
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = CrownGold)
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                ) {
                    Text(
                        stringResource(if (isPro) R.string.pro_active else R.string.pro_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                    if (!isPro) {
                        Text(
                            stringResource(R.string.pro_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.9f),
                        )
                    }
                }
                if (!isPro) {
                    Button(
                        onClick = onClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = BrandRed,
                        ),
                    ) {
                        Text(
                            stringResource(R.string.free_trial),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ProFeature(Icons.Filled.AutoAwesome, stringResource(R.string.feature_ai_summary))
                ProFeature(Icons.Filled.GTranslate, stringResource(R.string.feature_ai_translate))
                ProFeature(Icons.Filled.Block, stringResource(R.string.feature_ads_free))
                ProFeature(Icons.Filled.Edit, stringResource(R.string.feature_edit))
            }
        }
    }
}

@Composable
private fun ProFeature(icon: ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    label: String,
    value: String? = null,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
            )
            if (value != null) {
                Text(
                    value,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = BrandRed,
            )
        }
    }
}

/** FR-005. On API 33+ the system owns the per-app language screen; below that we set it ourselves. */
private fun openLanguageSettings(context: android.content.Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.parse("package:${context.packageName}")),
            )
        }.onFailure { AppCompatDelegate.setApplicationLocales(AppCompatDelegate.getApplicationLocales()) }
    } else {
        // Only English ships today; this keeps the stored locale list consistent for when more arrive.
        AppCompatDelegate.setApplicationLocales(AppCompatDelegate.getApplicationLocales())
    }
}

/** FR-021 */
private fun openDefaultAppSettings(context: android.content.Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Intent(
            Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        )
    } else {
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        )
    }
    runCatching { context.startActivity(intent) }
}
