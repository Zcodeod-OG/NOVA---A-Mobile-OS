package com.nova.runtime.ai.tokenizer

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BertWordPieceTokenizerTest {

    private lateinit var tokenizer: BertWordPieceTokenizer

    @BeforeTest
    fun setUp() {
        tokenizer = MiniLmTokenizer.fromClasspath()
    }

    @Test
    fun encode_helloWorld_matchesMiniLmVocab() {
        val output = tokenizer.encode("hello world")

        assertContentEquals(
            longArrayOf(101L, 7592L, 2088L, 102L),
            output.inputIds,
        )
        assertContentEquals(
            longArrayOf(1L, 1L, 1L, 1L),
            output.attentionMask,
        )
        assertContentEquals(
            longArrayOf(0L, 0L, 0L, 0L),
            output.tokenTypeIds,
        )
    }

    @Test
    fun encode_beachVacation_matchesMiniLmVocab() {
        val output = tokenizer.encode("beach vacation")

        assertContentEquals(
            longArrayOf(101L, 3509L, 10885L, 102L),
            output.inputIds,
        )
        assertContentEquals(
            longArrayOf(1L, 1L, 1L, 1L),
            output.attentionMask,
        )
        assertContentEquals(
            longArrayOf(0L, 0L, 0L, 0L),
            output.tokenTypeIds,
        )
    }

    @Test
    fun encode_respectsMaxLength128() {
        val longText = (1..200).joinToString(" ") { "word$it" }
        val output = tokenizer.encode(longText)

        assertEquals(MiniLmTokenizer.MAX_LENGTH, output.inputIds.size)
        assertEquals(MiniLmTokenizer.CLS_TOKEN_ID, output.inputIds.first())
        assertEquals(MiniLmTokenizer.SEP_TOKEN_ID, output.inputIds.last())
        assertEquals(output.inputIds.size, output.attentionMask.size)
        assertEquals(output.inputIds.size, output.tokenTypeIds.size)
        assertTrue(output.attentionMask.all { it == 1L })
        assertTrue(output.tokenTypeIds.all { it == 0L })
    }

    @Test
    fun encode_lowercasesInput() {
        val lower = tokenizer.encode("Hello World")
        val upper = tokenizer.encode("HELLO WORLD")

        assertContentEquals(lower.inputIds, upper.inputIds)
    }
}
