package com.nova.runtime.execution.worker

import com.nova.runtime.capability.CapabilityFramework
import com.nova.runtime.models.ActionNode
import com.nova.runtime.models.Priority
import com.nova.runtime.models.contracts.CapabilityRequest
import com.nova.runtime.models.contracts.CapabilityResult
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class StubCapabilityActionExecutorTest {

    @Test
    fun execute_failsWhenProviderReturnsStubSuccess() = runTest {
        val framework = StubOnlyFramework()
        val executor = StubCapabilityActionExecutor(framework)
        val node = ActionNode(
            id = UUID.randomUUID(),
            actionType = "execute_capability",
            inputs = mapOf(
                "capabilityType" to "whatsapp",
                "operation" to "send_message",
            ),
            outputs = emptyMap(),
            dependencies = emptyList(),
            timeoutMs = 5_000,
            retryPolicy = "none",
            rollbackPolicy = "none",
            executionPriority = Priority.NORMAL,
        )

        val outcome = executor.execute(node, UUID.randomUUID())

        assertIs<NodeExecutionOutcome.Failure>(outcome)
        assertEquals("CAPABILITY_STUB_ONLY", outcome.error.code)
        assertTrue(outcome.error.userVisibleMessage.contains("WhatsApp"))
    }

    private class StubOnlyFramework : CapabilityFramework {
        override suspend fun execute(request: CapabilityRequest): CapabilityResult =
            CapabilityResult.Success(
                mapOf(
                    "providerId" to "stub-whatsapp",
                    "status" to "stub_executed",
                    "operation" to request.operation,
                ),
            )

        override suspend fun health(capabilityType: String): Boolean = true

        override suspend fun discover(): List<String> = emptyList()
    }
}
