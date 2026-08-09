package com.nova.runtime.ai.native.search

import com.nova.runtime.ai.model.LocalLlmEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroundedDocumentAnswerServiceTest {

    @Test
    fun blankSnippet_refusesWithoutFabricatingSchedule() = runTest {
        val service = GroundedDocumentAnswerService()
        val answer = service.answer(
            query = "today's lectures",
            contentSnippet = null,
            sourceFileName = "timetable.png",
            sourceModifiedAt = 1L,
        )
        assertFalse(answer.contains("Physics", ignoreCase = true))
        assertFalse(answer.contains("DSP", ignoreCase = true))
        assertTrue(answer.contains("timetable.png") || answer.contains("Couldn't", ignoreCase = true))
    }

    @Test
    fun extractiveUsedWhenLlmUnavailable() = runTest {
        val snippet = """
            Monday
            9:30-11:00 MLL1001 Lecture LH 325
            Tuesday
            8:00-9:00 MEL2001 Lecture LH 308
            """.trimIndent()
        val service = GroundedDocumentAnswerService()
        val answer = service.answer(
            query = "timetable monday",
            contentSnippet = snippet,
            sourceFileName = "semester_timetable.html",
            sourceModifiedAt = System.currentTimeMillis(),
        )
        assertTrue(answer.contains("MLL1001") || answer.contains("Monday") || answer.contains("semester_timetable"))
        assertFalse(answer.startsWith("onnx:"))
    }

    @Test
    fun scheduleFallback_neverLabelsAsTodaysLectures() = runTest {
        val snippet = """
            MONDAY
            9:30-11:00 MLL1001 Lecture LH 325
            TUESDAY
            8:00-9:00 MEL2001 Lecture LH 308
            """.trimIndent()
        val service = GroundedDocumentAnswerService()
        val answer = service.answer(
            query = "today's lectures",
            contentSnippet = snippet,
            sourceFileName = "timetable_ocr.png",
            sourceModifiedAt = 1L,
        )
        assertFalse(answer.startsWith("Today's lecture slots"))
        assertTrue(answer.contains("MLL1001") || answer.contains("Schedule excerpt"))
        assertTrue(answer.contains("timetable_ocr.png"))
    }

    @Test
    fun ungroundedLlmOutput_fallsBackToExtractive() = runTest {
        val snippet = """
            Monday
            9:30-11:00 MLL1001 Lecture LH 325
            """.trimIndent()
        val lyingLlm = object : LocalLlmEngine {
            override fun isAvailable(): Boolean = true
            override suspend fun generate(prompt: String, maxTokens: Int): String =
                "Today you have Quantum Astrology at 3am in Room Z9."
        }
        val service = GroundedDocumentAnswerService(lyingLlm)
        val answer = service.answer(
            query = "monday lectures",
            contentSnippet = snippet,
            sourceFileName = "tt.html",
            sourceModifiedAt = 1L,
        )
        assertFalse(answer.contains("Quantum Astrology"))
        assertTrue(answer.contains("MLL1001") || answer.contains("tt.html"))
    }

    @Test
    fun groundedLlmRewrite_acceptedWhenOverlapping() = runTest {
        val snippet = """
            Monday
            9:30-11:00 MLL1001 Lecture LH 325
            5:00-6:30 HSL2605 Lecture LH 416
            """.trimIndent()
        val llm = object : LocalLlmEngine {
            override fun isAvailable(): Boolean = true
            override suspend fun generate(prompt: String, maxTokens: Int): String =
                "Monday: MLL1001 Lecture in LH 325; HSL2605 Lecture in LH 416."
        }
        val service = GroundedDocumentAnswerService(llm)
        val answer = service.answer(
            query = "monday lectures",
            contentSnippet = snippet,
            sourceFileName = "tt.html",
            sourceModifiedAt = 1L,
        )
        assertTrue(answer.contains("MLL1001"))
        assertTrue(answer.contains("HSL2605"))
    }
}
