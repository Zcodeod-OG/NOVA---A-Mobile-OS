package com.nova.runtime.ai.native.onnx

import com.nova.runtime.ai.model.EmbeddingResult
import com.nova.runtime.ai.model.ModelLoadConfig
import com.nova.runtime.ai.model.FileSystemModelLoader
import com.nova.runtime.utils.logging.StructuredLogger
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OnnxImageEmbeddingGeneratorTest {

    private val logger = StructuredLogger()

    @Test
    fun embed_withoutModel_fallsBackToHash() = runTest {
        val loader = FileSystemModelLoader(
            ModelLoadConfig(modelsDirectory = "/tmp/nova-image-models-missing-${UUID.randomUUID()}"),
        )
        val generator = OnnxImageEmbeddingGenerator(modelLoader = loader, logger = logger)
        val imageBytes = ImagePreprocessorTest.createTestPngBytes(width = 4, height = 4, color = 0xFF00FF00.toInt())

        val result = generator.embedImage(imageBytes)

        assertFalse(generator.isLoaded)
        assertTrue(result is EmbeddingResult.Success)
        val success = result as EmbeddingResult.Success
        assertEquals("hash-image-fallback-v1", success.modelVersion)
        val length = kotlin.math.sqrt(success.vector.sumOf { (it * it).toDouble() }).toFloat()
        assertEquals(1f, length, 0.01f)
    }

    @Test
    fun embed_emptyBytes_returnsFailure() = runTest {
        val loader = FileSystemModelLoader(
            ModelLoadConfig(modelsDirectory = "/tmp/nova-image-models-empty-${UUID.randomUUID()}"),
        )
        val generator = OnnxImageEmbeddingGenerator(modelLoader = loader, logger = logger)

        val result = generator.embedImage(byteArrayOf())

        assertTrue(result is EmbeddingResult.Failure)
    }
}
