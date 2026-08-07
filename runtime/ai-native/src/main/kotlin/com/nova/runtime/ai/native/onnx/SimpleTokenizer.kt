package com.nova.runtime.ai.native.onnx

/** Hash-based whitespace tokenizer retained for ONNX LLM inference stubs only. */
object SimpleTokenizer {
    private const val MAX_TOKENS = 128
    private const val CLS = 101L
    private const val SEP = 102L

    fun encode(text: String, maxLength: Int = MAX_TOKENS): LongArray {
        val tokens = mutableListOf(CLS)
        text.trim()
            .lowercase()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(maxLength - 2)
            .forEach { token ->
                tokens += token.hashCode().toLong().let { if (it < 0) -it else it } % 30_000 + 1_000
            }
        tokens += SEP
        return tokens.toLongArray()
    }

    fun attentionMask(inputIds: LongArray): LongArray = LongArray(inputIds.size) { 1L }

    fun tokenTypeIds(inputIds: LongArray): LongArray = LongArray(inputIds.size) { 0L }
}
