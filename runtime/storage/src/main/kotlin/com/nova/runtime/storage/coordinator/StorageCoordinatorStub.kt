package com.nova.runtime.storage.coordinator

class StorageCoordinatorStub : StorageCoordinator {
    override suspend fun store(key: String, value: Map<String, String>) { /* TODO */ }
    override suspend fun retrieve(key: String): Map<String, String>? = null
    override suspend fun query(collection: String, filter: Map<String, String>): List<Map<String, String>> = emptyList()
    override suspend fun delete(key: String) { /* TODO */ }
    override suspend fun update(key: String, value: Map<String, String>) { /* TODO */ }
    override suspend fun transaction(block: suspend () -> Unit) { block() }
}
