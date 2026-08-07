package com.nova.runtime.ai.native.onnx

import ai.onnxruntime.OnnxTensor
import com.nova.runtime.ai.embedding.VectorMath
import com.nova.runtime.ai.model.EmbeddingResult
import com.nova.runtime.ai.model.HashImageEmbeddingGenerator
import com.nova.runtime.ai.model.ImageEmbeddingGenerator
import com.nova.runtime.ai.model.ModelAssetPaths
import com.nova.runtime.ai.model.ModelLoader
import com.nova.runtime.utils.logging.NovaLogger

/**
 * ONNX Runtime image embedding generator with hash-based fallback when model file is absent.
 *
 * Expects a MobileCLIP-S0 image encoder export:
 * - Input: pixel_values float32 [1, 3, 224, 224] (NCHW, ImageNet-normalized)
 * - Output: image_embeds float32 [1, 512]
 */
class OnnxImageEmbeddingGenerator(
    private val modelLoader: ModelLoader,
    private val logger: NovaLogger,
    private val fallback: ImageEmbeddingGenerator = HashImageEmbeddingGenerator(),
) : ImageEmbeddingGenerator {
    private val sessionManager = OnnxSessionManager(
        modelLoader = modelLoader,
        fileName = ModelAssetPaths.IMAGE_EMBEDDING_MODEL,
        logger = logger,
        moduleTag = "IMAGE_EMBEDDING",
    )

    override val modelVersion: String
        get() = if (sessionManager.isLoaded) {
            ModelAssetPaths.IMAGE_EMBEDDING_MODEL_VERSION
        } else {
            fallback.modelVersion
        }

    override val dimension: Int = ModelAssetPaths.DEFAULT_IMAGE_EMBEDDING_DIMENSION

    override val isLoaded: Boolean
        get() = sessionManager.isLoaded

    override suspend fun embedImage(imageBytes: ByteArray): EmbeddingResult {
        if (imageBytes.isEmpty()) {
            return EmbeddingResult.Failure("Cannot embed empty image payload")
        }

        val preprocessed = when (val result = ImagePreprocessor.preprocess(imageBytes)) {
            is ImagePreprocessor.Result.Success -> result
            is ImagePreprocessor.Result.Failure -> return EmbeddingResult.Failure(result.message)
        }

        val onnxResult = sessionManager.withSession { session ->
            val env = ai.onnxruntime.OrtEnvironment.getEnvironment()
            val inputs = linkedMapOf<String, OnnxTensor>()
            try {
                inputs[resolveInputName(session)] = OnnxTensorUtils.createFloatTensor(
                    env = env,
                    values = preprocessed.data,
                    shape = preprocessed.shape,
                )

                session.run(inputs).use { result ->
                    val outputTensor = result[0] as OnnxTensor
                    val vector = OnnxTensorUtils.extractFloatVector(outputTensor)
                    if (vector.isEmpty()) {
                        return@withSession null
                    }
                    VectorMath.l2Normalize(vector)
                }
            } finally {
                inputs.values.forEach { it.close() }
            }
        }

        return if (onnxResult != null && onnxResult.isNotEmpty()) {
            EmbeddingResult.Success(
                vector = onnxResult,
                modelVersion = ModelAssetPaths.IMAGE_EMBEDDING_MODEL_VERSION,
                dimension = onnxResult.size,
            )
        } else {
            when (val fallbackResult = fallback.embedImage(imageBytes)) {
                is EmbeddingResult.Success -> fallbackResult
                is EmbeddingResult.Failure -> fallbackResult
            }
        }
    }

    override suspend fun embedQuery(text: String): EmbeddingResult = fallback.embedQuery(text)

    private fun resolveInputName(session: ai.onnxruntime.OrtSession): String {
        val inputNames = session.inputNames
        return when {
            "pixel_values" in inputNames -> "pixel_values"
            "input" in inputNames -> "input"
            else -> inputNames.first()
        }
    }

    fun close() = sessionManager.close()
}
