package com.nova.runtime.ai.model

/**
 * Outcome of on-device document text extraction.
 *
 * Distinguishes URI/open failures from readable-but-empty files so the indexer
 * can retry when permissions/URIs become available without treating empty OCR
 * as a permanent terminal state.
 */
sealed class DocumentExtractionResult {
    /** Readable document with non-blank extracted text. */
    data class Success(val text: String) : DocumentExtractionResult()

    /** Opened successfully but yielded no usable text (blank scan, empty file). */
    data object Empty : DocumentExtractionResult()

    /** Could not read the URI / stream / renderer (permissions, missing fd, I/O). */
    data class Unreadable(val reason: String) : DocumentExtractionResult()

    /** Format is not supported by the extractor. */
    data object Unsupported : DocumentExtractionResult()
}
