package com.nova.runtime.inference.metrics

import com.nova.runtime.inference.tier.InferenceTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DefaultInferenceMetricsTest {

    @Test
    fun snapshot_tracksSuccessFailureAndTierUsage() {
        val metrics = DefaultInferenceMetrics()

        val startedSuccess = metrics.recordStart("trace-1")
        metrics.recordSuccess("trace-1", InferenceTier.LIGHT, startedSuccess)

        val startedFailure = metrics.recordStart("trace-2")
        metrics.recordFailure("trace-2", InferenceTier.FULL, startedFailure)

        val snapshot = metrics.snapshot()

        assertEquals(2, snapshot.totalRequests)
        assertEquals(1, snapshot.successCount)
        assertEquals(1, snapshot.failureCount)
        assertEquals(1, snapshot.tierUsage[InferenceTier.LIGHT])
        assertEquals(1, snapshot.tierUsage[InferenceTier.FULL])
        assertNotNull(snapshot.lastLatencyMs)
        assertTrue(snapshot.averageLatencyMs >= 0.0)
    }
}
