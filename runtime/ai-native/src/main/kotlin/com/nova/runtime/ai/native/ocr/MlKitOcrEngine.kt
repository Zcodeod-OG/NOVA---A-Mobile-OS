package com.nova.runtime.ai.native.ocr

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.nova.runtime.ai.model.OcrEngine
import com.nova.runtime.ai.model.OcrResult
import com.nova.runtime.ai.model.OcrTextBlock
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** On-device OCR via ML Kit Text Recognition (Latin script). No cloud calls. */
class MlKitOcrEngine : OcrEngine {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun recognize(imageBytes: ByteArray): OcrResult {
        if (imageBytes.isEmpty()) {
            return OcrResult.Failure("Empty image payload")
        }

        return suspendCancellableCoroutine { continuation ->
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            if (bitmap == null) {
                continuation.resume(OcrResult.Failure("Unable to decode image bytes"))
                return@suspendCancellableCoroutine
            }

            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val blocks = visionText.textBlocks.map { block ->
                        OcrTextBlock(
                            text = block.text,
                            confidence = 1f,
                        )
                    }
                    continuation.resume(
                        OcrResult.Success(
                            text = visionText.text,
                            blocks = blocks,
                        ),
                    )
                }
                .addOnFailureListener { error ->
                    continuation.resume(
                        OcrResult.Failure(
                            message = error.message ?: "ML Kit OCR failed",
                            recoverable = true,
                        ),
                    )
                }
        }
    }
}
