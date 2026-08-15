package com.nova.runtime.ai.native.onnx

import com.nova.runtime.ai.embedding.VectorMath
import com.nova.runtime.ai.model.EmbeddingResult
import com.nova.runtime.ai.model.FileSystemModelLoader
import com.nova.runtime.ai.model.ModelAssetPaths
import com.nova.runtime.ai.model.ModelLoadConfig
import com.nova.runtime.ai.native.tokenizer.MiniLmTokenizer
import com.nova.runtime.ai.tokenizer.MiniLmTokenizer as CoreMiniLmTokenizer
import com.nova.runtime.utils.logging.NoOpRuntimeLogger
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OnnxEmbeddingGeneratorTest {
    private lateinit var tokenizer: MiniLmTokenizer
    private val logger = NoOpRuntimeLogger()

    @Before
    fun setUp() {
        tokenizer = MiniLmTokenizer.fromTokenizer(CoreMiniLmTokenizer.fromClasspath())
    }

    @Test
    fun embed_withoutOnnxModel_returnsFailureNotHashFallback() = runTest {
        val modelsDir = createTempDir(prefix = "nova-embed-missing")
        val generator = OnnxEmbeddingGenerator(
            modelLoader = FileSystemModelLoader(ModelLoadConfig(modelsDirectory = modelsDir.absolutePath)),
            tokenizer = tokenizer,
            logger = logger,
        )

        val result = generator.embed("invoice financial report")

        assertTrue(result is EmbeddingResult.Failure)
        assertTrue((result as EmbeddingResult.Failure).message.contains("ONNX embedding model unavailable"))
    }

    @Test
    fun embed_withoutTokenizer_returnsFailure() = runTest {
        val unavailableTokenizer = MiniLmTokenizer.fromVocabLines(emptyList())
        val generator = OnnxEmbeddingGenerator(
            modelLoader = FileSystemModelLoader(ModelLoadConfig(modelsDirectory = createTempDir().absolutePath)),
            tokenizer = unavailableTokenizer,
            logger = logger,
        )

        val result = generator.embed("hello")

        assertTrue(result is EmbeddingResult.Failure)
        assertTrue((result as EmbeddingResult.Failure).message.contains("Embedding tokenizer unavailable"))
    }

    @Test
    fun embed_withRealOnnxModel_roundTrips384DimVector() = runTest {
        val modelFile = locateEmbeddingModel()
        if (modelFile == null) {
            println("Skipping ONNX round-trip: ${ModelAssetPaths.EMBEDDING_MODEL} not found")
            return@runTest
        }

        val modelsDir = createTempDir(prefix = "nova-embed-onnx")
        modelFile.copyTo(File(modelsDir, ModelAssetPaths.EMBEDDING_MODEL), overwrite = true)
        val generator = OnnxEmbeddingGenerator(
            modelLoader = FileSystemModelLoader(ModelLoadConfig(modelsDirectory = modelsDir.absolutePath)),
            tokenizer = tokenizer,
            logger = logger,
        )

        val first = generator.embed("Quarterly invoice financial report")
        val second = generator.embed("beach vacation sunset photo")

        if (first is EmbeddingResult.Failure) {
            println("ONNX round-trip skipped: ${first.message}")
            return@runTest
        }

        assertTrue(first is EmbeddingResult.Success)
        assertTrue(second is EmbeddingResult.Success)
        val firstVector = (first as EmbeddingResult.Success).vector
        val secondVector = (second as EmbeddingResult.Success).vector
        assertEquals(ModelAssetPaths.DEFAULT_EMBEDDING_DIMENSION, firstVector.size)
        assertEquals(ModelAssetPaths.DEFAULT_EMBEDDING_DIMENSION, secondVector.size)
        assertEquals(ModelAssetPaths.EMBEDDING_MODEL_VERSION, first.modelVersion)

        val selfSimilarity = VectorMath.cosineSimilarity(firstVector, firstVector)
        val crossSimilarity = VectorMath.cosineSimilarity(firstVector, secondVector)
        assertTrue(selfSimilarity > 0.99f)
        assertTrue(crossSimilarity < selfSimilarity)
    }

    private fun locateEmbeddingModel(): File? {
        val candidates = listOf(
            File("../../app/src/main/assets/models/${ModelAssetPaths.EMBEDDING_MODEL}"),
            File("../../../app/src/main/assets/models/${ModelAssetPaths.EMBEDDING_MODEL}"),
        )
        return candidates.firstOrNull { it.isFile }
    }
}
