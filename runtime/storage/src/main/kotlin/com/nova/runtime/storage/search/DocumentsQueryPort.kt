package com.nova.runtime.storage.search

/** Abstraction over MediaStore Files (non-media documents) for document ingestion. */
interface DocumentsQueryPort {
    suspend fun queryDocuments(limit: Int, offset: Int = 0): DownloadQueryResult
}

/** No-op port for JVM tests and environments without Files access. */
class NoOpDocumentsQueryPort : DocumentsQueryPort {
    override suspend fun queryDocuments(limit: Int, offset: Int): DownloadQueryResult =
        DownloadQueryResult(emptyList())
}
