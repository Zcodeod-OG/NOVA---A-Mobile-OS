package com.nova.runtime.memory

import com.nova.runtime.models.contracts.MemoryQuery
import com.nova.runtime.models.contracts.MemoryResult

interface MemoryPlatform {
    suspend fun store(entry: Map<String, String>): MemoryResult
    suspend fun query(query: MemoryQuery): MemoryResult
    suspend fun update(id: String, entry: Map<String, String>): MemoryResult
    suspend fun forget(id: String): MemoryResult
    suspend fun restore(id: String): MemoryResult
}
