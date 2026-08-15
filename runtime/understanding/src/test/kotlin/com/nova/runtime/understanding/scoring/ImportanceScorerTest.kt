package com.nova.runtime.understanding.scoring

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImportanceScorerTest {

    private val profile = UserProfileContext(
        priorityTopics = setOf("meetings", "deadlines"),
        keyContacts = setOf("atharv"),
        defaultMeetingMinutes = 30,
        workHoursStart = "09:00",
        workHoursEnd = "17:00",
    )

    @Test
    fun score_boostsKeyContactAndPriorityTopic() {
        val result = ImportanceScorer.score(
            MessageScoreInput(
                body = "Team meeting tomorrow at 3pm — deadline for the proposal",
                sender = "Atharv Sharma",
                channel = "whatsapp",
            ),
            profile,
        )

        assertTrue(result.score >= 0.6f)
        assertEquals("meeting", result.actionType)
        assertTrue(result.title.isNotBlank())
        assertTrue(result.datetime != null)
    }

    @Test
    fun score_detectsDeadlineAction() {
        val result = ImportanceScorer.score(
            MessageScoreInput(
                body = "Please submit the report by Friday",
                sender = "Manager",
                channel = "gmail",
            ),
            profile,
        )

        assertEquals("deadline", result.actionType)
        assertTrue(result.score in 0f..1f)
    }

    @Test
    fun score_lowPriorityMessageStaysModerate() {
        val result = ImportanceScorer.score(
            MessageScoreInput(
                body = "See you later",
                sender = "Friend",
                channel = "whatsapp",
            ),
            profile,
        )

        assertTrue(result.score < 0.5f)
        assertEquals("reminder", result.actionType)
    }
}
