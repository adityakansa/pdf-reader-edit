package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.whats.web.scan.webscan.pdfreaderpdffileedit.R
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.SummaryPurple
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.SummaryPurpleBg
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.TranslateBlue
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.TranslateBlueBg

/** FR-060 (S03). */
@Composable
fun AiAssistantDialog(onTranslate: () -> Unit, onSummary: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    stringResource(R.string.ai_assistant),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                AiChoice(
                    label = stringResource(R.string.action_ai_translate),
                    icon = Icons.Filled.Translate,
                    background = TranslateBlueBg,
                    accent = TranslateBlue,
                    onClick = onTranslate,
                )
                AiChoice(
                    label = stringResource(R.string.action_ai_summary),
                    icon = Icons.Filled.AutoAwesome,
                    background = SummaryPurpleBg,
                    accent = SummaryPurple,
                    onClick = onSummary,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun AiChoice(
    label: String,
    icon: ImageVector,
    background: Color,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = ButtonDefaults.buttonColors(
            containerColor = background,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}
