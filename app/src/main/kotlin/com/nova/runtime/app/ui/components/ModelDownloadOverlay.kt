package com.nova.runtime.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.runtime.ai.model.ModelDownloadPhase
import com.nova.runtime.ai.model.ModelDownloadSessionState
import com.nova.runtime.ai.model.ModelFilePhase
import com.nova.runtime.app.ui.theme.NovaCyanAccent
import com.nova.runtime.app.ui.theme.NovaSurfaceDark
import com.nova.runtime.app.ui.theme.NovaTextPrimary
import com.nova.runtime.app.ui.theme.NovaTextSecondary

@Composable
fun ModelDownloadOverlay(
    session: ModelDownloadSessionState,
    modifier: Modifier = Modifier,
) {
    if (!session.showOverlay) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NovaSurfaceDark.copy(alpha = 0.95f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "DOWNLOADING LOCAL MODELS",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = NovaCyanAccent,
            fontFamily = FontFamily.Monospace,
        )

        session.message?.let { message ->
            Text(
                text = message,
                fontSize = 12.sp,
                color = NovaTextPrimary,
            )
        }

        LinearProgressIndicator(
            progress = { session.overallProgress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = "${(session.overallProgress * 100).toInt()}% overall",
            fontSize = 10.sp,
            color = NovaTextSecondary,
            fontFamily = FontFamily.Monospace,
        )

        session.files
            .filter { it.phase == ModelFilePhase.DOWNLOADING || it.phase == ModelFilePhase.COPYING }
            .forEach { file ->
                val total = file.totalBytes?.takeIf { it > 0L }
                val detail = if (total != null) {
                    "${file.fileName}: ${formatMb(file.bytesDownloaded)} / ${formatMb(total)} MB"
                } else {
                    "${file.fileName}: ${formatMb(file.bytesDownloaded)} MB"
                }
                Text(
                    text = detail,
                    fontSize = 10.sp,
                    color = NovaTextSecondary,
                    fontFamily = FontFamily.Monospace,
                )
            }

        if (session.phase == ModelDownloadPhase.OFFLINE) {
            Text(
                text = "Connect to Wi‑Fi or cellular to download remaining models.",
                fontSize = 10.sp,
                color = NovaTextSecondary,
            )
        }
    }
}

private fun formatMb(bytes: Long): String = "%.1f".format(bytes / (1024.0 * 1024.0))
