package com.nova.runtime.ai.native.search.di

import com.nova.runtime.ai.native.indexing.EmbeddingIndexer
import com.nova.runtime.ai.native.search.SearchIndexPipeline
import com.nova.runtime.ai.native.search.SemanticSearchService
import com.nova.runtime.ai.native.search.provider.DocumentSearchCapabilityProvider
import com.nova.runtime.ai.native.search.provider.PhotoSearchCapabilityProvider
import com.nova.runtime.ai.native.search.provider.SemanticSearchCapabilityProvider
import com.nova.runtime.capability.provider.CapabilityProvider
import com.nova.runtime.capability.registry.CapabilityRegistry
import com.nova.runtime.storage.search.PhotoSearchService
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
        )
    }

    single {
        SemanticSearchService(
            embeddingGenerator = get(),
            vectorIndex = get(),
            photoRepository = get(),
            documentRepository = get(),
            searchIndexPipeline = get(),
            logger = get(),
        )
    }

    single { PhotoSearchCapabilityProvider(photoSearchService = get()) }
    single { DocumentSearchCapabilityProvider(documentSearchService = get()) }
    single { SemanticSearchCapabilityProvider(semanticSearchService = get()) }

    single(createdAtStart = true) {
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
