package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.whats.web.scan.webscan.pdfreaderpdffileedit.R
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.BrandRed
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.BrandRedDark

enum class HomeTab { DOCUMENT, RECENT, FAVOURITE, SETTING }

/** FR-003. Four tabs with a raised Create button between the middle two. */
@Composable
fun BottomBar(
    selected: HomeTab,
    onSelect: (HomeTab) -> Unit,
    onCreate: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
        Box(contentAlignment = Alignment.TopCenter) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TabItem(HomeTab.DOCUMENT, Icons.Filled.Description, R.string.tab_document, selected, onSelect, Modifier.weight(1f))
                TabItem(HomeTab.RECENT, Icons.Filled.AccessTime, R.string.tab_recent, selected, onSelect, Modifier.weight(1f))
                Box(modifier = Modifier.weight(1f))
                TabItem(HomeTab.FAVOURITE, Icons.Filled.Star, R.string.tab_favourite, selected, onSelect, Modifier.weight(1f))
                TabItem(HomeTab.SETTING, Icons.Filled.Settings, R.string.tab_setting, selected, onSelect, Modifier.weight(1f))
            }
            Box(
                modifier = Modifier
                    .offset(y = (-22).dp)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(BrandRedDark)
                    .clickable(role = Role.Button, onClick = onCreate),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.DocumentScanner,
                    contentDescription = stringResource(R.string.cd_create_pdf),
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

@Composable
private fun TabItem(
    tab: HomeTab,
    icon: ImageVector,
    label: Int,
    selected: HomeTab,
    onSelect: (HomeTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSelected = tab == selected
    val tint = if (isSelected) BrandRed else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            // Announced as a tab with its selected state; the ripple confirms the tap.
            .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(tab) })
            .padding(top = 4.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .padding(bottom = 4.dp)
                .size(width = 24.dp, height = 3.dp)
                .background(
                    if (isSelected) BrandRed else Color.Transparent,
                    RoundedCornerShape(2.dp),
                ),
        )
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
