package com.nova.runtime.capability.transaction

import com.nova.runtime.capability.model.CapabilityExecutionRequest
import com.nova.runtime.capability.model.CapabilityExecutionResponse
import com.nova.runtime.capability.provider.CapabilityProvider
import java.util.UUID

/** Transactional capability invocation with commit/rollback hooks — TDD §15. */
interface CapabilityTransaction {
    val transactionId: UUID
    val traceId: UUID

    suspend fun execute(
        provider: CapabilityProvider,
        request: CapabilityExecutionRequest,
    ): CapabilityExecutionResponse

    suspend fun commit()
    suspend fun rollback()
}

class DefaultCapabilityTransaction(
    override val transactionId: UUID,
    override val traceId: UUID,
) : CapabilityTransaction {

    private val rollbackHooks = mutableListOf<suspend () -> Unit>()
    private var committed = false
    private var rolledBack = false

    override suspend fun execute(
        provider: CapabilityProvider,
        request: CapabilityExecutionRequest,
    ): CapabilityExecutionResponse {
        check(!committed && !rolledBack) { "Transaction $transactionId is already finalized" }

        val transactionalRequest = request.copy(transactionId = transactionId)
        val response = provider.execute(transactionalRequest)

        if (response is CapabilityExecutionResponse.Success && request.operation != "rollback") {
            rollbackHooks.add {
                provider.execute(
                    request.copy(
                        operation = "rollback",
                        transactionId = transactionId,
                    ),
                )
            }
        }

        return response
    }

    override suspend fun commit() {
        if (rolledBack) return
        committed = true
        rollbackHooks.clear()
    }

    override suspend fun rollback() {
        if (committed || rolledBack) return
        rolledBack = true
        rollbackHooks.asReversed().forEach { hook -> hook() }
        rollbackHooks.clear()
    }
}

interface CapabilityTransactionManager {
    suspend fun begin(traceId: UUID): CapabilityTransaction
}

class DefaultCapabilityTransactionManager : CapabilityTransactionManager {
    override suspend fun begin(traceId: UUID): CapabilityTransaction =
        DefaultCapabilityTransaction(
            transactionId = UUID.randomUUID(),
            traceId = traceId,
        )
}
