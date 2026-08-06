package com.nova.runtime.understanding

import com.nova.runtime.models.Nir
import com.nova.runtime.models.Observation

interface SemanticUnderstandingPipeline {
    suspend fun process(observation: Observation): Nir?
}
