package com.nova.runtime.ai.native.search.di

import com.nova.runtime.ai.native.indexing.EmbeddingIndexer
import com.nova.runtime.ai.native.ingestion.FullDeviceIndexer
import com.nova.runtime.ai.native.ingestion.IndexingCheckpointStore
import com.nova.runtime.ai.native.ingestion.MediaStoreIngestionService
import com.nova.runtime.ai.native.search.SearchIndexPipeline
import com.nova.runtime.ai.native.search.SemanticSearchService
import com.nova.runtime.ai.native.search.provider.DocumentSearchCapabilityProvider
import com.nova.runtime.ai.native.search.provider.PhotoSearchCapabilityProvider
import com.nova.runtime.ai.native.search.provider.SemanticSearchCapabilityProvider
import com.nova.runtime.capability.provider.CapabilityProvider
import com.nova.runtime.capability.registry.CapabilityRegistry
import com.nova.runtime.storage.search.PhotoSearchService
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Koin wiring for search services and capability providers. */
val searchModule = module {
    single {
        PhotoSearchService(
            photoDao = get(),
            mediaStoreQuery = get(),
            logger = get(),
        )
    }

    single {
        SearchIndexPipeline(
            embeddingIndexer = get<EmbeddingIndexer>(),
            photoDao = get(),
            documentDao = get(),
            photoRepository = get(),
            documentRepository = get(),
            photoImageLoader = get(),
        )
    }

    single { IndexingCheckpointStore(androidContext()) }

    single {
        MediaStoreIngestionService(
            mediaStoreQuery = get(),
            downloadsQuery = get(),
            documentsQuery = get(),
            photoDao = get(),
            documentDao = get(),
            photoRepository = get(),
            documentRepository = get(),
            embeddingIndexer = get(),
            searchIndexPipeline = get(),
            photoImageLoader = get(),
            logger = get(),
        )
    }

    single {
        FullDeviceIndexer(
            ingestionService = get(),
            checkpointStore = get(),
            eventBus = get(),
            logger = get(),
        )
    }

    single {
        SemanticSearchService(
            embeddingGenerator = get(),
            imageEmbeddingGenerator = get(),
            vectorIndex = get(),
            photoRepository = get(),
            documentRepository = get(),
            searchIndexPipeline = get(),
            mediaStoreIngestionService = get(),
            fullDeviceIndexer = get(),
            logger = get(),
        )
    }

    single {
        PhotoSearchCapabilityProvider(
            photoSearchService = get(),
            semanticSearchService = get(),
        )
    }
    single {
        DocumentSearchCapabilityProvider(
            documentSearchService = get(),
            semanticSearchService = get(),
        )
    }
    single { SemanticSearchCapabilityProvider(semanticSearchService = get()) }

    single {
        SearchCapabilityRegistrar(
            registry = get(),
            providers = listOf(
                get<PhotoSearchCapabilityProvider>(),
                get<DocumentSearchCapabilityProvider>(),
                get<SemanticSearchCapabilityProvider>(),
            ),
        )
    }
}

/** Registers search capability providers with the runtime registry at startup. */
class SearchCapabilityRegistrar(
    registry: CapabilityRegistry,
    providers: List<CapabilityProvider>,
) {
    init {
        providers.forEach { provider -> registry.register(provider) }
    }
}
