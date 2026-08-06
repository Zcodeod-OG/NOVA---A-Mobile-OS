package com.nova.runtime.storage.search

/** AIS §4.3 — abstraction over Android MediaStore image queries. */
interface MediaStoreQueryPort {
    suspend fun queryImages(limit: Int): MediaImageQueryResult
}

data class MediaImageItem(
    val mediaId: Long,
    val uri: String,
    val displayName: String?,
    val mimeType: String?,
    val dateAdded: Long,
    val size: Long,
)

data class MediaImageQueryResult(
    val items: List<MediaImageItem>,
)

/** No-op port for JVM tests and environments without MediaStore access. */
class NoOpMediaStoreQueryPort : MediaStoreQueryPort {
    override suspend fun queryImages(limit: Int): MediaImageQueryResult = MediaImageQueryResult(emptyList())
}
