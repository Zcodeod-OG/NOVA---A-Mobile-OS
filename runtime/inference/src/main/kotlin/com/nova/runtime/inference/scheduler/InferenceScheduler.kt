package com.nova.runtime.inference.scheduler

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

enum class InferencePriority(val weight: Int) {
    CRITICAL(0),
    HIGH(1),
    NORMAL(2),
    LOW(3),
}

interface InferenceScheduler {
    suspend fun <T> schedule(
        priority: InferencePriority = InferencePriority.NORMAL,
        block: suspend () -> T,
    ): T

    fun pendingCount(): Int
    fun totalScheduled(): Long
}

/** Serializes inference work on a dedicated logical queue per MSP §5 threading model. */
class DefaultInferenceScheduler : InferenceScheduler {

    private val mutex = Mutex()
    private val pending = AtomicInteger(0)
    private val totalScheduledCount = AtomicLong(0)

    override suspend fun <T> schedule(
        priority: InferencePriority,
        block: suspend () -> T,
    ): T {
        totalScheduledCount.incrementAndGet()
        pending.incrementAndGet()
        return try {
            mutex.withLock { block() }
        } finally {
            pending.decrementAndGet()
        }
    }

    override fun pendingCount(): Int = pending.get()

    override fun totalScheduled(): Long = totalScheduledCount.get()
}
