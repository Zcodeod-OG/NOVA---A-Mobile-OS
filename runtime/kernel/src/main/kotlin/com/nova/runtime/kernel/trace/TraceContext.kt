package com.nova.runtime.kernel.trace

import java.util.UUID

/**
 * Immutable trace context per EMS §11. Propagated across runtime operations.
 */
data class TraceContext(
    val traceId: UUID,
    val correlationId: UUID? = null,
) {
    fun child(): TraceContext = copy(correlationId = traceId)

    companion object {
        fun newRoot(): TraceContext = TraceContext(traceId = UUID.randomUUID())
    }
}

interface TraceIdGenerator {
    fun newTraceId(): UUID
    fun newCorrelationId(): UUID
}

class DefaultTraceIdGenerator : TraceIdGenerator {
    override fun newTraceId(): UUID = UUID.randomUUID()
    override fun newCorrelationId(): UUID = UUID.randomUUID()
}

/**
 * Thread-local trace holder for synchronous call paths.
 * Coroutine-aware propagation uses [TraceContextElement].
 */
class TraceContextHolder(
    private val generator: TraceIdGenerator = DefaultTraceIdGenerator(),
) {
    private val local = ThreadLocal<TraceContext?>()

    fun current(): TraceContext? = local.get()

    fun requireCurrent(): TraceContext =
        current() ?: TraceContext(traceId = generator.newTraceId()).also { set(it) }

    fun set(context: TraceContext) {
        local.set(context)
    }

    fun clear() {
        local.remove()
    }

    fun <T> withContext(context: TraceContext, block: () -> T): T {
        val previous = current()
        set(context)
        return try {
            block()
        } finally {
            if (previous == null) {
                clear()
            } else {
                set(previous)
            }
        }
    }
}
