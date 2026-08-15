package com.nova.runtime.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nova.runtime.app.ui.theme.NovaColors
import com.nova.runtime.models.RuntimeLifecycleState
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun SystemHeader(
    lifecycleState: RuntimeLifecycleState,
    indexingStatus: String? = null,
    modifier: Modifier = Modifier,
) {
    var currentTime by remember { mutableStateOf(formatClockTime()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = formatClockTime()
            delay(30_000L)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = currentTime,
                style = MaterialTheme.typography.headlineMedium,
                color = NovaColors.textPrimary,
            )

            Text(
                text = "NOVA",
                style = MaterialTheme.typography.labelMedium,
                color = NovaColors.textSecondary,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusPill(
                label = lifecycleLabel(lifecycleState),
                accentColor = lifecycleAccent(lifecycleState),
                showDot = true,
            )

            if (!indexingStatus.isNullOrBlank()) {
                StatusPill(
                    label = friendlyIndexingStatus(indexingStatus),
                    accentColor = indexingAccent(indexingStatus),
                    showDot = false,
                )
            }
        }
    }
}

@Composable
private fun StatusPill(
    label: String,
    accentColor: androidx.compose.ui.graphics.Color,
    showDot: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(NovaColors.glassSurface)
            .border(1.dp, NovaColors.glassBorder, MaterialTheme.shapes.large)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (showDot) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(accentColor),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun formatClockTime(): String =
    LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm"))

private fun lifecycleLabel(state: RuntimeLifecycleState): String = when (state) {
    RuntimeLifecycleState.READY -> "Ready"
    RuntimeLifecycleState.INITIALIZING -> "Starting up"
    RuntimeLifecycleState.CREATED -> "Starting up"
    RuntimeLifecycleState.STOPPING -> "Shutting down"
    RuntimeLifecycleState.STOPPED -> "Offline"
}

@Composable
private fun lifecycleAccent(state: RuntimeLifecycleState): androidx.compose.ui.graphics.Color = when (state) {
    RuntimeLifecycleState.READY -> NovaColors.success
    RuntimeLifecycleState.STOPPING, RuntimeLifecycleState.STOPPED -> NovaColors.textSecondary
    else -> NovaColors.accent
}

private fun friendlyIndexingStatus(status: String): String {
    val trimmed = status.trim()
    return when {
        trimmed.equals("Ready", ignoreCase = true) -> "Library ready"
        trimmed.equals("Index ready", ignoreCase = true) -> "Library ready"
        trimmed.contains("indexed", ignoreCase = true) -> "Library synced"
        trimmed.contains("Indexing", ignoreCase = true) -> "Syncing library"
        trimmed.length > 28 -> trimmed.take(25) + "…"
        else -> trimmed
    }
}

@Composable
private fun indexingAccent(status: String): androidx.compose.ui.graphics.Color {
    val ready = status.equals("Ready", ignoreCase = true) ||
        status.equals("Index ready", ignoreCase = true) ||
        status.contains("indexed", ignoreCase = true)
    return if (ready) NovaColors.success else NovaColors.accent
}
