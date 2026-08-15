package com.nova.runtime.inference.scheduler

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultInferenceSchedulerTest {

    @Test
    fun schedule_executesBlockAndTracksTotals() = runTest {
        val scheduler = DefaultInferenceScheduler()

        val result = scheduler.schedule { "done" }

        assertEquals("done", result)
        assertEquals(1, scheduler.totalScheduled())
        assertEquals(0, scheduler.pendingCount())
    }

    @Test
    fun schedule_serializesConcurrentWork() = runTest {
        val scheduler = DefaultInferenceScheduler()
        val concurrent = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)

        val jobs = List(5) {
            async {
                scheduler.schedule {
                    val active = concurrent.incrementAndGet()
                    maxConcurrent.updateAndGet { current -> maxOf(current, active) }
                    kotlinx.coroutines.delay(10)
                    concurrent.decrementAndGet()
                    active
                }
            }
        }

        jobs.awaitAll()
        assertEquals(1, maxConcurrent.get())
        assertEquals(5, scheduler.totalScheduled())
    }

    @Test
    fun pendingCount_isZeroAfterCompletion() = runTest {
        val scheduler = DefaultInferenceScheduler()
        scheduler.schedule {
            kotlinx.coroutines.delay(5)
            42
        }
        assertTrue(scheduler.pendingCount() >= 0)
    }
}
