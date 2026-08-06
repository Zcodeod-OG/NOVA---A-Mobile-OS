package com.nova.runtime.understanding

import com.nova.runtime.inference.AdaptiveInferenceEngine
import com.nova.runtime.models.Nir
import com.nova.runtime.models.Observation

class SemanticUnderstandingPipelineStub(
    private val inferenceEngine: AdaptiveInferenceEngine,
) : SemanticUnderstandingPipeline {
    override suspend fun process(observation: Observation): Nir? {
        // TODO(Sprint 1): SUP stages — normalization through NIR generation
        return null
    }
}
