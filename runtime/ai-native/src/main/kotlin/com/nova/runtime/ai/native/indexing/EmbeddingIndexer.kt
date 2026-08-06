package com.nova.runtime.ai.native.indexing

import com.nova.runtime.ai.model.EmbeddingGenerator
import com.nova.runtime.ai.model.EmbeddingResult
import com.nova.runtime.ai.model.OcrEngine
import com.nova.runtime.ai.model.OcrResult
import com.nova.runtime.ai.native.storage.CosineVectorIndex
import com.nova.runtime.storage.entities.EmbeddingEntity
import com.nova.runtime.storage.repository.EmbeddingRepository
import java.util.UUID

/** Coordinates OCR, embedding generation, metadata persistence, and vector indexing. */
class EmbeddingIndexer(
    private val embeddingGenerator: EmbeddingGenerator,
    private val embeddingRepository: EmbeddingRepository,
    private val vectorIndex: CosineVectorIndex,
    private val ocrEngine: OcrEngine,
) {
    suspend fun indexText(
        objectId: UUID,
        objectType: String,
        text: String,
    ): UUID? {
        val embeddingId = UUID.randomUUID()
        return when (val result = embeddingGenerator.embed(text)) {
            is EmbeddingResult.Success -> {
                embeddingRepository.insert(
                    EmbeddingEntity(
                        embeddingId = embeddingId,
                        objectType = objectType,
                        objectId = objectId,
                        modelVersion = result.modelVersion,
                        dimension = result.dimension,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
                vectorIndex.insert(
                    embeddingId = embeddingId,
                    vector = result.vector,
                    metadata = mapOf(
                        "objectId" to objectId.toString(),
                        "objectType" to objectType,
                    ),
                )
                embeddingId
            }
            is EmbeddingResult.Failure -> null
        }
    }

    suspend fun indexPhoto(
        objectId: UUID,
        imageBytes: ByteArray,
    ): IndexPhotoResult {
        val ocrResult = ocrEngine.recognize(imageBytes)
        val ocrText = when (ocrResult) {
            is OcrResult.Success -> ocrResult.text
            is OcrResult.Failure -> ""
        }
        val embeddingId = if (ocrText.isNotBlank()) {
            indexText(objectId, "photo", ocrText)
        } else {
            null
        }
        return IndexPhotoResult(ocrText = ocrText.takeIf { it.isNotBlank() }, embeddingId = embeddingId)
    }
}

data class IndexPhotoResult(
    val ocrText: String?,
    val embeddingId: UUID?,
)
