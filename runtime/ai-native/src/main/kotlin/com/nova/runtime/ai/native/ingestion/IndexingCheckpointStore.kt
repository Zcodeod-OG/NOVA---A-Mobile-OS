package com.nova.runtime.ai.native.ingestion

import android.content.Context

/** Persists incremental indexing cursors across app restarts (DPS §9). */
class IndexingCheckpointStore(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getOffset(category: IndexCategory): Int =
        prefs.getInt(offsetKey(category), 0).coerceAtLeast(0)

    fun setOffset(category: IndexCategory, offset: Int) {
        prefs.edit().putInt(offsetKey(category), offset.coerceAtLeast(0)).apply()
    }

    fun getCurrentCategory(): IndexCategory {
        val name = prefs.getString(KEY_CURRENT_CATEGORY, IndexCategory.PHOTOS.name)
        return runCatching { IndexCategory.valueOf(name ?: IndexCategory.PHOTOS.name) }
            .getOrDefault(IndexCategory.PHOTOS)
    }

    fun setCurrentCategory(category: IndexCategory) {
        prefs.edit().putString(KEY_CURRENT_CATEGORY, category.name).apply()
    }

    fun advanceToNextCategory(): IndexCategory {
        val next = getCurrentCategory().next()
        setCurrentCategory(next)
        setOffset(next, 0)
        return next
    }

    fun getTotalIndexed(): Long = prefs.getLong(KEY_TOTAL_INDEXED, 0L)

    fun incrementTotalIndexed(count: Int) {
        if (count <= 0) return
        prefs.edit().putLong(KEY_TOTAL_INDEXED, getTotalIndexed() + count).apply()
    }

    fun markFullCycleComplete() {
        prefs.edit().putLong(KEY_LAST_FULL_CYCLE_AT, System.currentTimeMillis()).apply()
    }

    fun getLastFullCycleAt(): Long = prefs.getLong(KEY_LAST_FULL_CYCLE_AT, 0L)

    fun markPrioritySyncAt() {
        prefs.edit().putLong(KEY_LAST_PRIORITY_SYNC_AT, System.currentTimeMillis()).apply()
    }

    fun getLastPrioritySyncAt(): Long = prefs.getLong(KEY_LAST_PRIORITY_SYNC_AT, 0L)

    fun resetCategory(category: IndexCategory) {
        setOffset(category, 0)
    }

    private fun offsetKey(category: IndexCategory): String = "offset_${category.name}"

    private companion object {
        const val PREFS_NAME = "nova_indexing_checkpoints"
        const val KEY_CURRENT_CATEGORY = "current_category"
        const val KEY_TOTAL_INDEXED = "total_indexed"
        const val KEY_LAST_FULL_CYCLE_AT = "last_full_cycle_at"
        const val KEY_LAST_PRIORITY_SYNC_AT = "last_priority_sync_at"
    }
}

enum class IndexCategory {
    PHOTOS,
    VIDEOS,
    AUDIO,
    DOWNLOADS,
    DOCUMENTS,
    ;

    /** Short label for progress UI (e.g. Photos, Downloads). */
    val displayName: String
        get() =
            when (this) {
                PHOTOS -> "Photos"
                VIDEOS -> "Videos"
                AUDIO -> "Audio"
                DOWNLOADS -> "Downloads"
                DOCUMENTS -> "Documents"
            }

    fun next(): IndexCategory {
        val values = entries
        val nextIndex = (ordinal + 1) % values.size
        return values[nextIndex]
    }
}
