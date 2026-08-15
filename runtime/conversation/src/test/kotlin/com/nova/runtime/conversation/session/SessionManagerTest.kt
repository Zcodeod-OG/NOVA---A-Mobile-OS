package com.nova.runtime.conversation.session

import com.nova.runtime.conversation.state.ConversationState
import com.nova.runtime.error.NovaException
import com.nova.runtime.kernel.trace.DefaultTraceIdGenerator
import com.nova.runtime.kernel.trace.TraceContextHolder
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SessionManagerTest {

    private val persistence = InMemorySessionPersistence()
    private val traceContextHolder = TraceContextHolder(DefaultTraceIdGenerator())
    private val manager = SessionManager(persistence, traceContextHolder, DefaultTraceIdGenerator())

    @Test
    fun createSession_persistsActiveSession() = runTest {
        val session = manager.createSession()
        assertTrue(session.isActive)
        assertEquals(ConversationState.IDLE, session.state)

        val loaded = persistence.getById(session.sessionId)
        assertNotNull(loaded)
        assertEquals(session.sessionId, loaded.sessionId)
    }

    @Test
    fun endSession_marksSessionEnded() = runTest {
        val session = manager.createSession()
        val ended = manager.endSession(session.sessionId)

        assertNotNull(ended.endedAt)
        assertEquals(ConversationState.IDLE, ended.state)
    }

    @Test
    fun resumeSession_returnsExistingActiveSession() = runTest {
        val session = manager.createSession()
        val resumed = manager.resumeSession(session.sessionId)
        assertEquals(session.sessionId, resumed.sessionId)
    }

    @Test
    fun resumeSession_createsNewWhenNoneActive() = runTest {
        val session = manager.resumeSession()
        assertNotNull(session.sessionId)
    }

    @Test
    fun resumeSession_failsForEndedSession() = runTest {
        val session = manager.createSession()
        manager.endSession(session.sessionId)

        assertFailsWith<NovaException> {
            manager.resumeSession(session.sessionId)
        }
    }

    @Test
    fun incrementTurn_updatesTurnCount() = runTest {
        val session = manager.createSession()
        val updated = manager.incrementTurn(session.sessionId)
        assertEquals(1, updated.turnCount)
    }
}
