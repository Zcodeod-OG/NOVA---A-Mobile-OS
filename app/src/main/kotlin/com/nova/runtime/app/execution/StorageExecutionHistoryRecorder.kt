package com.nova.runtime.app.execution

import com.nova.runtime.execution.history.ExecutionHistoryRecorder
import com.nova.runtime.storage.entities.ExecutionHistoryEntity
import com.nova.runtime.storage.repository.ExecutionHistoryRepository
import java.util.UUID

/** Bridges execution runtime to Room-backed execution history. */
class StorageExecutionHistoryRecorder(
    private val repository: ExecutionHistoryRepository,
) : ExecutionHistoryRecorder {

    override suspend fun recordStart(executionId: UUID, graphId: UUID, traceId: UUID) {
        repository.insert(
            ExecutionHistoryEntity(
                id = executionId,
                graphId = graphId,
                traceId = traceId,
                status = "running",
                duration = 0L,
                retryCount = 0,
                completedNodes = 0,
                failedNodes = 0,
            ),
        )
    }

    override suspend fun recordUpdate(
        executionId: UUID,
        graphId: UUID,
        traceId: UUID,
        status: String,
        durationMs: Long,
        retryCount: Int,
        completedNodes: Int,
        failedNodes: Int,
    ) {
        repository.update(
            ExecutionHistoryEntity(
                id = executionId,
                graphId = graphId,
                traceId = traceId,
                status = status,
                duration = durationMs,
                retryCount = retryCount,
                completedNodes = completedNodes,
                failedNodes = failedNodes,
            ),
        )
    }
}
