package com.nova.runtime.ai.native.tokenizer

import android.content.Context
import com.nova.runtime.ai.model.ModelAssetPaths
import com.nova.runtime.ai.tokenizer.BertWordPieceTokenizer
import com.nova.runtime.ai.tokenizer.MiniLmTokenizer as CoreMiniLmTokenizer
import java.io.BufferedReader

/**
 * Android-facing MiniLM tokenizer wrapper for ONNX embedding inference.
 *
 * Delegates WordPiece logic to [BertWordPieceTokenizer] in ai-core and loads vocab from APK assets.
 */
class MiniLmTokenizer private constructor(
    private val tokenizer: BertWordPieceTokenizer?,
    private val loadError: String?,
) {
    val isLoaded: Boolean get() = loadError == null

    val unavailableReason: String?
        get() = loadError

    fun tokenize(text: String, maxLength: Int = MAX_LENGTH): TokenizedInput {
        require(isLoaded) { loadError ?: "MiniLM tokenizer is not loaded" }
        val encoded = requireNotNull(tokenizer).encode(text, maxLength)
        return TokenizedInput(
            inputIds = encoded.inputIds,
            attentionMask = encoded.attentionMask,
            tokenTypeIds = encoded.tokenTypeIds,
        )
    }

    companion object {
        const val MAX_LENGTH = CoreMiniLmTokenizer.MAX_LENGTH

        private val ASSET_PATHS = listOf(
            ModelAssetPaths.TOKENIZER_VOCAB_ASSET,
            "models/${ModelAssetPaths.EMBEDDING_VOCAB}",
        )

        fun fromTokenizer(tokenizer: BertWordPieceTokenizer): MiniLmTokenizer =
            MiniLmTokenizer(tokenizer = tokenizer, loadError = null)

        fun fromVocabLines(lines: List<String>): MiniLmTokenizer {
            val cleaned = lines.map { it.trim() }.filter { it.isNotEmpty() }
            return if (cleaned.isEmpty()) {
                MiniLmTokenizer(
                    tokenizer = null,
                    loadError = "Tokenizer vocab is empty",
                )
            } else {
                fromTokenizer(
                    CoreMiniLmTokenizer.fromVocabReader(
                        BufferedReader(cleaned.joinToString("\n").reader()),
                    ),
                )
            }
        }

        fun fromAsset(context: Context): MiniLmTokenizer {
            for (assetPath in ASSET_PATHS) {
                val loaded = runCatching {
                    context.assets.open(assetPath).use { stream ->
                        fromTokenizer(CoreMiniLmTokenizer.create(stream))
                    }
                }.getOrNull()
                if (loaded != null && loaded.isLoaded) {
                    return loaded
                }
            }
            return MiniLmTokenizer(
                tokenizer = null,
                loadError = "Tokenizer vocab missing from assets: ${ASSET_PATHS.joinToString()}",
            )
        }
    }
}
