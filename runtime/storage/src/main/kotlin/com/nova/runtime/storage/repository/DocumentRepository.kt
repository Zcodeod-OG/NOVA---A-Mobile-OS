package com.nova.runtime.storage.repository

/** DSS §8 — repository layer stub; business logic prohibited in DAOs. */
interface DocumentRepository {
    suspend fun insert(documentId: String)
    suspend fun getById(documentId: String): Map<String, String>?
    suspend fun search(query: String): List<Map<String, String>>
}

class DocumentRepositoryStub : DocumentRepository {
    override suspend fun insert(documentId: String) {
        // TODO(Sprint 1): delegate to StorageCoordinator + DocumentDao
    }

    override suspend fun getById(documentId: String): Map<String, String>? = null

    override suspend fun search(query: String): List<Map<String, String>> = emptyList()
}
