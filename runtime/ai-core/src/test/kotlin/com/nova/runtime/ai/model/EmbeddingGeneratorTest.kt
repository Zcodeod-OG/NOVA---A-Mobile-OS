package com.nova.runtime.ai.model

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HashEmbeddingGeneratorTest {

    private val generator = HashEmbeddingGenerator(dimension = 8)

    @Test
    fun embed_returnsNormalizedVector() = runTest {
        val result = generator.embed("hello world")
        assertTrue(result is EmbeddingResult.Success)
        val vector = (result as EmbeddingResult.Success).vector
        assertEquals(8, vector.size)
        val length = kotlin.math.sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()
        assertEquals(1f, length, 0.01f)
    }

    @Test
    fun embed_sameText_producesSameVector() = runTest {
        val first = generator.embed("nova runtime")
        val second = generator.embed("nova runtime")
        assertTrue(first is EmbeddingResult.Success)
        assertTrue(second is EmbeddingResult.Success)
        assertEquals(
            (first as EmbeddingResult.Success).vector.toList(),
            (second as EmbeddingResult.Success).vector.toList(),
        )
    }

    @Test
    fun embed_emptyText_returnsFailure() = runTest {
        val result = generator.embed("  ")
        assertTrue(result is EmbeddingResult.Failure)
    }
}

class FileSystemModelLoaderTest {

    @Test
    fun availabilityReport_marksMissingModelsUnavailable() {
        val loader = FileSystemModelLoader(
            ModelLoadConfig(modelsDirectory = "/tmp/nova-models-test-missing"),
        )
        val report = kotlinx.coroutines.runBlocking { loader.availabilityReport() }
        assertTrue(report.all { !it.available })
        assertEquals(ModelAssetPaths.ALL_REQUIRED.size, report.size)
    }
}
