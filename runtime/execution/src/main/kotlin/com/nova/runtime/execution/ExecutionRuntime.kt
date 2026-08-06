package com.nova.runtime.execution

import com.nova.runtime.models.contracts.ExecutionRequest
import com.nova.runtime.models.contracts.ExecutionResult

interface ExecutionRuntime {
    suspend fun execute(request: ExecutionRequest): ExecutionResult
    suspend fun pause(graphId: String)
    suspend fun resume(graphId: String)
    suspend fun cancel(graphId: String)
}
