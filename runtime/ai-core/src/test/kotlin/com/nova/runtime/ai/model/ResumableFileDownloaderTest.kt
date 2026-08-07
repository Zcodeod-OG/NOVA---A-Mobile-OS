package com.nova.runtime.ai.model

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResumableFileDownloaderTest {

    @TempDir
    lateinit var tempDir: File

    private val downloader = ResumableFileDownloader()

    @Test
    fun download_skipsWhenTargetAlreadyValid() = runTest {
        val target = File(tempDir, "embedding-mini.onnx")
        target.writeBytes(ByteArray(2_000_000) { 1 })

        var progressCalls = 0
        val result = downloader.download(
            url = "https://example.invalid/model.onnx",
            targetFile = target,
            minimumValidBytes = 1_000_000L,
        ) { _, _ ->
            progressCalls++
        }

        assertTrue(result.isSuccess)
        assertEquals(1, progressCalls)
        assertEquals(2_000_000L, target.length())
    }

    @Test
    fun download_failsWhenResponseUnavailable() = runTest {
        val target = File(tempDir, "whisper-tiny.onnx")

        val result = downloader.download(
            url = "https://example.invalid/whisper.onnx",
            targetFile = target,
            minimumValidBytes = 10_000_000L,
        )

        assertTrue(result.isFailure)
        assertTrue(!target.isFile)
    }
}
