package com.nova.runtime.ai.native.extraction

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nova.runtime.ai.model.DocumentExtractionResult
import com.nova.runtime.ai.model.OcrEngine
import com.nova.runtime.ai.model.OcrResult
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AndroidDocumentTextExtractorTest {
    private lateinit var context: Context
    private lateinit var extractor: AndroidDocumentTextExtractor

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        extractor = AndroidDocumentTextExtractor(context, StubOcrEngine())
    }

    @Test
    fun extract_plainTextFile_returnsSuccess() =
        runTest {
            val file = File(context.cacheDir, "menu.txt")
            file.writeText(
                """
                MONDAY
                Lunch: dal rice
                """.trimIndent(),
            )

            val result = extractor.extract(
                uri = file.toURI().toString(),
                mimeType = "text/plain",
                extension = "txt",
            )

            assertTrue(result is DocumentExtractionResult.Success)
            assertTrue((result as DocumentExtractionResult.Success).text.contains("dal rice"))
        }

    @Test
    fun extract_emptyTextFile_returnsEmpty() =
        runTest {
            val file = File(context.cacheDir, "blank.txt")
            file.writeText("   \n  ")

            val result = extractor.extract(
                uri = file.toURI().toString(),
                mimeType = "text/plain",
                extension = "txt",
            )

            assertEquals(DocumentExtractionResult.Empty, result)
        }

    @Test
    fun extract_htmlStripsMarkup() =
        runTest {
            val file = File(context.cacheDir, "timetable.html")
            file.writeText(
                """
                <html><body>
                <table><tr><td>Monday</td><td>Lec-AAA1111</td></tr></table>
                </body></html>
                """.trimIndent(),
            )

            val result = extractor.extract(
                uri = file.toURI().toString(),
                mimeType = "text/html",
                extension = "html",
            )

            assertTrue(result is DocumentExtractionResult.Success)
            val text = (result as DocumentExtractionResult.Success).text
            assertTrue(text.contains("AAA1111"))
            assertTrue(!text.contains("<table"))
        }

    @Test
    fun extract_unsupportedDocExtension_returnsUnsupported() =
        runTest {
            val result = extractor.extract(
                uri = "content://docs/legacy.doc",
                mimeType = "application/msword",
                extension = "doc",
            )

            assertEquals(DocumentExtractionResult.Unsupported, result)
        }

    private class StubOcrEngine : OcrEngine {
        override suspend fun recognize(imageBytes: ByteArray): OcrResult =
            OcrResult.Failure("unused in plain-text tests")
    }
}
