package com.nova.runtime.storage.cache

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** DPS §4.5 — in-memory LRU cache with importance score for eviction. */
class LruStorageCache<K, V>(
    private val maxSize: Int,
) : StorageCache<K, V> {
    private data class Entry<V>(
        val value: V,
        val importance: Int,
        var lastAccess: Long,
    )

    private val accessCounter = AtomicLong()
    private val entries = ConcurrentHashMap<K, Entry<V>>()

    override suspend fun get(key: K): V? {
        val entry = entries[key] ?: return null
        entry.lastAccess = accessCounter.incrementAndGet()
        return entry.value
    }

    override suspend fun put(key: K, value: V, importance: Int) {
        entries[key] = Entry(value, importance, accessCounter.incrementAndGet())
        evictIfNeeded()
    }

    override suspend fun remove(key: K) {
        entries.remove(key)
    }

    override suspend fun clear() {
        entries.clear()
    }

    override fun size(): Int = entries.size

    private fun evictIfNeeded() {
        while (entries.size > maxSize) {
            val victimKey =
                entries.entries
                    .minWithOrNull(
                        compareBy<Map.Entry<K, Entry<V>>> { it.value.importance }
                            .thenBy { it.value.lastAccess },
                    )
                    ?.key
                    ?: break
            entries.remove(victimKey)
        }
    }
}
