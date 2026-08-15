package com.nova.runtime.ai.native.tokenizer

/** ONNX embedding model inputs produced by [MiniLmTokenizer]. */
data class TokenizedInput(
    val inputIds: LongArray,
    val attentionMask: LongArray,
    val tokenTypeIds: LongArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TokenizedInput) return false
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
