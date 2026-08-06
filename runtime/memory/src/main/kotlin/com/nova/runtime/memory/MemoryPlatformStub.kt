package com.nova.runtime.memory

import com.nova.runtime.models.ErrorCategory
import com.nova.runtime.models.ErrorSeverity
import com.nova.runtime.models.RuntimeError
import com.nova.runtime.models.contracts.MemoryQuery
import com.nova.runtime.models.contracts.MemoryResult
import com.nova.runtime.storage.coordinator.StorageCoordinator

class MemoryPlatformStub(
    @Suppress("UnusedPrivateProperty") private val storageCoordinator: StorageCoordinator,
) : MemoryPlatform {
    override suspend fun store(entry: Map<String, String>): MemoryResult = notImplemented()
    override suspend fun query(query: MemoryQuery): MemoryResult = notImplemented()
    override suspend fun update(id: String, entry: Map<String, String>): MemoryResult = notImplemented()
    override suspend fun forget(id: String): MemoryResult = notImplemented()
    override suspend fun restore(id: String): MemoryResult = notImplemented()

    private fun notImplemented(): MemoryResult.Failure = MemoryResult.Failure(
        RuntimeError(
            code = "MEMORY_NOT_IMPLEMENTED",
            category = ErrorCategory.INFRASTRUCTURE,
            severity = ErrorSeverity.LOW,
            recoverable = true,
            userVisibleMessage = "Memory platform not yet implemented.",
        ),
    )
}
