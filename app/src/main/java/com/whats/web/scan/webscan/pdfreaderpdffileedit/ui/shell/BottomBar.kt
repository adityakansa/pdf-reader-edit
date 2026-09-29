package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.shell

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTimeFilled
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.whats.web.scan.webscan.pdfreaderpdffileedit.R
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.BrandRed

enum class HomeTab { HOME, RECENT, SETTING }

/**
 * FR-003, reworked in Step 12a to the three-tab bar of One Read and Document Reader: Home (files and
 * tools), Recent, Settings. Creating moved onto Home's tool grid, so the bar has no raised button.
 */
@Composable
fun BottomBar(selected: HomeTab, onSelect: (HomeTab) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TabItem(HomeTab.HOME, Icons.Filled.Home, Icons.Outlined.Home, R.string.tab_home, selected, onSelect, Modifier.weight(1f))
            TabItem(
                HomeTab.RECENT,
                Icons.Filled.AccessTimeFilled,
                Icons.Outlined.AccessTime,
                R.string.tab_recent,
                selected,
                onSelect,
                Modifier.weight(1f),
            )
            TabItem(
                HomeTab.SETTING,
                Icons.Filled.Settings,
                Icons.Outlined.Settings,
                R.string.tab_setting,
                selected,
                onSelect,
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TabItem(
    tab: HomeTab,
    selectedIcon: ImageVector,
    icon: ImageVector,
    label: Int,
    selected: HomeTab,
    onSelect: (HomeTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSelected = tab == selected
    val tint by animateColorAsState(
        if (isSelected) BrandRed else MaterialTheme.colorScheme.onSurface,
        label = "tabTint",
    )
    Column(
        modifier = modifier
            // Announced as a tab with its selected state; the ripple confirms the tap.
            .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(tab) })
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            if (isSelected) selectedIcon else icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(26.dp),
        )
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
