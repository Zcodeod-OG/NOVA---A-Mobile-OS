package com.nova.runtime.ai.native.ingestion

/** Loads raw image bytes for a gallery URI during indexing. */
fun interface PhotoImageLoader {
    suspend fun loadBytes(uri: String): ByteArray?
}
