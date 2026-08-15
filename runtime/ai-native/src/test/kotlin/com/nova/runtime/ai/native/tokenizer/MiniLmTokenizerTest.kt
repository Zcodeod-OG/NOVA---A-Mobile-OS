package com.nova.runtime.ai.native.tokenizer

import com.nova.runtime.ai.tokenizer.MiniLmTokenizer as CoreMiniLmTokenizer
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MiniLmTokenizerTest {
    private lateinit var tokenizer: MiniLmTokenizer

    @Before
    fun setUp() {
        tokenizer = MiniLmTokenizer.fromTokenizer(CoreMiniLmTokenizer.fromClasspath())
    }

    @Test
    fun tokenize_helloWorld_wrapsWithClsSep() {
        val encoded = tokenizer.tokenize("hello world")

        assertArrayEquals(longArrayOf(101L, 7592L, 2088L, 102L), encoded.inputIds)
        assertArrayEquals(longArrayOf(1L, 1L, 1L, 1L), encoded.attentionMask)
        assertArrayEquals(longArrayOf(0L, 0L, 0L, 0L), encoded.tokenTypeIds)
    }

    @Test
    fun tokenize_beachVacation_producesExpectedSubwords() {
        val encoded = tokenizer.tokenize("beach vacation")

        assertArrayEquals(longArrayOf(101L, 3509L, 10885L, 102L), encoded.inputIds)
    }

    @Test
    fun fromVocabLines_emptyVocab_isNotLoaded() {
        val emptyTokenizer = MiniLmTokenizer.fromVocabLines(emptyList())

        assertFalse(emptyTokenizer.isLoaded)
        assertTrue(emptyTokenizer.unavailableReason?.contains("empty") == true)
    }
}
