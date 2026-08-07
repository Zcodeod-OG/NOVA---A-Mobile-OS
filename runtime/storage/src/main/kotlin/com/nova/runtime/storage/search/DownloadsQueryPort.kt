package com.nova.runtime.storage.search

/** Abstraction over Android MediaStore Downloads queries for document ingestion. */
interface DownloadsQueryPort {
    suspend fun queryDownloads(limit: Int): DownloadQueryResult
}

data class DownloadItem(
    val downloadId: Long,
    val uri: String,
    val displayName: String?,
    val mimeType: String?,
    val dateAdded: Long,
    val size: Long,
)

data class DownloadQueryResult(
    val items: List<DownloadItem>,
)

/** No-op port for JVM tests and environments without Downloads access. */
class NoOpDownloadsQueryPort : DownloadsQueryPort {
    override suspend fun queryDownloads(limit: Int): DownloadQueryResult = DownloadQueryResult(emptyList())
}
