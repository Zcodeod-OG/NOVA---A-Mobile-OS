package com.nova.runtime.ai.native.onnx

import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ImagePreprocessorTest {

    @Test
    fun preprocessBitmap_producesNchwTensor() {
        val bitmap = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)

        val result = ImagePreprocessor.preprocessBitmap(bitmap)

        assertTrue(result is ImagePreprocessor.Result.Success)
        val success = result as ImagePreprocessor.Result.Success
        assertEquals(listOf(1L, 3L, 224L, 224L), success.shape.toList())
        assertEquals(1 * 3 * 224 * 224, success.data.size)
    }

    @Test
    fun bitmapToNchw_normalizesRedPixel() {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(255, 0, 0))

        val tensor = ImagePreprocessor.bitmapToNchw(
            bitmap = bitmap,
            mean = ImagePreprocessor.meanRgb,
            std = ImagePreprocessor.stdRgb,
        )

        assertEquals(3, tensor.size)
        val expectedR = (1f - ImagePreprocessor.meanRgb[0]) / ImagePreprocessor.stdRgb[0]
        assertEquals(expectedR, tensor[0], 0.001f)
    }

    @Test
    fun preprocess_decodesPngBytes() {
        val pngBytes = createTestPngBytes(width = 8, height = 8, color = Color.BLUE)

        val result = ImagePreprocessor.preprocess(pngBytes)

        assertTrue(result is ImagePreprocessor.Result.Success)
    }

    @Test
    fun preprocess_emptyBytes_returnsFailure() {
        val result = ImagePreprocessor.preprocess(byteArrayOf())
        assertTrue(result is ImagePreprocessor.Result.Failure)
    }

    companion object {
        fun createTestPngBytes(width: Int, height: Int, color: Int): ByteArray {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(color)
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            return stream.toByteArray()
        }
    }
}
