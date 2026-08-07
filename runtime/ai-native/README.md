# NOVA On-Device AI (Android)

Android-native integrations for local inference. **All processing stays on-device** — no cloud API calls.

## Libraries

| Component | Library | Version |
|-----------|---------|---------|
| LLM + Embeddings + ASR | ONNX Runtime Android | 1.19.2 |
| OCR | ML Kit Text Recognition | 16.0.1 |

## Modules

- `:runtime:ai-core` — JVM vector math, model contracts, hash embedding fallback
- `:runtime:ai-native` — ONNX/ML Kit implementations, Koin `aiNativeModule`

## Wiring

Include in the app:

```kotlin
modules(
    runtimeModule,
    storageModule(context),
    aiIntegrationModule, // overrides SpeechRecognizer, ModelRegistry, VectorIndex
)
```

## Model Setup

### Option A — App assets (recommended for dev)

```
app/src/main/assets/models/
  embedding-mini.onnx
  llm-light.onnx
  llm-full.onnx
  whisper-tiny.onnx
```

### Option B — Device files directory

```
/data/data/com.nova.runtime.app/files/models/
```

`AndroidModelLoader` copies from assets when files are absent.

### Suggested models

1. **Embeddings:** [all-MiniLM-L6-v2](https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2) exported to ONNX
2. **LLM light:** Qwen2.5-0.5B-Instruct or Phi-3-mini ONNX export
3. **LLM full:** Same family, larger quantization (Q4)
4. **ASR:** [whisper-tiny.en](https://huggingface.co/onnx-community/whisper-tiny.en) ONNX export

> Models are **not** bundled in git due to size. Download and place locally.

## Components

| Class | Replaces |
|-------|----------|
| `OnnxEmbeddingGenerator` | Real MiniLM WordPiece tokenization; returns failure when model/tokenizer missing (no hash fallback) |
| `OnnxGenerativeInferenceModel` | `RuleEngineInferenceModel` / `LightweightInferenceModel` |
| `WhisperSpeechRecognizer` | `StubSpeechRecognizer` |
| `MlKitOcrEngine` | OCR stub |
| `CosineVectorIndex` | `NoOpVectorIndex` |
| `EmbeddingIndexer` | Manual embedding/OCR pipeline |

## Permissions

`RECORD_AUDIO` declared in `ai-native` manifest (for future streaming ASR).

## Deferred

- **TTS:** `StubTextToSpeech` remains; Android TextToSpeech on-device engine planned
- **whisper.cpp NDK:** current ASR uses ONNX Whisper export; JNI whisper.cpp optional upgrade
- **llama.cpp:** GGUF loading via NDK (ONNX used for MVP)
- **HNSW:** brute-force cosine KNN sufficient for MVP scale
