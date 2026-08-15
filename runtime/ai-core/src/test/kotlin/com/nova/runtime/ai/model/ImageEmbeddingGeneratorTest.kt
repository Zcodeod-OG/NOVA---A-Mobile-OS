package com.nova.runtime.ai.model

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HashImageEmbeddingGeneratorTest {

    private val generator = HashImageEmbeddingGenerator(dimension = 8)

    @Test
    fun embed_returnsNormalizedVector() = runTest {
        val result = generator.embedImage(byteArrayOf(10, 20, 30, 40, 50))
        assertTrue(result is EmbeddingResult.Success)
        val vector = (result as EmbeddingResult.Success).vector
        assertEquals(8, vector.size)
        val length = kotlin.math.sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()
        assertEquals(1f, length, 0.01f)
    }

    @Test
    fun embed_sameBytes_producesSameVector() = runTest {
        val payload = byteArrayOf(1, 2, 3, 4, 5, 6)
        val first = generator.embedImage(payload)
        val second = generator.embedImage(payload)
        assertTrue(first is EmbeddingResult.Success)
        assertTrue(second is EmbeddingResult.Success)
        assertEquals(
            (first as EmbeddingResult.Success).vector.toList(),
            (second as EmbeddingResult.Success).vector.toList(),
        )
    }

    @Test
    fun embed_emptyPayload_returnsFailure() = runTest {
        val result = generator.embedImage(byteArrayOf())
        assertTrue(result is EmbeddingResult.Failure)
    }
}
