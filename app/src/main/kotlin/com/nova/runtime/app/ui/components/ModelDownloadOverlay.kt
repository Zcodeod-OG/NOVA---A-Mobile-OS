package com.nova.runtime.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nova.runtime.ai.model.ModelDownloadPhase
import com.nova.runtime.ai.model.ModelDownloadSessionState
import com.nova.runtime.ai.model.ModelFilePhase
import com.nova.runtime.app.ui.theme.NovaColors

@Composable
fun ModelDownloadOverlay(
    session: ModelDownloadSessionState,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    val showRetry = session.phase == ModelDownloadPhase.FAILED ||
        session.phase == ModelDownloadPhase.OFFLINE
    if (!session.showOverlay && !showRetry) return

    val title = when (session.phase) {
        ModelDownloadPhase.FAILED -> "Download failed"
        ModelDownloadPhase.OFFLINE -> "You're offline"
        else -> "Downloading models"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(16.dp, MaterialTheme.shapes.large)
            .clip(MaterialTheme.shapes.large)
            .background(NovaColors.glassSurface)
            .border(1.dp, NovaColors.glassBorder, MaterialTheme.shapes.large)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = NovaColors.textPrimary,
        )

        session.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = NovaColors.textSecondary,
            )
        }

        LinearProgressIndicator(
            progress = { session.overallProgress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp)),
            color = NovaColors.accent,
            trackColor = NovaColors.surfaceElevated,
        )

        Text(
            text = "${(session.overallProgress * 100).toInt()}% complete",
            style = MaterialTheme.typography.labelMedium,
            color = NovaColors.textSecondary,
        )

        session.files
            .filter { it.phase == ModelFilePhase.DOWNLOADING || it.phase == ModelFilePhase.COPYING }
            .take(2)
            .forEach { file ->
                val total = file.totalBytes?.takeIf { it > 0L }
                val detail = if (total != null) {
                    "${friendlyFileName(file.fileName)} · ${formatMb(file.bytesDownloaded)} of ${formatMb(total)} MB"
                } else {
                    "${friendlyFileName(file.fileName)} · ${formatMb(file.bytesDownloaded)} MB"
                }
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelMedium,
                    color = NovaColors.textSecondary,
                )
            }

        if (session.phase == ModelDownloadPhase.OFFLINE) {
            Text(
                text = "Connect to Wi‑Fi or cellular to continue.",
                style = MaterialTheme.typography.bodyMedium,
                color = NovaColors.textSecondary,
            )
        }

        if (showRetry && onRetry != null) {
            Text(
                text = "Tap to retry",
                style = MaterialTheme.typography.titleMedium,
                color = NovaColors.accent,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(onClick = onRetry)
                    .padding(vertical = 8.dp),
            )
        }
    }
}

private fun friendlyFileName(name: String): String =
    name.substringBeforeLast('.').replace('-', ' ').replace('_', ' ')

private fun formatMb(bytes: Long): String = "%.1f".format(bytes / (1024.0 * 1024.0))
