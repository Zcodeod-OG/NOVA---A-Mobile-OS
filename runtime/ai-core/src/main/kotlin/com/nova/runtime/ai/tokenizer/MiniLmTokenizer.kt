package com.nova.runtime.ai.tokenizer

import com.nova.runtime.ai.model.ModelAssetPaths
import java.io.BufferedReader
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Factory for the all-MiniLM-L6-v2 BERT WordPiece tokenizer used by [ModelAssetPaths.EMBEDDING_MODEL].
 */
object MiniLmTokenizer {
    const val MAX_LENGTH = BertWordPieceTokenizer.DEFAULT_MAX_LENGTH
    const val CLS_TOKEN_ID = BertWordPieceTokenizer.DEFAULT_CLS_TOKEN_ID
    const val SEP_TOKEN_ID = BertWordPieceTokenizer.DEFAULT_SEP_TOKEN_ID
    const val UNK_TOKEN_ID = BertWordPieceTokenizer.DEFAULT_UNK_TOKEN_ID

    fun create(
        vocabStream: InputStream,
        maxLength: Int = MAX_LENGTH,
    ): BertWordPieceTokenizer =
        BertWordPieceTokenizer.fromVocabStream(
            inputStream = vocabStream,
            unkTokenId = UNK_TOKEN_ID,
            clsTokenId = CLS_TOKEN_ID,
            sepTokenId = SEP_TOKEN_ID,
            doLowerCase = true,
            tokenizeChineseChars = true,
            maxLength = maxLength,
        )

    fun fromVocabPath(vocabPath: Path, maxLength: Int = MAX_LENGTH): BertWordPieceTokenizer =
        Files.newInputStream(vocabPath).use { create(it, maxLength) }

    fun fromVocabReader(reader: BufferedReader, maxLength: Int = MAX_LENGTH): BertWordPieceTokenizer =
        BertWordPieceTokenizer.fromVocabReader(
            reader = reader,
            unkTokenId = UNK_TOKEN_ID,
            clsTokenId = CLS_TOKEN_ID,
            sepTokenId = SEP_TOKEN_ID,
            doLowerCase = true,
            tokenizeChineseChars = true,
            maxLength = maxLength,
        )

    fun fromClasspath(
        classLoader: ClassLoader = MiniLmTokenizer::class.java.classLoader
            ?: Thread.currentThread().contextClassLoader,
        resourcePath: String = ModelAssetPaths.TOKENIZER_VOCAB,
        maxLength: Int = MAX_LENGTH,
    ): BertWordPieceTokenizer =
        BertWordPieceTokenizer.fromClasspath(
            classLoader = classLoader,
            resourcePath = resourcePath,
            unkTokenId = UNK_TOKEN_ID,
            clsTokenId = CLS_TOKEN_ID,
            sepTokenId = SEP_TOKEN_ID,
            doLowerCase = true,
            tokenizeChineseChars = true,
            maxLength = maxLength,
        )

    fun fromAssetStream(
        assetPath: String = ModelAssetPaths.TOKENIZER_VOCAB_ASSET,
        openAsset: (String) -> InputStream,
        maxLength: Int = MAX_LENGTH,
    ): BertWordPieceTokenizer =
        openAsset(assetPath).use { create(it, maxLength) }

    fun fromAssetText(
        assetPath: String = ModelAssetPaths.TOKENIZER_VOCAB_ASSET,
        readAssetText: (String) -> String,
        maxLength: Int = MAX_LENGTH,
    ): BertWordPieceTokenizer =
        readAssetText(assetPath).byteInputStream(StandardCharsets.UTF_8).use { create(it, maxLength) }
}
