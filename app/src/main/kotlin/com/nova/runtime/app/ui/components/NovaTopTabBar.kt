package com.nova.runtime.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.runtime.app.ui.theme.NovaAkzidenzRed
import com.nova.runtime.app.ui.theme.NovaPureBlack
import com.nova.runtime.app.ui.theme.NovaSurfaceLowest

/**
 * Objective Modernist Top App Bar & Sub-Nav Header.
 * 2px solid border, Inter bold uppercase typography, Akzidenz Red accents.
 */
@Composable
fun NovaTopTabBar(
    selectedTab: NovaScreenTab,
    onTabSelected: (NovaScreenTab) -> Unit,
    modifier: Modifier = Modifier,
    onProfileClick: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(NovaSurfaceLowest)
            .border(width = 2.dp, color = NovaPureBlack, shape = RoundedCornerShape(0.dp)),
    ) {
        // Main Top Bar Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onProfileClick() },
            ) {
                Icon(
                    imageVector = Icons.Filled.GridView,
                    contentDescription = "Grid View",
                    tint = NovaAkzidenzRed,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "NOVA",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.02).sp,
                    ),
                    color = NovaPureBlack,
                )
            }

            Icon(
                imageVector = Icons.Filled.Terminal,
                contentDescription = "Terminal",
                tint = NovaAkzidenzRed,
                modifier = Modifier
                    .size(28.dp)
                    .clickable { onTabSelected(NovaScreenTab.COMMAND) },
            )
        }

        // Sub-Navigation Tabs Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = NovaPureBlack, shape = RoundedCornerShape(0.dp)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tabs = listOf(
                NovaScreenTab.DASHBOARD to "DASHBOARD",
                NovaScreenTab.DOCUMENTS to "DOCUMENTS",
                NovaScreenTab.ACTIONS to "ACTIONS",
                NovaScreenTab.COMMAND to "COMMAND",
                NovaScreenTab.SETTINGS to "SETTINGS",
            )

            tabs.forEach { (tab, label) ->
                val isSelected = selectedTab == tab
                val bottomBorderWidth = if (isSelected) 4.dp else 0.dp
                val textColor = if (isSelected) NovaPureBlack else Color(0xFF5E3F3B)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onTabSelected(tab) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                letterSpacing = 0.05.sp,
                            ),
                            color = textColor,
                        )
                        if (isSelected) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(bottomBorderWidth)
                                    .background(NovaAkzidenzRed),
                            )
                        }
                    }
                }
            }
        }
    }
}
