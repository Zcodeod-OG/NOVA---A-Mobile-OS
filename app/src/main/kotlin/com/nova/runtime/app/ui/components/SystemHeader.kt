package com.nova.runtime.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.runtime.app.ui.theme.NovaCyanAccent
import com.nova.runtime.app.ui.theme.NovaEmeraldGreen
import com.nova.runtime.app.ui.theme.NovaSurfaceDark
import com.nova.runtime.app.ui.theme.NovaSurfaceVariant
import com.nova.runtime.app.ui.theme.NovaTextPrimary
import com.nova.runtime.app.ui.theme.NovaTextSecondary
import com.nova.runtime.models.RuntimeLifecycleState

@Composable
fun SystemHeader(
    lifecycleState: RuntimeLifecycleState,
    activeModel: String = "NOVA-Local-1.0 (Quantized)",
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NovaSurfaceDark)
            .border(1.dp, NovaSurfaceVariant, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                if (lifecycleState == RuntimeLifecycleState.READY) NovaEmeraldGreen else NovaCyanAccent
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "NOVA OS",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = NovaTextPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(NovaSurfaceVariant)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "STATE: ${lifecycleState.name}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (lifecycleState == RuntimeLifecycleState.READY) NovaEmeraldGreen else NovaCyanAccent,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.padding(top = 12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ACTIVE INFERENCE ENGINE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = NovaTextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = activeModel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = NovaCyanAccent
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "MEMORY TELEMETRY",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = NovaTextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "480 MB / 4 GB",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = NovaTextPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
