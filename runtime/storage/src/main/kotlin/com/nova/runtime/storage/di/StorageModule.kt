package com.nova.runtime.storage.di

import android.content.Context
import com.nova.runtime.storage.cache.LruStorageCache
import com.nova.runtime.storage.cache.StorageCache
import com.nova.runtime.storage.coordinator.StorageCoordinatorImpl
import com.nova.runtime.storage.database.NovaDatabase
import com.nova.runtime.storage.database.NovaDatabaseProvider
import com.nova.runtime.storage.graph.KnowledgeGraphStore
import com.nova.runtime.storage.graph.NoOpKnowledgeGraphStore
import com.nova.runtime.storage.repository.ContactRepository
import com.nova.runtime.storage.repository.ContactRepositoryImpl
import com.nova.runtime.storage.repository.DocumentRepository
import com.nova.runtime.storage.repository.DocumentRepositoryImpl
import com.nova.runtime.storage.repository.EmbeddingRepository
import com.nova.runtime.storage.repository.EmbeddingRepositoryImpl
import com.nova.runtime.storage.repository.ExecutionHistoryRepository
import com.nova.runtime.storage.repository.ExecutionHistoryRepositoryImpl
import com.nova.runtime.storage.repository.PhotoRepository
import com.nova.runtime.storage.repository.PhotoRepositoryImpl
import com.nova.runtime.storage.repository.PreferenceRepository
import com.nova.runtime.storage.repository.PreferenceRepositoryImpl
import com.nova.runtime.storage.repository.ProjectRepository
import com.nova.runtime.storage.repository.ProjectRepositoryImpl
import com.nova.runtime.storage.repository.SessionRepository
import com.nova.runtime.storage.repository.SessionRepositoryImpl
import com.nova.runtime.storage.coordinator.StorageCoordinator
import com.nova.runtime.storage.search.DocumentSearchService
import com.nova.runtime.storage.vector.NoOpVectorIndex
import com.nova.runtime.storage.vector.VectorIndex
import org.koin.dsl.module

/** Koin DI wiring for the Data Platform per DPS §4. */
fun storageModule(context: Context) =
    module {
        single<NovaDatabase> { NovaDatabaseProvider.create(context) }
        single { get<NovaDatabase>().documentDao() }
        single { get<NovaDatabase>().photoDao() }
        single { get<NovaDatabase>().contactDao() }
        single { get<NovaDatabase>().projectDao() }
        single { get<NovaDatabase>().sessionDao() }
        single { get<NovaDatabase>().preferenceDao() }
        single { get<NovaDatabase>().executionHistoryDao() }
        single { get<NovaDatabase>().embeddingDao() }

        single<DocumentRepository> { DocumentRepositoryImpl(get()) }
        single<PhotoRepository> { PhotoRepositoryImpl(get()) }
        single<ContactRepository> { ContactRepositoryImpl(get()) }
        single<ProjectRepository> { ProjectRepositoryImpl(get()) }
        single<SessionRepository> { SessionRepositoryImpl(get()) }
        single<PreferenceRepository> { PreferenceRepositoryImpl(get()) }
        single<ExecutionHistoryRepository> { ExecutionHistoryRepositoryImpl(get()) }
        single<EmbeddingRepository> { EmbeddingRepositoryImpl(get()) }

        single { DocumentSearchService(documentDao = get(), logger = get()) }

        single<StorageCache<String, Map<String, String>>> { LruStorageCache(maxSize = 256) }
        single<VectorIndex> { NoOpVectorIndex() }
        single<KnowledgeGraphStore> { NoOpKnowledgeGraphStore() }

        single<StorageCoordinator> {
            StorageCoordinatorImpl(
                database = get(),
                documentRepository = get(),
                photoRepository = get(),
                contactRepository = get(),
                projectRepository = get(),
                sessionRepository = get(),
                preferenceRepository = get(),
                executionHistoryRepository = get(),
                embeddingRepository = get(),
                cache = get(),
            )
        }
    }
