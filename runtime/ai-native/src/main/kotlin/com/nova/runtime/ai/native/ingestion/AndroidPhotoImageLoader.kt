package com.nova.runtime.ai.native.ingestion

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidPhotoImageLoader(
    private val context: Context,
) : PhotoImageLoader {
    override suspend fun loadBytes(uri: String): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
                    val bytes = stream.readBytes()
                    if (bytes.size > MAX_IMAGE_BYTES) {
                        bytes.copyOf(MAX_IMAGE_BYTES)
                    } else {
                        bytes
                    }
                }
            }.getOrNull()
        }

    companion object {
        private const val MAX_IMAGE_BYTES = 10 * 1024 * 1024
    }
}
