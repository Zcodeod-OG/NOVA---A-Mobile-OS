package com.nova.runtime.ai.embedding

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VectorMathTest {

    @Test
    fun cosineSimilarity_identicalVectors_returnsOne() {
        val vector = floatArrayOf(1f, 0f, 0f)
        assertEquals(1f, VectorMath.cosineSimilarity(vector, vector), 0.001f)
    }

    @Test
    fun cosineSimilarity_orthogonalVectors_returnsZero() {
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(0f, 1f)
        assertEquals(0f, VectorMath.cosineSimilarity(a, b), 0.001f)
    }

    @Test
    fun l2Normalize_producesUnitLength() {
        val normalized = VectorMath.l2Normalize(floatArrayOf(3f, 4f))
        val length = kotlin.math.sqrt(normalized.sumOf { (it * it).toDouble() }).toFloat()
        assertEquals(1f, length, 0.001f)
    }

    @Test
    fun meanPool_averagesTokenEmbeddings() {
        val pooled = VectorMath.meanPool(
            arrayOf(
                floatArrayOf(1f, 0f),
                floatArrayOf(-1f, 0f),
            ),
        )
        assertEquals(2, pooled.size)
    }
}

class InMemoryVectorIndexTest {

    @Test
    fun search_returnsHighestSimilarityFirst() {
        val index = InMemoryVectorIndex()
        val idA = UUID.randomUUID()
        val idB = UUID.randomUUID()

        index.insert(idA, floatArrayOf(1f, 0f), mapOf("objectType" to "doc"))
        index.insert(idB, floatArrayOf(0f, 1f), mapOf("objectType" to "doc"))

        val hits = index.search(
            VectorSearchQuery(
                queryVector = floatArrayOf(0.9f, 0.1f),
                k = 2,
            ),
        )

        assertEquals(2, hits.size)
        assertEquals(idA, hits.first().embeddingId)
        assertTrue(hits.first().score >= hits.last().score)
    }

    @Test
    fun search_metadataFilter_limitsResults() {
        val index = InMemoryVectorIndex()
        val matchId = UUID.randomUUID()
        val otherId = UUID.randomUUID()

        index.insert(matchId, floatArrayOf(1f, 0f), mapOf("objectType" to "photo"))
        index.insert(otherId, floatArrayOf(1f, 0f), mapOf("objectType" to "doc"))

        val hits = index.search(
            VectorSearchQuery(
                queryVector = floatArrayOf(1f, 0f),
                k = 5,
                metadataFilter = mapOf("objectType" to "photo"),
            ),
        )

        assertEquals(1, hits.size)
        assertEquals(matchId, hits.first().embeddingId)
    }

    @Test
    fun deleteByObjectId_removesMatchingEntries() {
        val index = InMemoryVectorIndex()
        val objectId = UUID.randomUUID()
        val embeddingId = UUID.randomUUID()

        index.insert(
            embeddingId,
            floatArrayOf(1f, 0f),
            mapOf("objectId" to objectId.toString()),
        )
        index.deleteByObjectId(objectId)

        assertEquals(0, index.size())
    }
}
