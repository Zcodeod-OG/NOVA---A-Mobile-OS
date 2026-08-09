package com.nova.runtime.ai.model

/**
 * On-device plain-text extraction from document files (DPS §7).
 * Implementations must be fully local (no network) and cap output for battery/perf.
 */
interface DocumentTextExtractor {
    /**
     * Extracts natural text from the document at [uri].
     * Returns a typed outcome so callers can distinguish never-tried / failed / empty / ok.
     */
    suspend fun extract(
        uri: String,
        mimeType: String,
        extension: String,
        maxPages: Int = DEFAULT_MAX_PAGES,
        maxChars: Int = DEFAULT_MAX_CHARS,
    ): DocumentExtractionResult

    /**
     * Convenience wrapper: success text, or null for empty / unreadable / unsupported.
     */
    suspend fun extractText(
        uri: String,
        mimeType: String,
        extension: String,
        maxPages: Int = DEFAULT_MAX_PAGES,
        maxChars: Int = DEFAULT_MAX_CHARS,
    ): String? =
        when (val result = extract(uri, mimeType, extension, maxPages, maxChars)) {
            is DocumentExtractionResult.Success -> result.text.takeIf { it.isNotBlank() }
            else -> null
        }

    companion object {
        /** OCR page budget for scanned PDFs (raised for multi-page menus/timetables). */
        const val DEFAULT_MAX_PAGES = 16
        const val DEFAULT_MAX_CHARS = 32_000

        val PLAIN_TEXT_EXTENSIONS: Set<String> =
            setOf("txt", "md", "csv", "json", "log", "xml", "html", "htm")

        val IMAGE_EXTENSIONS: Set<String> =
            setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")

        /** Office formats with a solid on-device extractor (DOCX zip+XML). Legacy .doc is not supported. */
        val OFFICE_EXTENSIONS: Set<String> = setOf("docx")

        /**
         * Formats the indexer can extract on-device (PDF/image via OCR, plain text, DOCX).
         * Legacy `.doc` / `application/msword` are intentionally excluded — there is no solid
         * on-device extractor; claiming support left empty `contentText` with no recovery path.
         */
        fun isExtractable(mimeType: String, extension: String): Boolean {
            val ext = extension.lowercase()
            val mime = mimeType.lowercase()
            if (ext == "doc" || mime == "application/msword") return false
            return mime == "application/pdf" ||
                ext == "pdf" ||
                mime.startsWith("text/") ||
                ext in PLAIN_TEXT_EXTENSIONS ||
                mime.startsWith("image/") ||
                ext in IMAGE_EXTENSIONS ||
                ext in OFFICE_EXTENSIONS ||
                mime.contains("wordprocessingml")
        }

        fun isImage(mimeType: String, extension: String): Boolean {
            val ext = extension.lowercase()
            return mimeType.lowercase().startsWith("image/") || ext in IMAGE_EXTENSIONS
        }
    }
}
