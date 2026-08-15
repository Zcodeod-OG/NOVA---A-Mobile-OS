package com.nova.runtime.ai.tokenizer

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * BERT WordPiece tokenizer compatible with all-MiniLM-L6-v2 / BertTokenizer.
 *
 * Produces [CLS] + tokens + [SEP] with optional truncation to [maxLength].
 * Does not pad by default — callers may pad before ONNX inference.
 */
class BertWordPieceTokenizer private constructor(
    private val vocab: Map<String, Int>,
    private val unkTokenId: Long,
    private val clsTokenId: Long,
    private val sepTokenId: Long,
    private val doLowerCase: Boolean,
    private val tokenizeChineseChars: Boolean,
    private val maxLength: Int,
) {
    fun encode(text: String, maxLength: Int = this.maxLength): BertTokenizerOutput {
        val wordPieces = tokenizeToWordPieces(text)
        val cappedPieces = if (wordPieces.size > maxLength - 2) {
            wordPieces.take(maxLength - 2)
        } else {
            wordPieces
        }

        val inputIds = LongArray(cappedPieces.size + 2)
        inputIds[0] = clsTokenId
        cappedPieces.forEachIndexed { index, piece ->
            inputIds[index + 1] = vocab[piece]?.toLong() ?: unkTokenId
        }
        inputIds[inputIds.lastIndex] = sepTokenId

        val attentionMask = LongArray(inputIds.size) { 1L }
        val tokenTypeIds = LongArray(inputIds.size) { 0L }

        return BertTokenizerOutput(
            inputIds = inputIds,
            attentionMask = attentionMask,
            tokenTypeIds = tokenTypeIds,
        )
    }

    fun tokenizeToWordPieces(text: String): List<String> {
        val basicTokens = basicTokenize(text)
        return basicTokens.flatMap { wordPieceTokenize(it) }
    }

    private fun basicTokenize(text: String): List<String> {
        var cleaned = cleanText(text)
        if (tokenizeChineseChars) {
            cleaned = tokenizeChineseChars(cleaned)
        }
        return whitespaceTokenize(cleaned).flatMap { token ->
            splitOnPunctuation(token)
        }
    }

    private fun cleanText(text: String): String {
        val output = StringBuilder(text.length)
        for (char in text) {
            when {
                char.code == 0 || char.code == 0xFFFD || isControl(char) -> Unit
                isWhitespace(char) -> output.append(' ')
                else -> output.append(char)
            }
        }
        return output.toString().trim()
    }

    private fun tokenizeChineseChars(text: String): String {
        val output = StringBuilder(text.length * 2)
        for (char in text) {
            if (isChineseChar(char)) {
                output.append(' ')
                output.append(char)
                output.append(' ')
            } else {
                output.append(char)
            }
        }
        return output.toString()
    }

    private fun whitespaceTokenize(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        return text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    }

    private fun splitOnPunctuation(text: String): List<String> {
        if (text.isEmpty()) return emptyList()

        val chars = text.toCharArray()
        val groups = mutableListOf<MutableList<Char>>()
        var startNewWord = true

        for (char in chars) {
            if (isPunctuation(char)) {
                groups.add(mutableListOf(char))
                startNewWord = true
            } else {
                if (startNewWord || groups.isEmpty()) {
                    groups.add(mutableListOf())
                    startNewWord = false
                }
                groups.last().add(char)
            }
        }

        return groups.map { it.joinToString("") }.filter { it.isNotEmpty() }
    }

    private fun wordPieceTokenize(token: String): List<String> {
        if (token.isEmpty()) return emptyList()

        val normalized = if (doLowerCase) token.lowercase() else token
        val chars = normalized.toCharArray()
        val output = mutableListOf<String>()
        var start = 0

        while (start < chars.size) {
            var end = chars.size
            var currentSubword: String? = null

            while (start < end) {
                val candidate = buildString(end - start) {
                    if (start > 0) append("##")
                    append(chars, start, end - start)
                }
                if (vocab.containsKey(candidate)) {
                    currentSubword = candidate
                    break
                }
                end--
            }

            if (currentSubword == null) {
                output += UNK_TOKEN
                start++
            } else {
                output += currentSubword
                start = end
            }
        }

        return output
    }

    companion object {
        private const val UNK_TOKEN = "[UNK]"

        const val DEFAULT_MAX_LENGTH = 128
        const val DEFAULT_CLS_TOKEN_ID = 101L
        const val DEFAULT_SEP_TOKEN_ID = 102L
        const val DEFAULT_UNK_TOKEN_ID = 100L

        fun fromVocabReader(
            reader: BufferedReader,
            unkTokenId: Long = DEFAULT_UNK_TOKEN_ID,
            clsTokenId: Long = DEFAULT_CLS_TOKEN_ID,
            sepTokenId: Long = DEFAULT_SEP_TOKEN_ID,
            doLowerCase: Boolean = true,
            tokenizeChineseChars: Boolean = true,
            maxLength: Int = DEFAULT_MAX_LENGTH,
        ): BertWordPieceTokenizer {
            val vocab = linkedMapOf<String, Int>()
            reader.forEachLine { line ->
                val token = line.trim()
                if (token.isNotEmpty()) {
                    vocab[token] = vocab.size
                }
            }
            return BertWordPieceTokenizer(
                vocab = vocab,
                unkTokenId = unkTokenId,
                clsTokenId = clsTokenId,
                sepTokenId = sepTokenId,
                doLowerCase = doLowerCase,
                tokenizeChineseChars = tokenizeChineseChars,
                maxLength = maxLength,
            )
        }

        fun fromVocabStream(
            inputStream: InputStream,
            unkTokenId: Long = DEFAULT_UNK_TOKEN_ID,
            clsTokenId: Long = DEFAULT_CLS_TOKEN_ID,
            sepTokenId: Long = DEFAULT_SEP_TOKEN_ID,
            doLowerCase: Boolean = true,
            tokenizeChineseChars: Boolean = true,
            maxLength: Int = DEFAULT_MAX_LENGTH,
        ): BertWordPieceTokenizer =
            fromVocabReader(
                reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)),
                unkTokenId = unkTokenId,
                clsTokenId = clsTokenId,
                sepTokenId = sepTokenId,
                doLowerCase = doLowerCase,
                tokenizeChineseChars = tokenizeChineseChars,
                maxLength = maxLength,
            )

        fun fromClasspath(
            classLoader: ClassLoader = BertWordPieceTokenizer::class.java.classLoader
                ?: Thread.currentThread().contextClassLoader,
            resourcePath: String = "tokenizer/vocab.txt",
            unkTokenId: Long = DEFAULT_UNK_TOKEN_ID,
            clsTokenId: Long = DEFAULT_CLS_TOKEN_ID,
            sepTokenId: Long = DEFAULT_SEP_TOKEN_ID,
            doLowerCase: Boolean = true,
            tokenizeChineseChars: Boolean = true,
            maxLength: Int = DEFAULT_MAX_LENGTH,
        ): BertWordPieceTokenizer {
            val stream = requireNotNull(classLoader.getResourceAsStream(resourcePath)) {
                "Tokenizer vocab not found on classpath: $resourcePath"
            }
            return stream.use {
                fromVocabStream(
                    inputStream = it,
                    unkTokenId = unkTokenId,
                    clsTokenId = clsTokenId,
                    sepTokenId = sepTokenId,
                    doLowerCase = doLowerCase,
                    tokenizeChineseChars = tokenizeChineseChars,
                    maxLength = maxLength,
                )
            }
        }

        private fun isControl(char: Char): Boolean {
            val type = Character.getType(char)
            return type == Character.CONTROL.toInt() ||
                type == Character.FORMAT.toInt() ||
                type == Character.PRIVATE_USE.toInt() ||
                type == Character.SURROGATE.toInt() ||
                type == Character.UNASSIGNED.toInt()
        }

        private fun isWhitespace(char: Char): Boolean =
            char == ' ' || char == '\t' || char == '\n' || char == '\r' ||
                Character.isWhitespace(char)

        private fun isPunctuation(char: Char): Boolean {
            val cp = char.code
            if (cp in 33..47 || cp in 58..64 || cp in 91..96 || cp in 123..126) {
                return true
            }
            return Character.getType(char).toByte() in PUNCTUATION_TYPES
        }

        private val PUNCTUATION_TYPES = byteArrayOf(
            Character.CONNECTOR_PUNCTUATION.toByte(),
            Character.DASH_PUNCTUATION.toByte(),
            Character.START_PUNCTUATION.toByte(),
            Character.END_PUNCTUATION.toByte(),
            Character.INITIAL_QUOTE_PUNCTUATION.toByte(),
            Character.FINAL_QUOTE_PUNCTUATION.toByte(),
            Character.OTHER_PUNCTUATION.toByte(),
        )

        private fun isChineseChar(char: Char): Boolean {
            val cp = char.code
            return cp in 0x4E00..0x9FFF ||
                cp in 0x3400..0x4DBF ||
                cp in 0x20000..0x2A6DF ||
                cp in 0x2A700..0x2B73F ||
                cp in 0x2B740..0x2B81F ||
                cp in 0x2B820..0x2CEAF ||
                cp in 0xF900..0xFAFF ||
                cp in 0x2F800..0x2FA1F
        }
    }
}
