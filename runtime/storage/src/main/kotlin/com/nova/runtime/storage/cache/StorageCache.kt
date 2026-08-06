package com.nova.runtime.storage.cache

/** DPS §4.5 — cache abstraction with importance-aware eviction. */
interface StorageCache<K, V> {
    suspend fun get(key: K): V?

    suspend fun put(key: K, value: V, importance: Int = 0)

    suspend fun remove(key: K)

    suspend fun clear()

    fun size(): Int
}
