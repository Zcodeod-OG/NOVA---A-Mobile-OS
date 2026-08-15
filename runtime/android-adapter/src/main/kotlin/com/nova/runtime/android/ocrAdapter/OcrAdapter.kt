package com.nova.runtime.android.ocrAdapter

import com.nova.runtime.ai.model.OcrEngine
import com.nova.runtime.ai.model.OcrResult

/** Android adapter hook for on-device OCR — delegates to runtime OCR engine. */
interface OcrAdapter {
    suspend fun extractText(imageBytes: ByteArray): OcrAdapterResult
}

data class OcrAdapterResult(
    val text: String?,
    val blockCount: Int,
    val success: Boolean,
    val errorMessage: String? = null,
)

class OcrAdapterImpl(
    private val ocrEngine: OcrEngine,
) : OcrAdapter {
    override suspend fun extractText(imageBytes: ByteArray): OcrAdapterResult =
        when (val result = ocrEngine.recognize(imageBytes)) {
            is OcrResult.Success ->
                OcrAdapterResult(
                    text = result.text.takeIf { it.isNotBlank() },
                    blockCount = result.blocks.size,
                    success = true,
                )
            is OcrResult.Failure ->
                OcrAdapterResult(
                    text = null,
                    blockCount = 0,
                    success = false,
                    errorMessage = result.message,
                )
        }
}

/** Placeholder when AI module is not wired. */
class OcrAdapterStub : OcrAdapter {
    override suspend fun extractText(imageBytes: ByteArray): OcrAdapterResult =
        OcrAdapterResult(
            text = null,
            blockCount = 0,
            success = false,
            errorMessage = "OCR adapter not configured",
        )
}
