package com.nova.runtime.inference.model

import com.nova.runtime.inference.tier.InferenceTier
import com.nova.runtime.models.RuntimeError

data class ModelInput(
    val prompt: String,
    val traceId: String,
    val metadata: Map<String, String> = emptyMap(),
)

sealed class ModelOutput {
    data class Success(val text: String) : ModelOutput()
    data class Failure(val error: RuntimeError) : ModelOutput()
}

interface InferenceModel {
    val tier: InferenceTier
    val modelId: String
    val capabilities: Set<String>

    suspend fun infer(input: ModelInput): ModelOutput
}
