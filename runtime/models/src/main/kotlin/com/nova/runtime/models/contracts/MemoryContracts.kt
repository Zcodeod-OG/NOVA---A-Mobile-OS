package com.nova.runtime.models.contracts

import com.nova.runtime.models.RuntimeError
import java.util.UUID

data class MemoryQuery(
    val queryType: String,
    val parameters: Map<String, String>,
    val retrievalStrategy: String,
    val maxResults: Int,
    val timeoutMs: Long,
    val traceId: UUID,
)

sealed class MemoryResult {
    data class Success(val entries: List<Map<String, String>>) : MemoryResult()
    data class Failure(val error: RuntimeError) : MemoryResult()
}
