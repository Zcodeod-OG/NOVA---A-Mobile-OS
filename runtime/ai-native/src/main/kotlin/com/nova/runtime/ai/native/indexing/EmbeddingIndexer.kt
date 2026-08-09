package com.nova.runtime.ai.native.indexing

import com.nova.runtime.ai.model.EmbeddingGenerator
import com.nova.runtime.ai.model.EmbeddingResult
import com.nova.runtime.ai.model.ImageEmbeddingGenerator
import com.nova.runtime.ai.model.OcrEngine
import com.nova.runtime.ai.model.OcrResult
import com.nova.runtime.ai.native.storage.CosineVectorIndex
import com.nova.runtime.storage.entities.EmbeddingEntity
import com.nova.runtime.storage.repository.EmbeddingRepository
import java.util.UUID

/** Coordinates OCR, embedding generation, metadata persistence, and vector indexing. */
class EmbeddingIndexer(
    private val embeddingGenerator: EmbeddingGenerator,
    private val imageEmbeddingGenerator: ImageEmbeddingGenerator,
    private val embeddingRepository: EmbeddingRepository,
    private val vectorIndex: CosineVectorIndex,
    private val ocrEngine: OcrEngine,
) {
    suspend fun indexText(
        objectId: UUID,
        objectType: String,
        text: String,
        embeddingKind: String? = null,
        replaceExisting: Boolean = false,
    ): UUID? {
        if (replaceExisting) {
            vectorIndex.deleteByObjectId(objectId)
        }
        val embeddingId = UUID.randomUUID()
        return when (val result = embeddingGenerator.embed(text)) {
            is EmbeddingResult.Success -> {
                persistAndIndex(
                    embeddingId = embeddingId,
                    objectId = objectId,
                    objectType = objectType,
                    vector = result.vector,
                    modelVersion = result.modelVersion,
                    dimension = result.dimension,
                    embeddingKind = embeddingKind,
                )
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

        val ocrEmbeddingId = ocrText
            .takeIf { it.isNotBlank() }
            ?.let { indexText(objectId, OBJECT_TYPE_PHOTO, it, EmbeddingMetadata.KIND_OCR) }

        val imageEmbeddingId = indexImage(objectId, imageBytes)

        return IndexPhotoResult(
            ocrText = ocrText.takeIf { it.isNotBlank() },
            embeddingId = imageEmbeddingId ?: ocrEmbeddingId,
            ocrEmbeddingId = ocrEmbeddingId,
            imageEmbeddingId = imageEmbeddingId,
        )
    }

    private suspend fun indexImage(
        objectId: UUID,
        imageBytes: ByteArray,
    ): UUID? {
        val embeddingId = UUID.randomUUID()
        return when (val result = imageEmbeddingGenerator.embedImage(imageBytes)) {
            is EmbeddingResult.Success -> {
                persistAndIndex(
                    embeddingId = embeddingId,
                    objectId = objectId,
                    objectType = OBJECT_TYPE_PHOTO,
                    vector = result.vector,
                    modelVersion = result.modelVersion,
                    dimension = result.dimension,
                    embeddingKind = EmbeddingMetadata.KIND_IMAGE,
                )
            }
            is EmbeddingResult.Failure -> null
        }
    }

    private suspend fun persistAndIndex(
        embeddingId: UUID,
        objectId: UUID,
        objectType: String,
        vector: FloatArray,
        modelVersion: String,
        dimension: Int,
        embeddingKind: String?,
    ): UUID {
        embeddingRepository.insert(
            EmbeddingEntity(
                embeddingId = embeddingId,
                objectType = objectType,
                objectId = objectId,
                modelVersion = modelVersion,
                dimension = dimension,
                createdAt = System.currentTimeMillis(),
            ),
        )
        vectorIndex.insert(
            embeddingId = embeddingId,
            vector = vector,
            metadata = buildMetadata(objectId, objectType, embeddingKind),
        )
        return embeddingId
    }

    private fun buildMetadata(
        objectId: UUID,
        objectType: String,
        embeddingKind: String?,
    ): Map<String, String> =
        buildMap {
            put(EmbeddingMetadata.OBJECT_ID, objectId.toString())
            put(EmbeddingMetadata.OBJECT_TYPE, objectType)
            if (embeddingKind != null) {
                put(EmbeddingMetadata.EMBEDDING_KIND, embeddingKind)
            }
        }

    companion object {
        const val OBJECT_TYPE_PHOTO = "photo"
    }
}

data class IndexPhotoResult(
    val ocrText: String?,
    /** Primary embedding tracked on [com.nova.runtime.storage.entities.PhotoEntity]. */
    val embeddingId: UUID?,
    val ocrEmbeddingId: UUID? = null,
    val imageEmbeddingId: UUID? = null,
)
