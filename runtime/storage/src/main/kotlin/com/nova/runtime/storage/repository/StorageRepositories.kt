package com.nova.runtime.storage.repository

import com.nova.runtime.storage.entities.ContactEntity
import com.nova.runtime.storage.entities.DocumentEntity
import com.nova.runtime.storage.entities.EmbeddingEntity
import com.nova.runtime.storage.entities.ExecutionHistoryEntity
import com.nova.runtime.storage.entities.MessageEntity
import com.nova.runtime.storage.entities.PhotoEntity
import com.nova.runtime.storage.entities.PreferenceEntity
import com.nova.runtime.storage.entities.ProjectEntity
import com.nova.runtime.storage.entities.SessionEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface DocumentRepository {
    suspend fun insert(document: DocumentEntity)

    suspend fun update(document: DocumentEntity)

    suspend fun delete(id: UUID)

    suspend fun getById(id: UUID): DocumentEntity?

    fun observeById(id: UUID): Flow<DocumentEntity?>

    suspend fun search(query: String): List<DocumentEntity>
}

interface PhotoRepository {
    suspend fun insert(photo: PhotoEntity)

    suspend fun update(photo: PhotoEntity)

    suspend fun delete(id: UUID)

    suspend fun getById(id: UUID): PhotoEntity?

    fun observeById(id: UUID): Flow<PhotoEntity?>

    suspend fun search(query: String): List<PhotoEntity>
}

interface ContactRepository {
    suspend fun insert(contact: ContactEntity)

    suspend fun update(contact: ContactEntity)

    suspend fun delete(id: UUID)

    suspend fun getById(id: UUID): ContactEntity?

    fun observeById(id: UUID): Flow<ContactEntity?>

    suspend fun search(query: String): List<ContactEntity>
}

interface ProjectRepository {
    suspend fun insert(project: ProjectEntity)

    suspend fun update(project: ProjectEntity)

    suspend fun delete(id: UUID)

    suspend fun getById(id: UUID): ProjectEntity?

    fun observeById(id: UUID): Flow<ProjectEntity?>

    suspend fun search(query: String): List<ProjectEntity>
}

interface SessionRepository {
    suspend fun insert(session: SessionEntity)

    suspend fun update(session: SessionEntity)

    suspend fun delete(sessionId: UUID)

    suspend fun getById(sessionId: UUID): SessionEntity?

    suspend fun getByTraceId(traceId: UUID): List<SessionEntity>

    fun observeById(sessionId: UUID): Flow<SessionEntity?>
}

interface PreferenceRepository {
    suspend fun insert(preference: PreferenceEntity)

    suspend fun update(preference: PreferenceEntity)

    suspend fun delete(key: String)

    suspend fun getByKey(key: String): PreferenceEntity?

    fun observeByKey(key: String): Flow<PreferenceEntity?>
}

interface ExecutionHistoryRepository {
    suspend fun insert(execution: ExecutionHistoryEntity)

    suspend fun update(execution: ExecutionHistoryEntity)

    suspend fun delete(id: UUID)

    suspend fun getById(id: UUID): ExecutionHistoryEntity?

    fun observeById(id: UUID): Flow<ExecutionHistoryEntity?>
}

interface EmbeddingRepository {
    suspend fun insert(embedding: EmbeddingEntity)

    suspend fun update(embedding: EmbeddingEntity)

    suspend fun delete(embeddingId: UUID)

    suspend fun getById(embeddingId: UUID): EmbeddingEntity?

    fun observeById(embeddingId: UUID): Flow<EmbeddingEntity?>

    suspend fun listWithPersistedVectors(): List<EmbeddingEntity>
}

interface MessageRepository {
    suspend fun insert(message: MessageEntity): Boolean

    suspend fun update(message: MessageEntity)

    suspend fun getById(id: UUID): MessageEntity?

    suspend fun getByExternalId(externalId: String): MessageEntity?

    suspend fun listByChannel(channel: String, limit: Int = 50): List<MessageEntity>

    suspend fun listUnindexed(limit: Int = 32): List<MessageEntity>

    suspend fun search(channel: String, query: String, limit: Int = 25): List<MessageEntity>

    suspend fun countByChannel(channel: String): Int

    suspend fun getRecent(limit: Int): List<MessageEntity>

    suspend fun getHighImportance(limit: Int): List<MessageEntity>

    suspend fun getSince(since: Long): List<MessageEntity>
}
