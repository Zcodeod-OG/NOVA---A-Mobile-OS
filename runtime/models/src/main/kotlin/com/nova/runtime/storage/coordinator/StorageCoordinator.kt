package com.nova.runtime.storage.coordinator

/** DPS §4.1 — storage abstraction (interface in portable models layer). */
interface StorageCoordinator {
    suspend fun store(key: String, value: Map<String, String>)
    suspend fun retrieve(key: String): Map<String, String>?
    suspend fun query(collection: String, filter: Map<String, String>): List<Map<String, String>>
    suspend fun delete(key: String)
    suspend fun update(key: String, value: Map<String, String>)
    suspend fun transaction(block: suspend () -> Unit)
}
