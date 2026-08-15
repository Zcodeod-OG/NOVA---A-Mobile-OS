package com.nova.runtime.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nova.runtime.app.ui.theme.NovaColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ContentDetailState(
    val text: String,
    val fileName: String? = null,
    val modifiedAtMillis: Long? = null,
    val answerMode: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentDetailSheet(
    state: ContentDetailState?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state == null) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.fileName ?: "Extracted content",
                        style = MaterialTheme.typography.titleMedium,
                        color = NovaColors.textPrimary,
                    )
                    val meta = buildContentMeta(state)
                    if (meta.isNotBlank()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.labelSmall,
                            color = NovaColors.textSecondary,
                        )
                    }
                }
                TextButton(
                    onClick = { copyToClipboard(context, state.text) },
                ) {
                    Text("Copy")
                }
            }

            Text(
                text = state.text,
                style = MaterialTheme.typography.bodyMedium,
                color = NovaColors.textPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            )
        }
    }
}

private fun buildContentMeta(state: ContentDetailState): String {
    val parts = mutableListOf<String>()
    state.modifiedAtMillis?.takeIf { it > 0L }?.let { millis ->
        val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
        parts += "Modified ${CONTENT_DATE_FORMAT.format(date)}"
    }
    parts += "${state.text.length} chars"
    if (state.answerMode == "extract") {
        parts += "extract mode"
    }
    return parts.joinToString(" · ")
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("NOVA content", text))
}

private val CONTENT_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
