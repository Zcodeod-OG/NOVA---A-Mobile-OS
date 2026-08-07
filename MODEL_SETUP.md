# NOVA Model Setup

NOVA is a **local-first, offline-capable** cognitive runtime for Android. All inference — embeddings, LLM reasoning, speech recognition, and (planned) visual search — runs **on-device** via ONNX Runtime. Models are not optional extras; they are core product infrastructure.

This guide covers collaborator onboarding, model inventory, install paths, and the first-run download flow.

---

## Philosophy

| Principle | What it means |
|-----------|---------------|
| **Local-first** | Data and inference stay on the device. No cloud API calls for search, voice, or reasoning. |
| **Models required** | Full NOVA functionality depends on ONNX models being present in `filesDir/models/`. |
| **Tiered readiness** | `embedding-mini` ships in git so search works immediately; LLM and ASR models download on first launch or via manual scripts. |
| **No cloud-assistant fallback** | Missing models surface clear UI status — they are not silently replaced by online services. |

---

## Model inventory

| File | Size | Role | Shipped in git? | First-run download? |
|------|------|------|-----------------|---------------------|
| `embedding-mini.onnx` | ~86 MB | Text embeddings (384-dim, all-MiniLM-L6-v2) | **Yes** — `app/src/main/assets/models/` | Copied from assets |
| `llm-light.onnx` | ~480 MB | Tier-1 generative inference (Qwen2.5-0.5B Q4) | No | Yes |
| `llm-full.onnx` | ~1.2 GB | Tier-2 generative inference (Qwen2.5-1.5B Q4) | No | Yes |
| `whisper-tiny.onnx` | ~40 MB | Offline ASR (Whisper tiny.en) | No | Yes |
| `image-encoder.onnx` | TBD | Visual embeddings for photo search | No (planned) | Yes (when available) |

Version IDs are defined in `ModelAssetPaths` (`runtime/ai-core`).

### Tokenizer assets (text embeddings)

The real MiniLM tokenizer ships separately from the ONNX weights:

```
minilm-onnx-export/
  tokenizer.json
  vocab.txt
  tokenizer_config.json
  special_tokens_map.json
```

These are copied into app assets and used by `OnnxEmbeddingGenerator` for accurate text→vector encoding (replacing hash-based dev fallbacks).

### Multimodal model (planned)

Agent work is adding **`image-encoder.onnx`** — a mobile-friendly vision encoder (CLIP / MobileCLIP-style) for semantic photo search beyond OCR text. When integrated:

- Filename: `image-encoder.onnx` (registered in `ModelAssetPaths`)
- Purpose: image → dense vector for visual similarity search
- Delivery: first-run download alongside other large models
- Until installed: photo indexing uses OCR text embeddings only

---

## Runtime storage layout

All models load from the app's private files directory:

```
/data/data/com.nova.runtime.app/files/models/
  embedding-mini.onnx
  llm-light.onnx
  llm-full.onnx
  whisper-tiny.onnx
  image-encoder.onnx   # planned
```

`AndroidModelLoader` (`runtime/ai-native`) resolves paths here. On first access, it copies any matching file from `assets/models/` if not already on disk.

Bundled assets path (APK):

```
app/src/main/assets/models/
```

---

## Collaborator onboarding

### 1. Clone and verify bundled model

```bash
git clone <repo-url>
cd NOVA---A-Mobile-OS

# embedding-mini.onnx should already be present (~86 MB)
ls -lh app/src/main/assets/models/embedding-mini.onnx
```

Requires **git LFS** if your clone uses LFS for large files. The embedding model is tracked directly in git.

### 2. Install remaining models (choose one path)

#### Path A — Download scripts (recommended for dev)

Requires `curl` and `huggingface-cli` (`pip install 'huggingface_hub[cli]'`):

```bash
./scripts/download-whisper-onnx.sh   # ~40 MB  → assets/models/
./scripts/download-llm-onnx.sh       # ~1.7 GB → assets/models/
```

#### Path B — Copy from an existing model directory

If you already have the three large ONNX files locally:

```bash
./scripts/setup-models.sh /path/to/models
```

`embedding-mini.onnx` is skipped if already present in assets.

#### Path C — First-run download (end-user flow)

On first launch, NOVA prompts to download missing models over Wi‑Fi. Progress appears in the activity feed. Downloads are resumable and land in `filesDir/models/`. This path is intended for production / Play Store builds where large binaries are not bundled in the APK.

### 3. Build and run

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home  # macOS example
./gradlew :app:installDebug
adb shell am start -n com.nova.runtime.app/.MainActivity
```

On boot, check the activity feed for `MODELS` entries confirming each file is available.

### 4. Verify model readiness

```bash
adb shell run-as com.nova.runtime.app ls -lh files/models/
```

Expected output includes all required `.onnx` files with non-zero sizes.

---

## adb push (large models without rebuilding APK)

Useful when iterating on LLM weights without re-packaging assets:

```bash
# Stage via world-readable tmp, then copy into app-private storage
adb push llm-light.onnx /data/local/tmp/
adb shell run-as com.nova.runtime.app cp /data/local/tmp/llm-light.onnx files/models/

adb push llm-full.onnx /data/local/tmp/
adb shell run-as com.nova.runtime.app cp /data/local/tmp/llm-full.onnx files/models/

adb push whisper-tiny.onnx /data/local/tmp/
adb shell run-as com.nova.runtime.app cp /data/local/tmp/whisper-tiny.onnx files/models/

# Planned multimodal model
adb push image-encoder.onnx /data/local/tmp/
adb shell run-as com.nova.runtime.app cp /data/local/tmp/image-encoder.onnx files/models/
```

Restart the app after pushing new models so ONNX sessions reload.

---

## First-run download flow

```
App launch
    │
    ├─ embedding-mini.onnx in assets?
    │       └─ copy to filesDir/models/ if missing
    │
    ├─ llm-light / llm-full / whisper missing?
    │       └─ ModelDownloadManager: resumable HTTP from HuggingFace
    │               └─ progress → activity feed / overlay
    │
    └─ All required models present?
            └─ enable full inference tiers (LIGHT, FULL, offline voice)
```

**Network required** only during initial model download. After that, NOVA operates fully offline.

**No network?** Use the download scripts on a dev machine, then `adb push` or rebuild with assets populated.

---

## Download script reference

| Script | Models | Destination |
|--------|--------|-------------|
| `scripts/download-whisper-onnx.sh` | `whisper-tiny.onnx` | `app/src/main/assets/models/` |
| `scripts/download-llm-onnx.sh` | `llm-light.onnx`, `llm-full.onnx` | `app/src/main/assets/models/` |
| `scripts/setup-models.sh` | Copies large models from a local dir | `app/src/main/assets/models/` |

HuggingFace sources match the first-run download URLs:

- Whisper: `onnx-community/whisper-tiny.en` → `onnx/model.onnx`
- LLM light: `onnx-community/Qwen2.5-0.5B-Instruct` → `onnx/model_q4f16.onnx`
- LLM full: `onnx-community/Qwen2.5-1.5B-Instruct` → `onnx/model_q4.onnx`

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| Activity feed shows `embedding-mini.onnx missing` | Asset copy failed or corrupt clone | Re-clone; verify `app/src/main/assets/models/embedding-mini.onnx` exists |
| Voice commands fail immediately | `whisper-tiny.onnx` not installed | Run `./scripts/download-whisper-onnx.sh` or wait for first-run download |
| Search returns poor / no semantic matches | Embedding model or tokenizer missing | Ensure `embedding-mini.onnx` + tokenizer assets are present |
| LLM responses are rule-based stubs | `llm-light.onnx` / `llm-full.onnx` missing | Download LLM models; check `files/models/` |
| Photo search misses visual queries | `image-encoder.onnx` not yet available | Expected until multimodal support ships; OCR text search still works |
| Out of storage during download | ~2 GB total for all models | Free space or install subset via `adb push` |

---

## Related docs

- `app/src/main/assets/models/README.md` — quick reference at the assets directory
- `runtime/ai-native/README.md` — ONNX/ML Kit component wiring
- `runtime/inference/README.md` — inference tier architecture
- `docs/PRD.md`, `docs/TDD.md`, `docs/AIS.md` — product and engineering specs (architecture docs)
