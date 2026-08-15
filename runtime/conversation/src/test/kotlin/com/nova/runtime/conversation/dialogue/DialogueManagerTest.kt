package com.nova.runtime.conversation.dialogue

import com.nova.runtime.models.Modality
import com.nova.runtime.models.Observation
import kotlinx.coroutines.test.runTest
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DialogueManagerTest {

    private val manager = DialogueManager()
    private val sessionId = UUID.randomUUID()

    private fun observation(text: String = "hello") = Observation(
        id = UUID.randomUUID(),
        timestamp = Instant.now(),
        sessionId = sessionId,
        traceId = UUID.randomUUID(),
        modality = Modality.TEXT,
        payload = text,
    )

    @Test
    fun beginTurn_incrementsTurnNumbers() = runTest {
        val turn1 = manager.beginTurn(sessionId, observation("one"))
        val turn2 = manager.beginTurn(sessionId, observation("two"))

        assertEquals(1, turn1.turnNumber)
        assertEquals(2, turn2.turnNumber)
        assertEquals(2, manager.turnHistory(sessionId).size)
    }

    @Test
    fun completeTurn_recordsResponse() = runTest {
        val turn = manager.beginTurn(sessionId, observation())
        manager.completeTurn(sessionId, turn.turnNumber, "response")

        val history = manager.turnHistory(sessionId)
        assertEquals("response", history.single().response)
    }

    @Test
    fun cancelInProgressTurn_removesIncompleteTurn() = runTest {
        manager.beginTurn(sessionId, observation())
        assertTrue(manager.hasInProgressTurn(sessionId))

        manager.cancelInProgressTurn(sessionId)
        assertEquals(0, manager.turnHistory(sessionId).size)
    }
}
