package com.nova.runtime.storage.search

/** Serializes typed search pages into capability/execution output maps. */
object SearchResultCodec {
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
            "items" to page.items.joinToString("|") { hit ->
                listOf(
                    hit.id,
                    hit.uri,
                    hit.takenAt,
                    hit.ocrText.orEmpty(),
                    hit.matchSource.name,
                    hit.score,
                ).joinToString(":")
            },
        )

    fun encodeDocumentHits(page: SearchPage<DocumentSearchHit>): Map<String, String> =
        encodePage(page) + mapOf(
            "items" to page.items.joinToString("|") { hit ->
                listOf(
                    hit.id,
                    hit.path,
                    hit.name,
                    hit.extension,
                    hit.mimeType,
                    hit.modifiedAt,
                    hit.score,
                ).joinToString(":")
            },
        )

    fun encodeSemanticHits(page: SearchPage<SemanticSearchHit>): Map<String, String> =
        encodePage(page) + mapOf(
            "items" to page.items.joinToString("|") { hit ->
                listOf(
                    hit.objectId,
                    hit.objectType,
                    hit.score,
                    hit.title,
                    hit.snippet.orEmpty(),
                ).joinToString(":")
            },
        )
}
