package com.nova.runtime.ai.native.indexing

import com.nova.runtime.ai.native.storage.CosineVectorIndex
import com.nova.runtime.models.RuntimeModule
import com.nova.runtime.storage.repository.EmbeddingRepository
import com.nova.runtime.storage.vector.VectorBlobCodec
import com.nova.runtime.utils.logging.NovaLogger
import java.util.UUID

/** Loads persisted embedding vectors from Room into [CosineVectorIndex] at startup. */
class VectorIndexHydrator(
    private val embeddingRepository: EmbeddingRepository,
    private val vectorIndex: CosineVectorIndex,
    private val logger: NovaLogger,
) {
    suspend fun hydrateFromDatabase() {
        val entities = embeddingRepository.listWithPersistedVectors()
        if (entities.isEmpty()) return

        var loaded = 0
        var skipped = 0
        for (entity in entities) {
            val blob = entity.vectorBlob ?: continue
            val vector = VectorBlobCodec.decode(blob, entity.dimension)
            if (vector == null) {
                skipped++
                continue
            }
            vectorIndex.insert(
                embeddingId = entity.embeddingId,
                vector = vector,
                metadata = buildMetadata(entity.objectId, entity.objectType, entity.embeddingKind),
            )
            loaded++
        }

        logger.info(
            module = RuntimeModule.STORAGE.name,
            message = "Vector index hydrated from Room",
            metadata = mapOf(
                "loaded" to loaded.toString(),
                "skipped" to skipped.toString(),
                "candidates" to entities.size.toString(),
            ),
        )
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
}
