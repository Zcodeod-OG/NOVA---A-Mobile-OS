package com.nova.runtime.conversation.dialogue

import com.nova.runtime.models.Observation
import java.util.UUID

data class DialogueTurn(
    val turnNumber: Int,
    val observation: Observation,
    val response: String? = null,
)

/**
 * Orchestrates multi-turn dialogue within a session.
 * Tracks turn history and determines when interruption is required.
 */
class DialogueManager {
    private val turnsBySession = mutableMapOf<UUID, MutableList<DialogueTurn>>()

    fun beginTurn(sessionId: UUID, observation: Observation): DialogueTurn {
        val turns = turnsBySession.getOrPut(sessionId) { mutableListOf() }
        val turn = DialogueTurn(
            turnNumber = turns.size + 1,
            observation = observation,
        )
        turns.add(turn)
        return turn
    }

    fun completeTurn(sessionId: UUID, turnNumber: Int, response: String) {
        val turns = turnsBySession[sessionId] ?: return
        val index = turns.indexOfFirst { it.turnNumber == turnNumber }
        if (index >= 0) {
            turns[index] = turns[index].copy(response = response)
        }
    }

    fun cancelInProgressTurn(sessionId: UUID) {
        val turns = turnsBySession[sessionId] ?: return
        val last = turns.lastOrNull() ?: return
        if (last.response == null) {
            turns.removeAt(turns.lastIndex)
        }
    }

    fun turnHistory(sessionId: UUID): List<DialogueTurn> =
        turnsBySession[sessionId]?.toList().orEmpty()

    fun clearSession(sessionId: UUID) {
        turnsBySession.remove(sessionId)
    }

    fun hasInProgressTurn(sessionId: UUID): Boolean {
        val last = turnsBySession[sessionId]?.lastOrNull() ?: return false
        return last.response == null
    }
}
