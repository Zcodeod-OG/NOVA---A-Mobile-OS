package com.nova.runtime.android.accessibilityAdapter

import com.nova.runtime.models.contracts.CapabilityResult
import com.nova.runtime.utils.logging.StructuredLogger
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityAdapterImplTest {
    private val bridge = AccessibilityServiceBridge()
    private val adapter = AccessibilityAdapterImpl(bridge, StructuredLogger())
    private val traceId = UUID.randomUUID()

    @Test
    fun execute_whenServiceUnavailable_returnsAccessibilityUnavailable() = runTest {
        val result = adapter.execute(AccessibilityOperations.CLICK, emptyMap(), traceId)
        assertTrue(result is CapabilityResult.Failure)
        val failure = result as CapabilityResult.Failure
        assertEquals("ANDROID_ACCESSIBILITY_UNAVAILABLE", failure.error.code)
    }
}
