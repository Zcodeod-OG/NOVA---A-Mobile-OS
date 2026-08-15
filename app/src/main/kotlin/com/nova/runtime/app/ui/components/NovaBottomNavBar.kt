package com.nova.runtime.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.runtime.app.ui.theme.NovaAkzidenzRed
import com.nova.runtime.app.ui.theme.NovaPureBlack
import com.nova.runtime.app.ui.theme.NovaSurfaceLowest

/**
 * Objective Modernist 5-Tab Bottom Navigation Bar.
 * 2px solid border, 0px corner radius, high-contrast black & Akzidenz Red styling.
 */
@Composable
fun NovaBottomNavBar(
    selectedTab: NovaScreenTab,
    onTabSelected: (NovaScreenTab) -> Unit,
    onActionOrbClick: () -> Unit = {},
    isRecordingVoice: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(NovaSurfaceLowest)
            .border(width = 2.dp, color = NovaPureBlack, shape = RoundedCornerShape(0.dp)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SwissNavItem(
                label = "SYSTEM",
                icon = Icons.Filled.Analytics,
                isSelected = selectedTab == NovaScreenTab.DASHBOARD,
                onClick = { onTabSelected(NovaScreenTab.DASHBOARD) },
                modifier = Modifier.weight(1f),
            )

            SwissNavItem(
                label = "INDEX",
                icon = Icons.Filled.Storage,
                isSelected = selectedTab == NovaScreenTab.DOCUMENTS,
                onClick = { onTabSelected(NovaScreenTab.DOCUMENTS) },
                modifier = Modifier.weight(1f),
            )

            SwissNavItem(
                label = "ACTION",
                icon = Icons.Filled.Bolt,
                isSelected = selectedTab == NovaScreenTab.ACTIONS,
                onClick = { onTabSelected(NovaScreenTab.ACTIONS) },
                modifier = Modifier.weight(1f),
            )

            SwissNavItem(
                label = "COMMAND",
                icon = Icons.Filled.Terminal,
                isSelected = selectedTab == NovaScreenTab.COMMAND,
                onClick = { onTabSelected(NovaScreenTab.COMMAND) },
                modifier = Modifier.weight(1f),
            )

            SwissNavItem(
                label = "CONFIG",
                icon = Icons.Filled.SettingsInputComponent,
                isSelected = selectedTab == NovaScreenTab.SETTINGS,
                onClick = { onTabSelected(NovaScreenTab.SETTINGS) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SwissNavItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor = if (isSelected) NovaAkzidenzRed else NovaSurfaceLowest
    val fgColor = if (isSelected) NovaSurfaceLowest else NovaPureBlack

    Box(
        modifier = modifier
            .height(72.dp)
            .background(bgColor)
            .border(width = 1.dp, color = NovaPureBlack, shape = RoundedCornerShape(0.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = fgColor,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.05.sp,
                ),
                color = fgColor,
            )
        }
    }
}
