package com.nova.runtime.kernel.trace

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class TraceContextTest {

    @Test
    fun newRoot_generatesUniqueTraceId() {
        val a = TraceContext.newRoot()
        val b = TraceContext.newRoot()
        assertNotEquals(a.traceId, b.traceId)
    }

    @Test
    fun child_setsCorrelationIdToParentTraceId() {
        val root = TraceContext.newRoot()
        val child = root.child()
        assertEquals(root.traceId, child.correlationId)
        assertEquals(root.traceId, child.traceId)
    }

    @Test
    fun holder_withContext_restoresPreviousContext() {
        val holder = TraceContextHolder()
        val outer = TraceContext.newRoot()
        val inner = TraceContext.newRoot()

        holder.withContext(outer) {
            assertEquals(outer, holder.current())
            holder.withContext(inner) {
                assertEquals(inner, holder.current())
            }
            assertEquals(outer, holder.current())
        }
        assertNull(holder.current())
    }
}
