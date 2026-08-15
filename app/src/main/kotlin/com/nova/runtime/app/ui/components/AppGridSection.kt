package com.nova.runtime.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.runtime.app.ui.theme.NovaSurfaceDark
import com.nova.runtime.app.ui.theme.NovaSurfaceVariant
import com.nova.runtime.app.ui.theme.NovaTextSecondary

data class MobileAppInfo(
    val name: String,
    val icon: String,
    val gradientColors: List<Color>
)

@Composable
fun AppGridSection(
    onAppClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val apps = listOf(
        MobileAppInfo("Phone", "📞", listOf(Color(0xFF047857), Color(0xFF10B981))),
        MobileAppInfo("Messages", "💬", listOf(Color(0xFF0284C7), Color(0xFF06B6D4))),
        MobileAppInfo("Browser", "🌐", listOf(Color(0xFF4338CA), Color(0xFF6366F1))),
        MobileAppInfo("Camera", "📷", listOf(Color(0xFFBE123C), Color(0xFFF43F5E))),
        MobileAppInfo("YouTube", "▶️", listOf(Color(0xFFDC2626), Color(0xFFEF4444))),
        MobileAppInfo("Spotify", "🎧", listOf(Color(0xFF059669), Color(0xFF34D399))),
        MobileAppInfo("Gmail", "✉️", listOf(Color(0xFFB45309), Color(0xFFF59E0B))),
        MobileAppInfo("Settings", "⚙️", listOf(Color(0xFF6B21A8), Color(0xFFA855F7)))
    )

    Column(modifier = modifier) {
        // App Grid (4 Columns)
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(apps) { app ->
                AppGridItem(app = app, onClick = { onAppClick(app.name) })
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Dock Bar
        DockBar(onAppClick = onAppClick)
    }
}

@Composable
fun AppGridItem(
    app: MobileAppInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(brush = Brush.linearGradient(colors = app.gradientColors))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = app.icon, fontSize = 20.sp)
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = app.name,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = NovaTextSecondary,
            maxLines = 1
        )
    }
}

@Composable
fun DockBar(
    onAppClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val dockApps = listOf(
        MobileAppInfo("Phone", "📞", listOf(Color(0xFF047857), Color(0xFF10B981))),
        MobileAppInfo("Messages", "💬", listOf(Color(0xFF0284C7), Color(0xFF06B6D4))),
        MobileAppInfo("Browser", "🌐", listOf(Color(0xFF4338CA), Color(0xFF6366F1))),
        MobileAppInfo("Camera", "📷", listOf(Color(0xFFBE123C), Color(0xFFF43F5E)))
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(NovaSurfaceDark)
            .border(1.dp, NovaSurfaceVariant, RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            dockApps.forEach { app ->
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(brush = Brush.linearGradient(colors = app.gradientColors))
                        .clickable { onAppClick(app.name) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = app.icon, fontSize = 18.sp)
                }
            }
        }
    }
}
