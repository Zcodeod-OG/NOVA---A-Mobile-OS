package com.nova.runtime.capability.transaction

import com.nova.runtime.capability.model.CapabilityExecutionRequest
import com.nova.runtime.capability.model.CapabilityExecutionResponse
import com.nova.runtime.capability.provider.StubCapabilityProvider
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class CapabilityTransactionTest {

    private val provider = object : StubCapabilityProvider(
        providerId = "tx-provider",
        capabilityType = "device",
        version = "1.0.0",
        operations = setOf("execute", "rollback"),
    ) {
        val rollbackInvocations = mutableListOf<CapabilityExecutionRequest>()

        override suspend fun execute(request: CapabilityExecutionRequest): CapabilityExecutionResponse {
            if (request.operation == "rollback") {
                rollbackInvocations.add(request)
            }
            return super.execute(request)
        }
    }

    @Test
    fun commit_clearsRollbackHooks() = runTest {
        val transaction = DefaultCapabilityTransaction(UUID.randomUUID(), UUID.randomUUID())
        val request = CapabilityExecutionRequest(
            operation = "execute",
            parameters = emptyMap(),
            traceId = UUID.randomUUID(),
        )

        transaction.execute(provider, request)
        transaction.commit()
        transaction.rollback()

        assertTrue(provider.rollbackInvocations.isEmpty())
    }

    @Test
    fun rollback_invokesProviderRollbackHooks() = runTest {
        val transaction = DefaultCapabilityTransaction(UUID.randomUUID(), UUID.randomUUID())
        val traceId = UUID.randomUUID()
        val request = CapabilityExecutionRequest(
            operation = "execute",
            parameters = emptyMap(),
            traceId = traceId,
        )

        val response = transaction.execute(provider, request)
        assertIs<CapabilityExecutionResponse.Success>(response)

        transaction.rollback()

        assertTrue(provider.rollbackInvocations.isNotEmpty())
        assertEquals("rollback", provider.rollbackInvocations.first().operation)
    }

    @Test
    fun execute_attachesTransactionId() = runTest {
        val transactionId = UUID.randomUUID()
        val transaction = DefaultCapabilityTransaction(transactionId, UUID.randomUUID())
        val response = transaction.execute(
            provider,
            CapabilityExecutionRequest(
                operation = "execute",
                parameters = emptyMap(),
                traceId = UUID.randomUUID(),
            ),
        )

        assertIs<CapabilityExecutionResponse.Success>(response)
        assertEquals(transactionId.toString(), response.output["transactionId"])
    }

    @Test
    fun transactionManager_begin_createsTransaction() = runTest {
        val manager = DefaultCapabilityTransactionManager()
        val traceId = UUID.randomUUID()
        val transaction = manager.begin(traceId)

        assertEquals(traceId, transaction.traceId)
    }
}
