package com.nova.runtime.ai.tokenizer

/**
 * Tokenized BERT inputs for ONNX sentence-transformer models.
 */
data class BertTokenizerOutput(
    val inputIds: LongArray,
    val attentionMask: LongArray,
    val tokenTypeIds: LongArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BertTokenizerOutput) return false
        return inputIds.contentEquals(other.inputIds) &&
            attentionMask.contentEquals(other.attentionMask) &&
            tokenTypeIds.contentEquals(other.tokenTypeIds)
    }

    override fun hashCode(): Int {
        var result = inputIds.contentHashCode()
        result = 31 * result + attentionMask.contentHashCode()
        result = 31 * result + tokenTypeIds.contentHashCode()
        return result
    }
}
