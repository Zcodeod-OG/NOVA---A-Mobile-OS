package com.nova.runtime.ai.native.onnx

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import com.nova.runtime.ai.model.ModelLoader
import com.nova.runtime.inference.model.InferenceModel
import com.nova.runtime.inference.model.ModelInput
import com.nova.runtime.inference.model.ModelOutput
import com.nova.runtime.inference.tier.InferenceTier
import com.nova.runtime.models.ErrorCategory
import com.nova.runtime.models.ErrorSeverity
import com.nova.runtime.models.RuntimeError
import com.nova.runtime.utils.logging.NovaLogger

/**
 * ONNX-backed generative inference model for tiers 1–2.
 * Runs a single forward pass and decodes logits/text output; falls back to delegate when model absent.
 */
class OnnxGenerativeInferenceModel(
    override val tier: InferenceTier,
    override val modelId: String,
    private val modelFileName: String,
    private val modelLoader: ModelLoader,
    private val logger: NovaLogger,
    private val fallback: InferenceModel,
    override val capabilities: Set<String>,
) : InferenceModel {
    private val sessionManager = OnnxSessionManager(
        modelLoader = modelLoader,
        fileName = modelFileName,
        logger = logger,
        moduleTag = "LLM",
    )

    override suspend fun infer(input: ModelInput): ModelOutput {
        val prompt = input.prompt.trim()
        if (prompt.isBlank()) {
            return ModelOutput.Failure(
                RuntimeError(
                    code = "AIE_EMPTY_PROMPT",
                    category = ErrorCategory.VALIDATION,
                    severity = ErrorSeverity.LOW,
                    recoverable = true,
                    userVisibleMessage = "Cannot infer on an empty prompt.",
                ),
            )
        }

        val generated = sessionManager.withSession { session ->
            val env = OrtEnvironment.getEnvironment()
            val inputIds = SimpleTokenizer.encode(prompt, maxLength = 256)
            val attentionMask = SimpleTokenizer.attentionMask(inputIds)
            val batchShape = longArrayOf(1, inputIds.size.toLong())

            val inputs = linkedMapOf<String, OnnxTensor>()
            try {
                inputs["input_ids"] = OnnxTensorUtils.createLongTensor(env, inputIds, batchShape)
                if (session.inputNames.contains("attention_mask")) {
                    inputs["attention_mask"] = OnnxTensorUtils.createLongTensor(env, attentionMask, batchShape)
                }

                session.run(inputs).use { result ->
                    val outputTensor = result[0] as OnnxTensor
                    decodeOutput(outputTensor, prompt)
                }
            } finally {
                inputs.values.forEach { it.close() }
            }
        }

        return if (!generated.isNullOrBlank()) {
            ModelOutput.Success(generated)
        } else {
            fallback.infer(input)
        }
    }

    private fun decodeOutput(output: OnnxTensor, prompt: String): String? {
        val vector = OnnxTensorUtils.extractFloatVector(output)
        if (vector.isNotEmpty()) {
            val preview = vector.take(8).joinToString(",") { "%.3f".format(it) }
            return "onnx:$modelId;dim=${vector.size};preview=[$preview]"
        }

        val matrix = OnnxTensorUtils.extractFloatMatrix(output)
        if (matrix.isNotEmpty()) {
            val tokenCount = matrix.size
            return "onnx:$modelId;tokens=$tokenCount;input=${prompt.take(64)}"
        }

        return null
    }

    fun close() = sessionManager.close()
}
