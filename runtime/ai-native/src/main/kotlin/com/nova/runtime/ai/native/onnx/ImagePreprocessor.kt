package com.nova.runtime.ai.native.onnx

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.nova.runtime.ai.model.ModelAssetPaths

/**
 * Converts decoded image bytes into an ImageNet-normalized NCHW float tensor
 * for MobileCLIP / CLIP-style vision encoders.
 */
object ImagePreprocessor {
    val inputSize: Int = ModelAssetPaths.IMAGE_EMBEDDING_INPUT_SIZE

    /** ImageNet mean/std used by MobileCLIP-S0 and standard CLIP exports. */
    val meanRgb: FloatArray = floatArrayOf(0.485f, 0.456f, 0.406f)
    val stdRgb: FloatArray = floatArrayOf(0.229f, 0.224f, 0.225f)

    sealed interface Result {
        data class Success(
            /** Flat NCHW buffer: index = c * H * W + y * W + x */
            val data: FloatArray,
            val shape: LongArray,
        ) : Result

        data class Failure(val message: String) : Result
    }

    fun preprocess(imageBytes: ByteArray): Result {
        if (imageBytes.isEmpty()) {
            return Result.Failure("Empty image payload")
        }
        val decoded = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            ?: return Result.Failure("Unable to decode image bytes")
        return try {
            preprocessBitmap(decoded)
        } finally {
            decoded.recycle()
        }
    }

    fun preprocessBitmap(bitmap: Bitmap): Result {
        val size = inputSize
        val scaled = if (bitmap.width == size && bitmap.height == size) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, size, size, true)
        }
        val ownsScaled = scaled !== bitmap
        return try {
            val data = bitmapToNchw(scaled, meanRgb, stdRgb)
            Result.Success(
                data = data,
                shape = longArrayOf(1, 3, size.toLong(), size.toLong()),
            )
        } finally {
            if (ownsScaled) {
                scaled.recycle()
            }
        }
    }

    internal fun bitmapToNchw(
        bitmap: Bitmap,
        mean: FloatArray,
        std: FloatArray,
    ): FloatArray {
        val width = bitmap.width
        val height = bitmap.height
        val planeSize = width * height
        val tensor = FloatArray(3 * planeSize)
        val pixels = IntArray(planeSize)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = pixels[y * width + x]
                val r = ((pixel shr 16) and 0xFF) / 255f
                val g = ((pixel shr 8) and 0xFF) / 255f
                val b = (pixel and 0xFF) / 255f
                val offset = y * width + x
                tensor[offset] = (r - mean[0]) / std[0]
                tensor[planeSize + offset] = (g - mean[1]) / std[1]
                tensor[2 * planeSize + offset] = (b - mean[2]) / std[2]
            }
        }
        return tensor
    }
}
