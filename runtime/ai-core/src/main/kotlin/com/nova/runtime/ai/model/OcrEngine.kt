package com.nova.runtime.ai.model

sealed class OcrResult {
    data class Success(val text: String, val blocks: List<OcrTextBlock> = emptyList()) : OcrResult()
    data class Failure(val message: String, val recoverable: Boolean = true) : OcrResult()
}

data class OcrTextBlock(
    val text: String,
    val confidence: Float = 1f,
)

/** On-device OCR contract for photo/document indexing (DPS §7). */
interface OcrEngine {
    suspend fun recognize(imageBytes: ByteArray): OcrResult
}
