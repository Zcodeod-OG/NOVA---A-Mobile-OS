package com.nova.runtime.models.contracts

import com.nova.runtime.models.Nag
import com.nova.runtime.models.RuntimeError
import java.util.UUID

data class ExecutionRequest(
    val graph: Nag,
    val traceId: UUID,
)

sealed class ExecutionResult {
    data class Success(
        val completedNodes: Int,
        /** Optional user-facing message from the last capability node (e.g. WhatsApp auto-send). */
        val userMessage: String? = null,
    ) : ExecutionResult()
    data class Failure(val error: RuntimeError) : ExecutionResult()
}
