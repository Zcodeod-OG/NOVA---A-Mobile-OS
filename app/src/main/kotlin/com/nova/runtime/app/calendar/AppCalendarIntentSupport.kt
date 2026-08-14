package com.nova.runtime.app.calendar

import com.nova.runtime.models.Nir
import com.nova.runtime.orchestrator.calendar.CalendarIntentSupport
import com.nova.runtime.storage.profile.ProfilePreferencesStore
import com.nova.runtime.storage.profile.toScoringContext
import com.nova.runtime.storage.repository.MessageRepository
import com.nova.runtime.understanding.scoring.ImportanceScorer
import com.nova.runtime.understanding.scoring.MessageScoreInput
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Android implementation backed by Room message and preference storage. */
class AppCalendarIntentSupport(
    private val messageRepository: MessageRepository,
    private val profileStore: ProfilePreferencesStore,
) : CalendarIntentSupport {
    override suspend fun enrichScheduleNir(nir: Nir): Nir {
        val message = messageRepository.getRecent(1).firstOrNull() ?: return nir
        val profile = profileStore.loadProfile()
        val scored = ImportanceScorer.score(
            MessageScoreInput(
                body = message.body,
                sender = message.sender,
                channel = message.channel,
            ),
            profile.toScoringContext(),
        )

        val startMillis = scored.datetime
        if (startMillis == null) {
            return nir.copy(
                constraints = nir.constraints + mapOf(
                    "messageId" to message.id.toString(),
                    "importanceScore" to scored.score.toString(),
                    "actionType" to scored.actionType,
                    "intentType" to "schedule_from_message",
                ),
            )
        }
        val durationMs = profile.defaultMeetingMinutes.coerceAtLeast(15) * 60_000L

        return nir.copy(
            constraints = nir.constraints + buildMap {
                put("messageId", message.id.toString())
                put("importanceScore", scored.score.toString())
                put("actionType", scored.actionType)
                put("intentType", "schedule_from_message")
                put("title", scored.title)
                put("startTime", startMillis.toString())
                put("endTime", (startMillis + durationMs).toString())
                put("requiresConfirmation", "true")
            },
        )
    }

    override suspend fun buildImportantSummary(maxItems: Int): String {
        val profile = profileStore.loadProfile().toScoringContext()
        val zone = ZoneId.systemDefault()
        val startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val messages = messageRepository.getSince(startOfDay)
        if (messages.isEmpty()) {
            return "No messages ingested yet today. Connect WhatsApp notifications or Gmail to see what's important."
        }

        val ranked = messages
            .map { message ->
                message to ImportanceScorer.score(
                    MessageScoreInput(message.body, message.sender, message.channel),
                    profile,
                )
            }
            .sortedByDescending { (_, scored) -> scored.score }
            .take(maxItems)

        val timeFormat = DateTimeFormatter.ofPattern("h:mm a").withZone(zone)
        return buildString {
            append("Important today (${ranked.size} message")
            if (ranked.size != 1) append('s')
            append("):\n")
            ranked.forEachIndexed { index, (message, scored) ->
                append(index + 1)
                append(". ")
                append(message.sender)
                append(" (")
                append(String.format("%.0f%%", scored.score * 100))
                append(") — ")
                append(message.body.take(120).replace('\n', ' '))
                if (message.body.length > 120) append('…')
                scored.datetime?.let { millis ->
                    append(" [")
                    append(timeFormat.format(Instant.ofEpochMilli(millis)))
                    append(']')
                }
                append('\n')
            }
        }.trimEnd()
    }

    override fun buildConfirmationPrompt(nir: Nir): String {
        val title = nir.constraints["title"] ?: "Calendar event"
        val start = nir.constraints["startTime"]?.toLongOrNull()
        val zone = ZoneId.systemDefault()
        val whenLabel = start?.let {
            DateTimeFormatter.ofPattern("EEE MMM d, h:mm a")
                .withZone(zone)
                .format(Instant.ofEpochMilli(it))
        } ?: "the proposed time"
        return "Add \"$title\" on $whenLabel? Reply \"confirm\" to create the event."
    }
}
