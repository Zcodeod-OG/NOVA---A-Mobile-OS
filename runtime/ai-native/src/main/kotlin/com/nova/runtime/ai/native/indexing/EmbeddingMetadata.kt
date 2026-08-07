package com.nova.runtime.ai.native.indexing

/** Metadata keys stored alongside vectors in [com.nova.runtime.ai.native.storage.CosineVectorIndex]. */
object EmbeddingMetadata {
    const val OBJECT_ID = "objectId"
    const val OBJECT_TYPE = "objectType"
    const val EMBEDDING_KIND = "embeddingKind"

    const val KIND_OCR = "ocr"
    const val KIND_IMAGE = "image"
}
