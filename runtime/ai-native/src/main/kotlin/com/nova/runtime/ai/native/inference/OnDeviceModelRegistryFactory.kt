package com.nova.runtime.ai.native.inference

import com.nova.runtime.ai.model.ModelAssetPaths
import com.nova.runtime.ai.model.ModelLoader
import com.nova.runtime.ai.native.onnx.OnnxGenerativeInferenceModel
import com.nova.runtime.inference.model.DeterministicInferenceModel
import com.nova.runtime.inference.model.InferenceModel
import com.nova.runtime.inference.model.LightweightInferenceModel
import com.nova.runtime.inference.model.RuleEngineInferenceModel
import com.nova.runtime.inference.registry.DefaultModelRegistry
import com.nova.runtime.inference.registry.ModelRegistry
import com.nova.runtime.inference.tier.InferenceTier
import com.nova.runtime.utils.logging.NovaLogger

/** Builds a tiered model registry with ONNX models and placeholder fallbacks. */
class OnDeviceModelRegistryFactory(
    private val modelLoader: ModelLoader,
    private val logger: NovaLogger,
) {
    fun create(): ModelRegistry {
        val deterministic = DeterministicInferenceModel()
        val ruleFallback = RuleEngineInferenceModel()
        val lightweightFallback = LightweightInferenceModel()

        val lightModel = OnnxGenerativeInferenceModel(
            tier = InferenceTier.LIGHT,
            modelId = "onnx-${ModelAssetPaths.LLM_LIGHT_MODEL_VERSION}",
            modelFileName = ModelAssetPaths.LLM_LIGHT_MODEL,
            modelLoader = modelLoader,
            logger = logger,
            fallback = ruleFallback,
            capabilities = setOf("intent_hint", "entity_hint", "constraint_hint", "onnx_inference"),
        )

        val fullModel = OnnxGenerativeInferenceModel(
            tier = InferenceTier.FULL,
            modelId = "onnx-${ModelAssetPaths.LLM_FULL_MODEL_VERSION}",
            modelFileName = ModelAssetPaths.LLM_FULL_MODEL,
            modelLoader = modelLoader,
            logger = logger,
            fallback = lightweightFallback,
            capabilities = setOf("general_inference", "summarization", "reasoning_hint", "onnx_inference"),
        )

        return DefaultModelRegistry(
            initialModels = listOf<InferenceModel>(
                deterministic,
                lightModel,
                fullModel,
            ),
        )
    }
}
