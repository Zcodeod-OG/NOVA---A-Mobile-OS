# NOVA On-Device Inference

Portable inference orchestration lives in this module. **Actual model execution** is provided
by `:runtime:ai-native` (ONNX Runtime) and wired at the app layer via Koin overrides.

## Inference Tiers (TDD §7 / MSP §5)

| Tier | Level | Default Implementation |
|------|-------|------------------------|
| DETERMINISTIC | 0 | `DeterministicInferenceModel` — rule patterns, always available |
| LIGHT | 1 | `OnnxGenerativeInferenceModel` → fallback `RuleEngineInferenceModel` |
| FULL | 2 | `OnnxGenerativeInferenceModel` → fallback `LightweightInferenceModel` |

## Expected Model Files

Place user-supplied ONNX models under the device directory `{filesDir}/models/` or in app assets at `assets/models/`:

| File | Purpose | Version ID |
|------|---------|------------|
| `embedding-mini.onnx` | Sentence embeddings (384-dim) | `all-MiniLM-L6-v2-onnx` |
| `llm-light.onnx` | Tier-1 lightweight LLM | `llm-light-onnx-v1` |
| `llm-full.onnx` | Tier-2 full LLM | `llm-full-onnx-v1` |
| `whisper-tiny.onnx` | Offline ASR | `whisper-tiny-onnx-v1` |

When a model file is missing, the engine **gracefully falls back** to deterministic placeholders so development and CI continue to work without large binaries.

## Model Loading

- **Registry:** `DefaultModelRegistry` (dev) / `OnDeviceModelRegistryFactory` (production)
- **Lazy init:** ONNX sessions load on first inference via `OnnxSessionManager`
- **Memory:** call `close()` on generators/models when evicting (future work: LRU eviction)

## Developer Setup

1. Export or download quantized ONNX models (see `runtime/ai-native/README.md`).
2. Copy into `app/src/main/assets/models/` **or** push to `/data/data/com.nova.runtime.app/files/models/` on device.
3. Launch the app — `AndroidModelLoader` copies assets on first access.

## Dependencies

- `:runtime:ai-core` — vector math, embedding/OCR contracts
- `:runtime:ai-native` — ONNX Runtime, ML Kit, Whisper ASR (Android)

## Deferred

- llama.cpp JNI bindings for GGUF models
- HNSW native index (current MVP uses brute-force cosine in Kotlin)
- On-device TTS (see `runtime/conversation/README.md`)
