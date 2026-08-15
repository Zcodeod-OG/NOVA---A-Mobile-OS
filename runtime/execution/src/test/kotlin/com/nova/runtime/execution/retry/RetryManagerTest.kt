package com.nova.runtime.execution.retry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RetryManagerTest {

    private val retryManager = DefaultRetryManager(defaultMaxRetries = 3, defaultBackoffMs = 100L)

    @Test
    fun nonePolicy_neverRetries() {
        assertFalse(retryManager.shouldRetry("none", attempt = 0))
        assertEquals(1, retryManager.maxAttempts("none"))
        assertEquals(0L, retryManager.backoffDelayMs("none", attempt = 0))
    }

    @Test
    fun fixedPolicy_retriesUntilMaxAttempts() {
        assertTrue(retryManager.shouldRetry("fixed:2:250", attempt = 0))
        assertTrue(retryManager.shouldRetry("fixed:2:250", attempt = 1))
        assertFalse(retryManager.shouldRetry("fixed:2:250", attempt = 2))
        assertEquals(250L, retryManager.backoffDelayMs("fixed:2:250", attempt = 0))
    }

    @Test
    fun exponentialPolicy_increasesDelayDeterministically() {
        assertEquals(100L, retryManager.backoffDelayMs("exponential:3:100:2", attempt = 0))
        assertEquals(200L, retryManager.backoffDelayMs("exponential:3:100:2", attempt = 1))
        assertEquals(400L, retryManager.backoffDelayMs("exponential:3:100:2", attempt = 2))
    }
}
