package com.nova.runtime.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.runtime.app.ui.theme.NovaCyanAccent
import com.nova.runtime.app.ui.theme.NovaIndigoAccent
import com.nova.runtime.app.ui.theme.NovaSurfaceDark
import com.nova.runtime.app.ui.theme.NovaSurfaceVariant
import com.nova.runtime.app.ui.theme.NovaTextPrimary
import com.nova.runtime.app.ui.theme.NovaTextSecondary

data class ActivityItem(
    val timestamp: String,
    val source: String,
    val message: String,
    val isAlert: Boolean = false
)

@Composable
fun ActivityStream(
    activities: List<ActivityItem>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(activities.size) {
        if (activities.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "LIVE EVENT & COGNITION STREAM (${activities.size} EVENTS)",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = NovaTextSecondary,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(NovaSurfaceDark)
                .border(1.dp, NovaSurfaceVariant, RoundedCornerShape(14.dp))
                .padding(12.dp)
        ) {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(activities) { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "[${item.timestamp}]",
                            fontSize = 11.sp,
                            color = NovaTextSecondary,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(68.dp)
                        )

                        Text(
                            text = "${item.source}:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (item.isAlert) NovaCyanAccent else NovaIndigoAccent,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(92.dp)
                        )

                        Text(
                            text = item.message,
                            fontSize = 12.sp,
                            color = NovaTextPrimary
                        )
                    }
                }
            }
        }
    }
}
