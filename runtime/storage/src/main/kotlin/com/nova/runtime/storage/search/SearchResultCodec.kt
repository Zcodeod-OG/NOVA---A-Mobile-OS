package com.nova.runtime.storage.search

data class ShareableDocumentHit(
    val uri: String,
    val mimeType: String,
    val name: String? = null,
    /** Extracted plain-text excerpt to accompany the shared file (e.g. today's menu section). */
    val contentSnippet: String? = null,
)

/** Serializes typed search pages into capability/execution output maps. */
object SearchResultCodec {
    private const val FIELD_SEP = "\u001e"
    private const val ITEM_SEP = "|"

    fun encodePage(page: SearchPage<*>): Map<String, String> =
        buildMap {
            put("query", page.query)
            put("count", page.count.toString())
            put("totalCount", page.totalCount.toString())
            put("limit", page.limit.toString())
            put("offset", page.offset.toString())
            put("hasMore", page.hasMore.toString())
        }

    fun encodePhotoHits(page: SearchPage<PhotoSearchHit>): Map<String, String> =
        encodePage(page) + mapOf(
            "items" to page.items.joinToString(ITEM_SEP) { hit ->
                joinFields(
                    hit.id,
                    hit.uri,
                    hit.takenAt,
                    hit.ocrText.orEmpty(),
                    hit.matchSource.name,
                    hit.score,
                )
            },
        )

    fun encodeDocumentHits(page: SearchPage<DocumentSearchHit>): Map<String, String> =
        encodePage(page) + mapOf(
            "items" to page.items.joinToString(ITEM_SEP) { hit ->
                joinFields(
                    hit.id,
                    hit.path,
                    hit.name,
                    hit.extension,
                    hit.mimeType,
                    hit.modifiedAt,
                    hit.score,
                    sanitizeSnippet(hit.contentSnippet),
                )
            },
        ) + answerFields(
            query = page.query,
            contentSnippet = page.items.firstOrNull()?.contentSnippet,
            sourceFileName = page.items.firstOrNull()?.name,
            sourceModifiedAt = page.items.firstOrNull()?.modifiedAt,
        )

    fun encodeSemanticHits(page: SearchPage<SemanticSearchHit>): Map<String, String> =
        encodePage(page) + mapOf(
            "items" to page.items.joinToString(ITEM_SEP) { hit ->
                joinFields(
                    hit.objectId,
                    hit.objectType,
                    hit.score,
                    hit.title,
                    hit.snippet.orEmpty(),
                    sanitizeSnippet(hit.contentSnippet),
                )
            },
        ) + answerFields(
            query = page.query,
            contentSnippet = page.items.firstOrNull()?.contentSnippet,
            sourceFileName = page.items.firstOrNull()?.title,
            sourceModifiedAt = page.items.firstOrNull()?.modifiedAt,
        )

    /** Top-level answer fields so the activity feed can show the snippet, not just "capability executed". */
    private fun answerFields(
        query: String,
        contentSnippet: String?,
        sourceFileName: String? = null,
        sourceModifiedAt: Long? = null,
    ): Map<String, String> {
        val snippet = contentSnippet?.takeIf { it.isNotBlank() }
        if (snippet == null) {
            // No extractive body — never invent a schedule; leave userMessage unset so the
            // capability provider can emit an explicit unreadable/no-section message.
            return emptyMap()
        }
        if (!DocumentContentNormalizer.isGroundedAnswerable(snippet)) {
            val refusal = DocumentDateIntelligence.unreadableContentMessage(
                query = query,
                sourceFileName = sourceFileName,
                sourceModifiedAt = sourceModifiedAt,
            )
            return mapOf("userMessage" to sanitizeSnippet(refusal))
        }
        val answer = DocumentDateIntelligence.formatAnswer(
            query = query,
            snippet = snippet,
            sourceFileName = sourceFileName,
            sourceModifiedAt = sourceModifiedAt,
        )
        return mapOf(
            "contentSnippet" to sanitizeSnippet(snippet),
            "answer" to sanitizeSnippet(answer),
            "userMessage" to sanitizeSnippet(answer),
        )
    }

    fun decodeFirstShareableDocument(itemsEncoded: String): ShareableDocumentHit? {
        val firstItem = itemsEncoded.split(ITEM_SEP).firstOrNull()?.takeIf { it.isNotBlank() } ?: return null
        val fields = firstItem.split(FIELD_SEP)
        return when {
            // encodeDocumentHits: id, path, name, extension, mimeType, modifiedAt, score, snippet
            fields.size >= 7 && looksLikeShareableUriOrPath(fields[1]) ->
                ShareableDocumentHit(
                    uri = toShareableUri(fields[1]),
                    mimeType = fields[4].ifBlank { "*/*" },
                    name = fields[2].ifBlank { null },
                    contentSnippet = fields.getOrNull(7)?.takeIf { it.isNotBlank() },
                )
            // encodeSemanticHits document: objectId, "document", score, title, snippetOrUri, contentSnippet
            fields.size >= 5 && fields[1].equals("document", ignoreCase = true) &&
                looksLikeShareableUriOrPath(fields[4]) ->
                ShareableDocumentHit(
                    uri = toShareableUri(fields[4]),
                    mimeType = "*/*",
                    name = fields[3].ifBlank { null },
                    contentSnippet = fields.getOrNull(5)?.takeIf { it.isNotBlank() },
                )
            else -> null
        }
    }

    private fun looksLikeShareableUriOrPath(raw: String): Boolean {
        if (raw.isBlank()) return false
        // content://…, file://…, and Java File.toURI() form file:/absolute/path
        if (raw.contains("://") || raw.startsWith("file:/")) return true
        // Absolute filesystem paths from local scans
        return raw.startsWith("/") && raw.length > 1
    }

    private fun toShareableUri(raw: String): String =
        when {
            raw.contains("://") -> raw
            // Normalize Java File.toURI() ("file:/path") to FileProvider-friendly file://path
            raw.startsWith("file:/") && !raw.startsWith("file://") ->
                "file://" + raw.removePrefix("file:")
            raw.startsWith("/") -> "file://$raw"
            else -> raw
        }

    private fun joinFields(vararg values: Any?): String =
        values.joinToString(FIELD_SEP) { value -> value?.toString().orEmpty() }

    /** Keeps snippets from corrupting the field/item separators used by the wire format. */
    private fun sanitizeSnippet(snippet: String?): String =
        snippet.orEmpty()
            .replace(FIELD_SEP, " ")
            .replace(ITEM_SEP, "/")
}
