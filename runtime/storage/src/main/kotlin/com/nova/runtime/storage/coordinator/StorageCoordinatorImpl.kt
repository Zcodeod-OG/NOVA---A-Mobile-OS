package com.nova.runtime.storage.coordinator

import androidx.room.withTransaction
import com.nova.runtime.storage.cache.StorageCache
import com.nova.runtime.storage.database.NovaDatabase
import com.nova.runtime.storage.repository.ContactRepository
import com.nova.runtime.storage.repository.DocumentRepository
import com.nova.runtime.storage.repository.EmbeddingRepository
import com.nova.runtime.storage.repository.ExecutionHistoryRepository
import com.nova.runtime.storage.repository.PhotoRepository
import com.nova.runtime.storage.repository.PreferenceRepository
import com.nova.runtime.storage.repository.ProjectRepository
import com.nova.runtime.storage.repository.SessionRepository
import com.nova.runtime.storage.coordinator.EntityMapCodec.contactFromMap
import com.nova.runtime.storage.coordinator.EntityMapCodec.contactToMap
import com.nova.runtime.storage.coordinator.EntityMapCodec.documentFromMap
import com.nova.runtime.storage.coordinator.EntityMapCodec.documentToMap
import com.nova.runtime.storage.coordinator.EntityMapCodec.embeddingFromMap
import com.nova.runtime.storage.coordinator.EntityMapCodec.embeddingToMap
import com.nova.runtime.storage.coordinator.EntityMapCodec.executionFromMap
import com.nova.runtime.storage.coordinator.EntityMapCodec.executionToMap
import com.nova.runtime.storage.coordinator.EntityMapCodec.photoFromMap
import com.nova.runtime.storage.coordinator.EntityMapCodec.photoToMap
import com.nova.runtime.storage.coordinator.EntityMapCodec.preferenceFromMap
import com.nova.runtime.storage.coordinator.EntityMapCodec.preferenceToMap
import com.nova.runtime.storage.coordinator.EntityMapCodec.projectFromMap
import com.nova.runtime.storage.coordinator.EntityMapCodec.projectToMap
import com.nova.runtime.storage.coordinator.EntityMapCodec.sessionFromMap
import com.nova.runtime.storage.coordinator.EntityMapCodec.sessionToMap
import java.util.UUID

/** DPS §4.1 — central storage orchestration over SQLite, cache, and deferred indexes. */
class StorageCoordinatorImpl(
    private val database: NovaDatabase,
    private val documentRepository: DocumentRepository,
    private val photoRepository: PhotoRepository,
    private val contactRepository: ContactRepository,
    private val projectRepository: ProjectRepository,
    private val sessionRepository: SessionRepository,
    private val preferenceRepository: PreferenceRepository,
    private val executionHistoryRepository: ExecutionHistoryRepository,
    private val embeddingRepository: EmbeddingRepository,
    private val cache: StorageCache<String, Map<String, String>>,
) : StorageCoordinator {
    override suspend fun store(key: String, value: Map<String, String>) {
        val collection = value["collection"] ?: StorageCollections.DOCUMENTS
        when (collection) {
            StorageCollections.DOCUMENTS ->
                documentRepository.insert(documentFromMap(key, value))
            StorageCollections.PHOTOS ->
                photoRepository.insert(photoFromMap(key, value))
            StorageCollections.CONTACTS ->
                contactRepository.insert(contactFromMap(key, value))
            StorageCollections.PROJECTS ->
                projectRepository.insert(projectFromMap(key, value))
            StorageCollections.SESSIONS ->
                sessionRepository.insert(sessionFromMap(key, value))
            StorageCollections.PREFERENCES ->
                preferenceRepository.insert(preferenceFromMap(key, value))
            StorageCollections.EXECUTION_HISTORY ->
                executionHistoryRepository.insert(executionFromMap(key, value))
            StorageCollections.EMBEDDINGS ->
                embeddingRepository.insert(embeddingFromMap(key, value))
            else -> error("Unknown collection: $collection")
        }
        cache.put(key, value)
    }

    override suspend fun retrieve(key: String): Map<String, String>? {
        cache.get(key)?.let { return it }

        return retrieveFromCollection(StorageCollections.DOCUMENTS, key)
            ?: retrieveFromCollection(StorageCollections.PHOTOS, key)
            ?: retrieveFromCollection(StorageCollections.CONTACTS, key)
            ?: retrieveFromCollection(StorageCollections.PROJECTS, key)
            ?: retrieveFromCollection(StorageCollections.SESSIONS, key)
            ?: retrieveFromCollection(StorageCollections.PREFERENCES, key)
            ?: retrieveFromCollection(StorageCollections.EXECUTION_HISTORY, key)
            ?: retrieveFromCollection(StorageCollections.EMBEDDINGS, key)
    }

    override suspend fun query(collection: String, filter: Map<String, String>): List<Map<String, String>> {
        val query = filter["query"].orEmpty()
        return when (collection) {
            StorageCollections.DOCUMENTS ->
                documentRepository.search(query).map { documentToMap(it) }
            StorageCollections.PHOTOS ->
                photoRepository.search(query).map { photoToMap(it) }
            StorageCollections.CONTACTS ->
                contactRepository.search(query).map { contactToMap(it) }
            StorageCollections.PROJECTS ->
                projectRepository.search(query).map { projectToMap(it) }
            StorageCollections.SESSIONS ->
                filter["traceId"]?.let { traceId ->
                    sessionRepository.getByTraceId(UUID.fromString(traceId)).map { sessionToMap(it) }
                } ?: emptyList()
            StorageCollections.PREFERENCES ->
                preferenceRepository.getByKey(filter["key"] ?: query)?.let { listOf(preferenceToMap(it)) }
                    ?: emptyList()
            StorageCollections.EXECUTION_HISTORY ->
                executionHistoryRepository.getById(UUID.fromString(filter["id"] ?: keyOrEmpty(filter)))
                    ?.let { listOf(executionToMap(it)) }
                    ?: emptyList()
            StorageCollections.EMBEDDINGS ->
                embeddingRepository.getById(UUID.fromString(filter["embeddingId"] ?: keyOrEmpty(filter)))
                    ?.let { listOf(embeddingToMap(it)) }
                    ?: emptyList()
            else -> emptyList()
        }
    }

    private fun keyOrEmpty(filter: Map<String, String>): String = filter["id"] ?: filter["key"] ?: ""

    override suspend fun delete(key: String) {
        val id = runCatching { UUID.fromString(key) }.getOrNull()
        if (id != null) {
            documentRepository.delete(id)
            photoRepository.delete(id)
            contactRepository.delete(id)
            projectRepository.delete(id)
            sessionRepository.delete(id)
            executionHistoryRepository.delete(id)
            embeddingRepository.delete(id)
        }
        preferenceRepository.delete(key)
        cache.remove(key)
    }

    override suspend fun update(key: String, value: Map<String, String>) {
        store(key, value)
    }

    override suspend fun transaction(block: suspend () -> Unit) {
        database.withTransaction { block() }
    }

    private suspend fun retrieveFromCollection(collection: String, key: String): Map<String, String>? {
        val id = runCatching { UUID.fromString(key) }.getOrNull()
        val result =
            when (collection) {
                StorageCollections.DOCUMENTS -> id?.let { documentRepository.getById(it) }?.let { documentToMap(it) }
                StorageCollections.PHOTOS -> id?.let { photoRepository.getById(it) }?.let { photoToMap(it) }
                StorageCollections.CONTACTS -> id?.let { contactRepository.getById(it) }?.let { contactToMap(it) }
                StorageCollections.PROJECTS -> id?.let { projectRepository.getById(it) }?.let { projectToMap(it) }
                StorageCollections.SESSIONS -> id?.let { sessionRepository.getById(it) }?.let { sessionToMap(it) }
                StorageCollections.PREFERENCES ->
                    preferenceRepository.getByKey(key)?.let { preferenceToMap(it) }
                StorageCollections.EXECUTION_HISTORY ->
                    id?.let { executionHistoryRepository.getById(it) }?.let { executionToMap(it) }
                StorageCollections.EMBEDDINGS ->
                    id?.let { embeddingRepository.getById(it) }?.let { embeddingToMap(it) }
                else -> null
            }
        if (result != null) {
            cache.put(key, result)
        }
        return result
    }
}
