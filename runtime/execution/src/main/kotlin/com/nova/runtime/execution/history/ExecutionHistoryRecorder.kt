package com.nova.runtime.execution.history

import java.util.UUID

/** Persists execution history without coupling to Android storage. */
interface ExecutionHistoryRecorder {
    suspend fun recordStart(executionId: UUID, graphId: UUID, traceId: UUID)
    suspend fun recordUpdate(
        executionId: UUID,
        graphId: UUID,
        traceId: UUID,
        status: String,
        durationMs: Long,
        retryCount: Int,
        completedNodes: Int,
        failedNodes: Int,
    )
}

class NoOpExecutionHistoryRecorder : ExecutionHistoryRecorder {
    override suspend fun recordStart(executionId: UUID, graphId: UUID, traceId: UUID) = Unit

    override suspend fun recordUpdate(
        executionId: UUID,
        graphId: UUID,
        traceId: UUID,
        status: String,
        durationMs: Long,
        retryCount: Int,
        completedNodes: Int,
        failedNodes: Int,
    ) = Unit
}
