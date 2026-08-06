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
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.runtime.app.ui.theme.NovaCyanAccent
import com.nova.runtime.app.ui.theme.NovaIndigoAccent
import com.nova.runtime.app.ui.theme.NovaSurfaceDark
import com.nova.runtime.app.ui.theme.NovaSurfaceVariant
import com.nova.runtime.app.ui.theme.NovaTextPrimary
import com.nova.runtime.app.ui.theme.NovaTextSecondary

@Composable
fun MobileOsWidgets(
    onPlayMusicClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Weather Widget Card
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0x660E7490),
                            Color(0x661E1B4B)
                        )
                    )
                )
                .border(1.dp, NovaSurfaceVariant, RoundedCornerShape(20.dp))
                .padding(14.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "NEW YORK",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = NovaCyanAccent
                    )
                    Text(text = "☀️", fontSize = 20.sp)
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "28°C",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Light,
                    color = NovaTextPrimary
                )

                Text(
                    text = "Sunny · H:30° L:22°",
                    fontSize = 10.sp,
                    color = NovaTextSecondary
                )
            }
        }

        // 2. Music Player Widget Card
        var isPlaying by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0x66581C87),
                            Color(0x660F172A)
                        )
                    )
                )
                .border(1.dp, NovaSurfaceVariant, RoundedCornerShape(20.dp))
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFFEC4899), Color(0xFF8B5CF6))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🎵", fontSize = 18.sp)
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Midnight City",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NovaTextPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = "M83 · NOVA",
                        fontSize = 9.sp,
                        color = NovaTextSecondary,
                        maxLines = 1
                    )
                }

                Text(
                    text = if (isPlaying) "⏸" else "▶",
                    fontSize = 18.sp,
                    color = NovaCyanAccent,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .clickable {
                            isPlaying = !isPlaying
                            onPlayMusicClick()
                        }
                )
            }
        }
    }
}
