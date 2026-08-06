package com.nova.runtime.ai.native.onnx

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import com.nova.runtime.ai.embedding.VectorMath
import com.nova.runtime.ai.model.EmbeddingGenerator
import com.nova.runtime.ai.model.EmbeddingResult
import com.nova.runtime.ai.model.HashEmbeddingGenerator
import com.nova.runtime.ai.model.ModelAssetPaths
import com.nova.runtime.ai.model.ModelLoader
import com.nova.runtime.utils.logging.NovaLogger

/**
 * ONNX Runtime embedding generator with hash-based fallback when model file is absent.
 * Expects a sentence-transformer style ONNX export (input_ids, attention_mask, token_type_ids).
 */
class OnnxEmbeddingGenerator(
    private val modelLoader: ModelLoader,
    private val logger: NovaLogger,
    private val fallback: EmbeddingGenerator = HashEmbeddingGenerator(),
) : EmbeddingGenerator {
    private val sessionManager = OnnxSessionManager(
        modelLoader = modelLoader,
        fileName = ModelAssetPaths.EMBEDDING_MODEL,
        logger = logger,
        moduleTag = "EMBEDDING",
    )

    override val modelVersion: String
        get() = if (sessionManager.isLoaded) ModelAssetPaths.EMBEDDING_MODEL_VERSION else fallback.modelVersion

    override val dimension: Int = ModelAssetPaths.DEFAULT_EMBEDDING_DIMENSION

    override val isLoaded: Boolean
        get() = sessionManager.isLoaded

    override suspend fun embed(text: String): EmbeddingResult {
        if (text.isBlank()) {
            return EmbeddingResult.Failure("Cannot embed empty text")
        }

        val onnxResult = sessionManager.withSession { session ->
            val env = OrtEnvironment.getEnvironment()
            val inputIds = SimpleTokenizer.encode(text)
            val attentionMask = SimpleTokenizer.attentionMask(inputIds)
            val tokenTypeIds = SimpleTokenizer.tokenTypeIds(inputIds)
            val batchShape = longArrayOf(1, inputIds.size.toLong())

            val inputs = linkedMapOf<String, OnnxTensor>()
            try {
                inputs["input_ids"] = OnnxTensorUtils.createLongTensor(env, inputIds, batchShape)
                inputs["attention_mask"] = OnnxTensorUtils.createLongTensor(env, attentionMask, batchShape)
                inputs["token_type_ids"] = OnnxTensorUtils.createLongTensor(env, tokenTypeIds, batchShape)

                session.run(inputs).use { result ->
                    val outputTensor = result[0] as OnnxTensor
                    val tokenEmbeddings = OnnxTensorUtils.extractFloatMatrix(outputTensor)
                    if (tokenEmbeddings.isEmpty()) {
                        return@withSession null
                    }
                    VectorMath.meanPool(tokenEmbeddings)
                }
            } finally {
                inputs.values.forEach { it.close() }
            }
        }

        return if (onnxResult != null && onnxResult.isNotEmpty()) {
            EmbeddingResult.Success(
                vector = onnxResult,
                modelVersion = ModelAssetPaths.EMBEDDING_MODEL_VERSION,
                dimension = onnxResult.size,
            )
        } else {
            when (val fallbackResult = fallback.embed(text)) {
                is EmbeddingResult.Success -> fallbackResult
                is EmbeddingResult.Failure -> fallbackResult
            }
        }
    }

    fun close() = sessionManager.close()
}
