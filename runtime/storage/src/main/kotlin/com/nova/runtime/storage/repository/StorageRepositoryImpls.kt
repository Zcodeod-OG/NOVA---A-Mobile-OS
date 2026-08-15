package com.nova.runtime.storage.repository

import com.nova.runtime.storage.dao.ContactDao
import com.nova.runtime.storage.dao.DocumentDao
import com.nova.runtime.storage.dao.EmbeddingDao
import com.nova.runtime.storage.dao.ExecutionHistoryDao
import com.nova.runtime.storage.dao.MessageDao
import com.nova.runtime.storage.dao.PhotoDao
import com.nova.runtime.storage.dao.PreferenceDao
import com.nova.runtime.storage.dao.ProjectDao
import com.nova.runtime.storage.dao.SessionDao
import com.nova.runtime.storage.entities.ContactEntity
import com.nova.runtime.storage.entities.DocumentEntity
import com.nova.runtime.storage.entities.EmbeddingEntity
import com.nova.runtime.storage.entities.ExecutionHistoryEntity
import com.nova.runtime.storage.entities.MessageEntity
import com.nova.runtime.storage.entities.PhotoEntity
import com.nova.runtime.storage.entities.PreferenceEntity
import com.nova.runtime.storage.entities.ProjectEntity
import com.nova.runtime.storage.entities.SessionEntity
import java.util.UUID

class DocumentRepositoryImpl(
    private val documentDao: DocumentDao,
) : DocumentRepository {
    override suspend fun insert(document: DocumentEntity) = documentDao.insert(document)

    override suspend fun update(document: DocumentEntity) = documentDao.update(document)

    override suspend fun delete(id: UUID) {
        documentDao.getById(id)?.let { documentDao.delete(it) }
    }

    override suspend fun getById(id: UUID): DocumentEntity? = documentDao.getById(id)

    override fun observeById(id: UUID) = documentDao.observeById(id)

    override suspend fun search(query: String): List<DocumentEntity> = documentDao.searchByName(query)
}

class PhotoRepositoryImpl(
    private val photoDao: PhotoDao,
) : PhotoRepository {
    override suspend fun insert(photo: PhotoEntity) = photoDao.insert(photo)

    override suspend fun update(photo: PhotoEntity) = photoDao.update(photo)

    override suspend fun delete(id: UUID) {
        photoDao.getById(id)?.let { photoDao.delete(it) }
    }

    override suspend fun getById(id: UUID): PhotoEntity? = photoDao.getById(id)

    override fun observeById(id: UUID) = photoDao.observeById(id)

    override suspend fun search(query: String): List<PhotoEntity> = photoDao.searchByOcr(query)
}

class ContactRepositoryImpl(
    private val contactDao: ContactDao,
) : ContactRepository {
    override suspend fun insert(contact: ContactEntity) = contactDao.insert(contact)

    override suspend fun update(contact: ContactEntity) = contactDao.update(contact)

    override suspend fun delete(id: UUID) {
        contactDao.getById(id)?.let { contactDao.delete(it) }
    }

    override suspend fun getById(id: UUID): ContactEntity? = contactDao.getById(id)

    override fun observeById(id: UUID) = contactDao.observeById(id)

    override suspend fun search(query: String): List<ContactEntity> = contactDao.search(query)
}

class ProjectRepositoryImpl(
    private val projectDao: ProjectDao,
) : ProjectRepository {
    override suspend fun insert(project: ProjectEntity) = projectDao.insert(project)

    override suspend fun update(project: ProjectEntity) = projectDao.update(project)

    override suspend fun delete(id: UUID) {
        projectDao.getById(id)?.let { projectDao.delete(it) }
    }

    override suspend fun getById(id: UUID): ProjectEntity? = projectDao.getById(id)

    override fun observeById(id: UUID) = projectDao.observeById(id)

    override suspend fun search(query: String): List<ProjectEntity> = projectDao.search(query)
}

class SessionRepositoryImpl(
    private val sessionDao: SessionDao,
) : SessionRepository {
    override suspend fun insert(session: SessionEntity) = sessionDao.insert(session)

    override suspend fun update(session: SessionEntity) = sessionDao.update(session)

    override suspend fun delete(sessionId: UUID) {
        sessionDao.getById(sessionId)?.let { sessionDao.delete(it) }
    }

    override suspend fun getById(sessionId: UUID): SessionEntity? = sessionDao.getById(sessionId)

    override suspend fun getByTraceId(traceId: UUID): List<SessionEntity> = sessionDao.getByTraceId(traceId)

    override fun observeById(sessionId: UUID) = sessionDao.observeById(sessionId)
}

class PreferenceRepositoryImpl(
    private val preferenceDao: PreferenceDao,
) : PreferenceRepository {
    override suspend fun insert(preference: PreferenceEntity) = preferenceDao.insert(preference)

    override suspend fun update(preference: PreferenceEntity) = preferenceDao.update(preference)

    override suspend fun delete(key: String) {
        preferenceDao.getByKey(key)?.let { preferenceDao.delete(it) }
    }

    override suspend fun getByKey(key: String): PreferenceEntity? = preferenceDao.getByKey(key)

    override fun observeByKey(key: String) = preferenceDao.observeByKey(key)
}

class ExecutionHistoryRepositoryImpl(
    private val executionHistoryDao: ExecutionHistoryDao,
) : ExecutionHistoryRepository {
    override suspend fun insert(execution: ExecutionHistoryEntity) = executionHistoryDao.insert(execution)

    override suspend fun update(execution: ExecutionHistoryEntity) = executionHistoryDao.update(execution)

    override suspend fun delete(id: UUID) {
        executionHistoryDao.getById(id)?.let { executionHistoryDao.delete(it) }
    }

    override suspend fun getById(id: UUID): ExecutionHistoryEntity? = executionHistoryDao.getById(id)

    override fun observeById(id: UUID) = executionHistoryDao.observeById(id)
}

class MessageRepositoryImpl(
    private val messageDao: MessageDao,
) : MessageRepository {
    override suspend fun insert(message: MessageEntity): Boolean = messageDao.insert(message) != -1L

    override suspend fun update(message: MessageEntity) = messageDao.update(message)

    override suspend fun getById(id: UUID): MessageEntity? = messageDao.getById(id)

    override suspend fun getByExternalId(externalId: String): MessageEntity? =
        messageDao.getByExternalId(externalId)

    override suspend fun listByChannel(channel: String, limit: Int): List<MessageEntity> =
        messageDao.listByChannel(channel, limit)

    override suspend fun listUnindexed(limit: Int): List<MessageEntity> = messageDao.listUnindexed(limit)

    override suspend fun search(channel: String, query: String, limit: Int): List<MessageEntity> =
        messageDao.search(channel, query, limit)

    override suspend fun countByChannel(channel: String): Int = messageDao.countByChannel(channel)

    override suspend fun getRecent(limit: Int): List<MessageEntity> = messageDao.getRecent(limit)

    override suspend fun getHighImportance(limit: Int): List<MessageEntity> =
        messageDao.getHighImportance(limit)

    override suspend fun getSince(since: Long): List<MessageEntity> = messageDao.getSince(since)
}

class EmbeddingRepositoryImpl(
    private val embeddingDao: EmbeddingDao,
) : EmbeddingRepository {
    override suspend fun insert(embedding: EmbeddingEntity) = embeddingDao.insert(embedding)

    override suspend fun update(embedding: EmbeddingEntity) = embeddingDao.update(embedding)

    override suspend fun delete(embeddingId: UUID) {
        embeddingDao.getById(embeddingId)?.let { embeddingDao.delete(it) }
    }

    override suspend fun getById(embeddingId: UUID): EmbeddingEntity? = embeddingDao.getById(embeddingId)

    override fun observeById(embeddingId: UUID) = embeddingDao.observeById(embeddingId)

    override suspend fun listWithPersistedVectors(): List<EmbeddingEntity> =
        embeddingDao.listWithPersistedVectors()
}
