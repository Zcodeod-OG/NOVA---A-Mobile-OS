package com.nova.runtime.ai.native.di

import com.nova.runtime.ai.model.EmbeddingGenerator
import com.nova.runtime.ai.model.ModelLoader
import com.nova.runtime.ai.model.OcrEngine
import com.nova.runtime.ai.native.indexing.EmbeddingIndexer
import com.nova.runtime.ai.native.inference.OnDeviceModelRegistryFactory
import com.nova.runtime.ai.native.model.AndroidModelLoader
import com.nova.runtime.ai.native.ocr.MlKitOcrEngine
import com.nova.runtime.ai.native.onnx.OnnxEmbeddingGenerator
import com.nova.runtime.ai.native.onnx.WhisperOnnxAsrEngine
import com.nova.runtime.ai.native.speech.WhisperSpeechRecognizer
import com.nova.runtime.ai.native.storage.CosineVectorIndex
import com.nova.runtime.conversation.speech.SpeechRecognizer
import com.nova.runtime.inference.registry.ModelRegistry
import com.nova.runtime.storage.vector.VectorIndex
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Koin wiring for on-device AI integrations (AIS §5, TDD §7). */
val aiNativeModule = module {
    single<ModelLoader> { AndroidModelLoader(androidContext()) }

    single { CosineVectorIndex() }
    single<VectorIndex> { get<CosineVectorIndex>() }

    single<EmbeddingGenerator> {
        OnnxEmbeddingGenerator(
            modelLoader = get(),
            logger = get(),
        )
    }

    single { WhisperOnnxAsrEngine(modelLoader = get(), logger = get()) }
    single<SpeechRecognizer> {
        WhisperSpeechRecognizer(asrEngine = get())
    }

    single<OcrEngine> { MlKitOcrEngine() }

    single<ModelRegistry> {
        OnDeviceModelRegistryFactory(
            modelLoader = get(),
            logger = get(),
        ).create()
    }

    single {
        EmbeddingIndexer(
            embeddingGenerator = get(),
            embeddingRepository = get(),
            vectorIndex = get(),
            ocrEngine = get(),
        )
    }
}
