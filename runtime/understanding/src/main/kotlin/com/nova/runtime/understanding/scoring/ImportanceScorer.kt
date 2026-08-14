package com.nova.runtime.understanding.scoring

import com.nova.runtime.understanding.time.NaturalLanguageTimeParser

data class MessageScoreInput(
    val body: String,
    val sender: String,
    val channel: String = "unknown",
)

data class UserProfileContext(
    val priorityTopics: Set<String>,
    val keyContacts: Set<String>,
    val defaultMeetingMinutes: Int,
    val workHoursStart: String?,
    val workHoursEnd: String?,
)

data class ImportanceScoreResult(
    val score: Float,
    val datetime: Long?,
    val title: String,
    val actionType: String,
)

/** Scores message importance and extracts scheduling hints from body text. */
object ImportanceScorer {
    private val URGENCY_WORDS = setOf(
        "urgent", "asap", "important", "deadline", "due", "meeting", "call", "tomorrow", "today",
    )
    private val MEETING_WORDS = setOf("meeting", "call", "sync", "standup", "stand-up", "interview")
    private val DEADLINE_WORDS = setOf("deadline", "due", "submit", "deliver", "expires")

    fun score(
        input: MessageScoreInput,
        profile: UserProfileContext,
        nowMillis: Long = System.currentTimeMillis(),
    ): ImportanceScoreResult {
        val lowerBody = input.body.lowercase()
        val lowerSender = input.sender.lowercase()

        var score = 0.3f
        if (profile.keyContacts.any { contact -> contact in lowerSender || lowerSender.contains(contact) }) {
            score += 0.2f
        }
        val topicHits = profile.priorityTopics.count { topic -> topic in lowerBody }
        score += (topicHits * 0.12f).coerceAtMost(0.36f)
        if (URGENCY_WORDS.any { word -> word in lowerBody }) {
            score += 0.15f
        }

        val parsedEvent = NaturalLanguageTimeParser.parseCalendarEvent(input.body, java.time.Instant.ofEpochMilli(nowMillis))
        if (parsedEvent != null) {
            score += 0.1f
        }

        val actionType = detectActionType(lowerBody)
        val title = extractTitle(input.body, input.sender, actionType)
        val datetime = parsedEvent?.startTime

        return ImportanceScoreResult(
            score = score.coerceIn(0f, 1f),
            datetime = datetime,
            title = title,
            actionType = actionType,
        )
    }

    private fun detectActionType(lowerBody: String): String = when {
        MEETING_WORDS.any { it in lowerBody } -> "meeting"
        DEADLINE_WORDS.any { it in lowerBody } -> "deadline"
        else -> "reminder"
    }

    private fun extractTitle(body: String, sender: String, actionType: String): String {
        val firstLine = body.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        if (firstLine.length in 4..80) return firstLine
        return when (actionType) {
            "meeting" -> "Meeting with $sender"
            "deadline" -> "Deadline from $sender"
            else -> "Reminder from $sender"
        }
    }
}
