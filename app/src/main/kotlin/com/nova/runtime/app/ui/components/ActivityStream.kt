package com.nova.runtime.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nova.runtime.app.ui.theme.NovaColors

data class ActivityItem(
    val timestamp: String,
    val source: String,
    val message: String,
    val isAlert: Boolean = false,
    /** When set, later items with the same key replace this row in-place (live progress). */
    val replaceKey: String? = null,
    val fullContent: String? = null,
    val answerMode: String? = null,
    val sourceFileName: String? = null,
    val sourceModifiedAtMillis: Long? = null,
    val expandable: Boolean = false,
)

@Composable
fun ActivityStream(
    activities: List<ActivityItem>,
    onExpandContent: (ContentDetailState) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Recent",
            style = MaterialTheme.typography.titleMedium,
            color = NovaColors.textPrimary,
            modifier = Modifier.padding(start = 4.dp),
        )

        if (activities.isEmpty()) {
            EmptyActivityState(modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(
                    items = activities,
                    key = { item -> item.replaceKey ?: "${item.timestamp}-${item.source}-${item.message}" },
                ) { item ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + slideInVertically { it / 3 },
                    ) {
                        ActivityCard(
                            item = item,
                            onExpandContent = onExpandContent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyActivityState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(NovaColors.glassSurface)
            .border(1.dp, NovaColors.glassBorder, MaterialTheme.shapes.large)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Nothing here yet",
                style = MaterialTheme.typography.titleMedium,
                color = NovaColors.textPrimary,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Ask a question or give a command to get started.",
                style = MaterialTheme.typography.bodyMedium,
                color = NovaColors.textSecondary,
            )
        }
    }
}

@Composable
private fun ActivityCard(
    item: ActivityItem,
    onExpandContent: (ContentDetailState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isExtract = item.answerMode == "extract"
    val displayText = if (isExtract && !item.fullContent.isNullOrBlank()) {
        item.fullContent
    } else {
        friendlyMessage(item.message)
    }
    val canExpand = item.expandable && !item.fullContent.isNullOrBlank()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(NovaColors.glassSurface)
            .border(1.dp, NovaColors.glassBorder, MaterialTheme.shapes.large)
            .then(
                if (canExpand) {
                    Modifier.clickable {
                        onExpandContent(
                            ContentDetailState(
                                text = item.fullContent.orEmpty(),
                                fileName = item.sourceFileName,
                                modifiedAtMillis = item.sourceModifiedAtMillis,
                                answerMode = item.answerMode,
                            ),
                        )
                    }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SourceLabel(
                source = item.source,
                isAlert = item.isAlert,
            )
            Text(
                text = item.timestamp,
                style = MaterialTheme.typography.labelSmall,
                color = androidx.compose.ui.graphics.Color(0xFFDDDDDD),
            )
        }

        Text(
            text = displayText,
            style = MaterialTheme.typography.bodyMedium,
            color = if (item.isAlert || item.message.startsWith("FAILED", ignoreCase = true)) {
                NovaColors.error
            } else {
                androidx.compose.ui.graphics.Color.White
            },
            maxLines = if (isExtract || canExpand) Int.MAX_VALUE else 6,
            overflow = TextOverflow.Ellipsis,
        )
        if (canExpand && !isExtract) {
            Text(
                text = "Tap to view full content",
                style = MaterialTheme.typography.labelSmall,
                color = NovaColors.accent,
            )
        }
    }
}

@Composable
private fun SourceLabel(
    source: String,
    isAlert: Boolean,
    modifier: Modifier = Modifier,
) {
    val label = friendlySource(source)
    val tint = when {
        isAlert -> NovaColors.error
        label.equals("Answer", ignoreCase = true) -> NovaColors.success
        else -> NovaColors.accent
    }

    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
    }
}

private fun friendlySource(source: String): String = when (source.uppercase()) {
    "KERNEL" -> "System"
    "SUP", "UNDERSTANDING" -> "Understanding"
    "REASONING" -> "Reasoning"
    "PLANNER" -> "Planning"
    "EXECUTION" -> "Actions"
    "STORAGE" -> "Library"
    "CAPABILITY" -> "Answer"
    "INFERENCE" -> "Assistant"
    else -> source.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }
}

private fun friendlyMessage(message: String): String {
    val trimmed = message.trim()
    return when {
        trimmed.startsWith("FAILED:", ignoreCase = true) ->
            trimmed.removePrefix("FAILED:").removePrefix("failed:").trim()
        trimmed.contains("NIR generated", ignoreCase = true) ->
            "Understood your request"
        trimmed.contains("Action graph built", ignoreCase = true) ->
            "Plan ready"
        trimmed.contains("Graph execution completed", ignoreCase = true) ->
            "Done"
        trimmed.contains("Runtime lifecycle transitioned to READY", ignoreCase = true) ->
            "NOVA is ready"
        trimmed.contains("Runtime modules initializing", ignoreCase = true) ->
            "Starting NOVA…"
        else -> trimmed
    }
}
