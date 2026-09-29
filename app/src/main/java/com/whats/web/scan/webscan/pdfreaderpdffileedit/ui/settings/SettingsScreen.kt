package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whats.web.scan.webscan.pdfreaderpdffileedit.BuildConfig
import com.whats.web.scan.webscan.pdfreaderpdffileedit.R
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.prefs.ThemeMode
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.components.Intents
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.home.openDefaultAppSettings
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.shell.ProAction
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.shell.ScreenTitle
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.shell.TitleBar
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.BrandRed
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.CrownGold
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.CtaOrange
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.CtaRed

/**
 * FR-080 … FR-083 (S06), laid out in Step 12a like One Read's Settings: the "Remove ads" card, then
 * General, Display and Help groups of plain rows, and the version at the bottom.
 */
@Composable
fun SettingsContent(
    onPaywall: () -> Unit,
    onNotices: () -> Unit,
    onFileManager: () -> Unit,
    onRecycleBin: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val keepScreenOn by viewModel.keepScreenOn.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    var themeDialog by remember { mutableStateOf(false) }
    var faq by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TitleBar(title = { ScreenTitle(stringResource(R.string.settings_title)) }) {
            ProAction(isPro, onPaywall)
            IconButton(onClick = { activity?.let(viewModel::requestReview) }) {
                Icon(Icons.Outlined.ThumbUp, contentDescription = stringResource(R.string.settings_rate))
            }
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            PremiumCard(isPro = isPro, onClick = onPaywall)

            GroupTitle(R.string.settings_general)
            SettingRow(Icons.Outlined.FolderOpen, stringResource(R.string.settings_file_manager), onClick = onFileManager)
            SettingRow(Icons.Outlined.Delete, stringResource(R.string.tool_recycle_bin), onClick = onRecycleBin)
            SettingRow(
                Icons.Outlined.PhoneAndroid,
                stringResource(R.string.settings_keep_screen_on),
                trailing = {
                    Switch(
                        checked = keepScreenOn,
                        onCheckedChange = viewModel::setKeepScreenOn,
                        colors = SwitchDefaults.colors(checkedTrackColor = BrandRed),
                    )
                },
                onClick = { viewModel.setKeepScreenOn(!keepScreenOn) },
            )
            SettingRow(Icons.Outlined.AutoStories, stringResource(R.string.settings_default_reader)) {
                openDefaultAppSettings(context)
            }
            SettingRow(Icons.Outlined.Share, stringResource(R.string.settings_share)) {
                Intents.shareText(context, context.getString(R.string.share_app_text, viewModel.storeUrl))
            }

            GroupTitle(R.string.settings_display)
            SettingRow(
                Icons.Outlined.Brightness6,
                stringResource(R.string.settings_theme),
                subtitle = stringResource(themeMode.label),
            ) { themeDialog = true }
            SettingRow(
                Icons.Outlined.Language,
                stringResource(R.string.settings_language),
                subtitle = viewModel.languageLabel,
            ) { openLanguageSettings(context) }

            GroupTitle(R.string.settings_help)
            SettingRow(Icons.AutoMirrored.Filled.HelpOutline, stringResource(R.string.settings_faq), chevron = false) {
                faq = true
            }
            SettingRow(Icons.Outlined.EditNote, stringResource(R.string.settings_request_feature), chevron = false) {
                Intents.email(context, BuildConfig.SUPPORT_EMAIL, context.getString(R.string.mail_feature_subject))
            }
            SettingRow(Icons.Outlined.MailOutline, stringResource(R.string.settings_feedback), chevron = false) {
                Intents.email(
                    context,
                    BuildConfig.SUPPORT_EMAIL,
                    context.getString(R.string.mail_feedback_subject),
                    context.getString(R.string.mail_feedback_body, BuildConfig.VERSION_NAME, Build.MODEL, Build.VERSION.RELEASE),
                )
            }
            SettingRow(Icons.Outlined.StarOutline, stringResource(R.string.settings_rate), chevron = false) {
                activity?.let(viewModel::requestReview)
            }
            SettingRow(Icons.Outlined.Description, stringResource(R.string.settings_terms), chevron = false) {
                Intents.openUrl(context, BuildConfig.TERMS_URL)
            }
            SettingRow(Icons.Outlined.PrivacyTip, stringResource(R.string.settings_privacy), chevron = false) {
                Intents.openUrl(context, BuildConfig.PRIVACY_POLICY_URL)
            }
            if (viewModel.privacyOptionsRequired && !isPro) {
                SettingRow(Icons.Outlined.Block, stringResource(R.string.settings_privacy_options), chevron = false) {
                    activity?.let(viewModel::showPrivacyOptions)
                }
            }
            SettingRow(Icons.Outlined.Gavel, stringResource(R.string.settings_notices), chevron = false, onClick = onNotices)

            Text(
                stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
            )
        }
    }

    if (themeDialog) {
        ThemeDialog(
            current = themeMode,
            onPick = { viewModel.setThemeMode(it); themeDialog = false },
            onDismiss = { themeDialog = false },
        )
    }
    if (faq) FaqDialog(onDismiss = { faq = false })
}

val ThemeMode.label: Int
    get() = when (this) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    }

/** "Remove ads — Unlock all premium features ›" in the orange gradient; "Premium" once bought. */
@Composable
private fun PremiumCard(isPro: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(listOf(CtaOrange, CtaRed)))
            .clickable(enabled = !isPro, role = Role.Button, onClick = onClick)
            .padding(16.dp),
    ) {
        Box(
            Modifier
                .size(44.dp)
                .background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = if (isPro) CrownGold else Color.White)
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                stringResource(if (isPro) R.string.pro_active else R.string.remove_ads),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(if (isPro) R.string.pro_active_body else R.string.remove_ads_body),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.92f),
            )
        }
        if (!isPro) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = CtaRed,
                modifier = Modifier
                    .size(30.dp)
                    .background(Color.White, CircleShape)
                    .padding(3.dp),
            )
        }
    }
}

@Composable
private fun GroupTitle(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    label: String,
    subtitle: String? = null,
    chevron: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = if (subtitle == null) 16.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        Column(
            Modifier
                .weight(1f)
                .padding(start = 16.dp),
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        when {
            trailing != null -> trailing()
            chevron -> Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ThemeDialog(current: ThemeMode, onPick: (ThemeMode) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_theme)) },
        text = {
            Column {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = mode == current, role = Role.RadioButton) { onPick(mode) }
                            .padding(vertical = 8.dp),
                    ) {
                        RadioButton(selected = mode == current, onClick = null)
                        Text(stringResource(mode.label), modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Short answers to what users of reader apps ask most, all true of this app. */
@Composable
private fun FaqDialog(onDismiss: () -> Unit) {
    val items = listOf(
        R.string.faq_q_find to R.string.faq_a_find,
        R.string.faq_q_default to R.string.faq_a_default,
        R.string.faq_q_edit to R.string.faq_a_edit,
        R.string.faq_q_saved to R.string.faq_a_saved,
        R.string.faq_q_deleted to R.string.faq_a_deleted,
        R.string.faq_q_privacy to R.string.faq_a_privacy,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_faq)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                items.forEach { (q, a) ->
                    Text(
                        stringResource(q),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(stringResource(a), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
    )
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
