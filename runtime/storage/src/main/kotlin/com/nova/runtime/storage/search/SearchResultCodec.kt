package com.nova.runtime.storage.search

data class ShareableDocumentHit(
    val uri: String,
    val mimeType: String,
    val name: String? = null,
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
                )
            },
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
                )
            },
        )

    fun decodeFirstShareableDocument(itemsEncoded: String): ShareableDocumentHit? {
        val firstItem = itemsEncoded.split(ITEM_SEP).firstOrNull()?.takeIf { it.isNotBlank() } ?: return null
        val fields = firstItem.split(FIELD_SEP)
        return when {
            fields.size >= 7 && fields[1].contains("://") ->
                ShareableDocumentHit(
                    uri = fields[1],
                    mimeType = fields[4].ifBlank { "*/*" },
                    name = fields[2].ifBlank { null },
                )
            fields.size >= 5 && fields[1].equals("document", ignoreCase = true) && fields[4].contains("://") ->
                ShareableDocumentHit(
                    uri = fields[4],
                    mimeType = "*/*",
                    name = fields[3].ifBlank { null },
                )
            else -> null
        }
    }

    private fun joinFields(vararg values: Any?): String =
        values.joinToString(FIELD_SEP) { value -> value?.toString().orEmpty() }
}
