package com.nova.runtime.ai.native.onnx

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import com.nova.runtime.ai.embedding.VectorMath
import com.nova.runtime.ai.model.EmbeddingGenerator
import com.nova.runtime.ai.model.EmbeddingResult
import com.nova.runtime.ai.model.ModelAssetPaths
import com.nova.runtime.ai.model.ModelLoader
import com.nova.runtime.ai.native.tokenizer.MiniLmTokenizer
import com.nova.runtime.utils.logging.NovaLogger

/**
 * ONNX Runtime embedding generator using real MiniLM BERT WordPiece tokenization.
 *
 * When the ONNX model or tokenizer assets are unavailable, returns [EmbeddingResult.Failure]
 * instead of silently falling back to hash embeddings.
 */
class OnnxEmbeddingGenerator(
    private val modelLoader: ModelLoader,
    private val tokenizer: MiniLmTokenizer,
    private val logger: NovaLogger,
) : EmbeddingGenerator {
    private val sessionManager = OnnxSessionManager(
        modelLoader = modelLoader,
        fileName = ModelAssetPaths.EMBEDDING_MODEL,
        logger = logger,
        moduleTag = "EMBEDDING",
    )

    override val modelVersion: String
        get() = ModelAssetPaths.EMBEDDING_MODEL_VERSION

    override val dimension: Int = ModelAssetPaths.DEFAULT_EMBEDDING_DIMENSION

    override val isLoaded: Boolean
        get() = sessionManager.isLoaded && tokenizer.isLoaded

    override suspend fun embed(text: String): EmbeddingResult {
        if (text.isBlank()) {
            return EmbeddingResult.Failure("Cannot embed empty text")
        }

        if (!tokenizer.isLoaded) {
            val reason = tokenizer.unavailableReason ?: "MiniLM tokenizer assets unavailable"
            logger.warn("EMBEDDING", "Tokenizer unavailable: $reason")
            return EmbeddingResult.Failure(
                message = "Embedding tokenizer unavailable: $reason",
                recoverable = true,
            )
        }

        val tokenized = runCatching { tokenizer.tokenize(text) }
            .getOrElse { error ->
                logger.warn("EMBEDDING", "Tokenization failed: ${error.message}")
                return EmbeddingResult.Failure(
                    message = "Tokenization failed: ${error.message}",
                    recoverable = true,
                )
            }

        val onnxResult = sessionManager.withSession { session ->
            val env = OrtEnvironment.getEnvironment()
            val inputIds = tokenized.inputIds
            val attentionMask = tokenized.attentionMask
            val tokenTypeIds = tokenized.tokenTypeIds
            val batchShape = longArrayOf(1, inputIds.size.toLong())

            val inputs = linkedMapOf<String, OnnxTensor>()
            try {
                inputs["input_ids"] = OnnxTensorUtils.createLongTensor(env, inputIds, batchShape)
                inputs["attention_mask"] = OnnxTensorUtils.createLongTensor(env, attentionMask, batchShape)
                inputs["token_type_ids"] = OnnxTensorUtils.createLongTensor(env, tokenTypeIds, batchShape)

                session.run(inputs).use { result ->
                    val outputTensor = result[0] as OnnxTensor
                    val tokenEmbeddings = OnnxTensorUtils.extractFloatMatrix(outputTensor)
                    when {
                        tokenEmbeddings.isNotEmpty() -> VectorMath.meanPool(tokenEmbeddings)
                        else -> {
                            val pooled = OnnxTensorUtils.extractFloatVector(outputTensor)
                            if (pooled.isEmpty()) null else VectorMath.l2Normalize(pooled)
                        }
                    }
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
            logger.warn(
                "EMBEDDING",
                "ONNX embedding model unavailable or inference failed; semantic search requires ${ModelAssetPaths.EMBEDDING_MODEL}",
            )
            EmbeddingResult.Failure(
                message = "ONNX embedding model unavailable or inference failed",
                recoverable = true,
            )
        }
    }

    suspend fun preWarm() {
        sessionManager.ensureLoaded()
        embed("nova warmup")
    }

    fun close() = sessionManager.close()
}
