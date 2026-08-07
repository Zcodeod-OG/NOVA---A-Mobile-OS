package com.nova.runtime.ai.model

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Resumable HTTP downloader using Range requests for large ONNX artifacts. */
class ResumableFileDownloader(
    private val connectTimeoutMs: Int = 30_000,
    private val readTimeoutMs: Int = 60_000,
    private val userAgent: String = "NOVA-ModelDownloader/1.0",
) {

    suspend fun download(
        url: String,
        targetFile: File,
        minimumValidBytes: Long = 0L,
        onProgress: suspend (downloaded: Long, total: Long?) -> Unit = { _, _ -> },
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            targetFile.parentFile?.mkdirs()

            if (targetFile.isFile && targetFile.length() >= minimumValidBytes) {
                onProgress(targetFile.length(), targetFile.length())
                return@runCatching
            }

            val partialFile = File(targetFile.parentFile, "${targetFile.name}.part")
            val existingBytes = if (partialFile.isFile) partialFile.length() else 0L

            val connection = openConnection(url, existingBytes)
            try {
                val responseCode = connection.responseCode
                when (responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        if (existingBytes > 0L) {
                            partialFile.delete()
                        }
                        streamToFile(connection, partialFile, startOffset = 0L, onProgress)
                    }
                    HttpURLConnection.HTTP_PARTIAL -> {
                        streamToFile(connection, partialFile, startOffset = existingBytes, onProgress)
                    }
                    else -> throw IOException("HTTP $responseCode for $url")
                }
            } finally {
                connection.disconnect()
            }

            if (!partialFile.isFile || partialFile.length() < minimumValidBytes) {
                throw IOException(
                    "Download incomplete for ${targetFile.name}: ${partialFile.length()} bytes " +
                        "(minimum $minimumValidBytes)",
                )
            }

            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (!partialFile.renameTo(targetFile)) {
                partialFile.copyTo(targetFile, overwrite = true)
                partialFile.delete()
            }
        }
    }

    private fun openConnection(url: String, resumeFrom: Long): HttpURLConnection {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", userAgent)
            if (resumeFrom > 0L) {
                setRequestProperty("Range", "bytes=$resumeFrom-")
            }
        }
        connection.connect()
        return connection
    }

    private suspend fun streamToFile(
        connection: HttpURLConnection,
        target: File,
        startOffset: Long,
        onProgress: suspend (downloaded: Long, total: Long?) -> Unit,
    ) {
        val contentLength = connection.contentLengthLong.takeIf { it > 0L }
        val totalBytes = when (connection.responseCode) {
            HttpURLConnection.HTTP_PARTIAL -> contentLength?.let { startOffset + it }
            else -> contentLength
        }

        connection.inputStream.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var downloaded = startOffset
                onProgress(downloaded, totalBytes)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    onProgress(downloaded, totalBytes)
                }
            }
        }
    }
}
