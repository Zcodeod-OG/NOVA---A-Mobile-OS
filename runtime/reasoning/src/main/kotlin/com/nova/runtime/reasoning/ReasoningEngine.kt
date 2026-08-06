package com.nova.runtime.reasoning

import com.nova.runtime.models.contracts.ReasoningEngineResult
import com.nova.runtime.models.contracts.ReasoningRequest

interface ReasoningEngine {
    suspend fun reason(request: ReasoningRequest): ReasoningEngineResult
}
