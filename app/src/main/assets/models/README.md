# NOVA On-Device ONNX Models

NOVA is a **local-first** cognitive runtime. On-device ONNX models are **required** for the product experience — semantic search, offline voice, and on-device reasoning. There is no cloud-assistant fallback path.

Models resolve at runtime from:

```
/data/data/com.nova.runtime.app/files/models/
```

`AndroidModelLoader` copies bundled assets into that directory on first use. Large models can also be fetched on first launch (see [MODEL_SETUP.md](../../../../../MODEL_SETUP.md)) or installed manually via scripts / `adb push`.

## Model inventory

| File | Size (approx.) | Purpose | In git? |
|------|----------------|---------|---------|
| `embedding-mini.onnx` | ~86 MB | Text embeddings for semantic search (384-dim MiniLM) | **Yes** — bundled in assets |
| `Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task` | ~500 MB+ | MediaPipe grounded document Q&A | No — `./scripts/download-gemma-task.sh` (HF Gemma license) |
| `llm-light.onnx` | ~480 MB | Tier-1 ONNX LLM (decode limited) | No — first-run download or script |
| `llm-full.onnx` | ~1.2 GB | Tier-2 ONNX LLM (decode limited) | No — first-run download or script |
| `whisper-tiny.onnx` | ~40 MB | Offline speech recognition | No — first-run download or script |
| `image-encoder.onnx` | TBD | Visual embeddings for photo search (planned) | No — coming with multimodal support |

## Quick start (developers)

After cloning the repo, `embedding-mini.onnx` is already present. Install the remaining models with:

```bash
./scripts/download-whisper-onnx.sh
./scripts/download-gemma-task.sh   # optional polish for grounded doc Q&A (HF Gemma license)
./scripts/download-llm-onnx.sh     # optional ONNX tiers (not used for doc Q&A)
```

Then build and run:

```bash
./gradlew :app:installDebug
```

On first launch, bundled assets copy into `filesDir/models/`. When first-run download is enabled, missing large models download automatically with progress shown in the activity feed.

## Manual install options

**Copy from a local directory** (all three large models required):

```bash
./scripts/setup-models.sh /path/to/your/models
```

**Push large models to a connected device** (avoids bloating APK rebuilds):

```bash
adb push llm-light.onnx /data/local/tmp/
adb shell run-as com.nova.runtime.app cp /data/local/tmp/llm-light.onnx files/models/

adb push llm-full.onnx /data/local/tmp/
adb shell run-as com.nova.runtime.app cp /data/local/tmp/llm-full.onnx files/models/

adb push whisper-tiny.onnx /data/local/tmp/
adb shell run-as com.nova.runtime.app cp /data/local/tmp/whisper-tiny.onnx files/models/

adb push Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task /data/local/tmp/
adb shell run-as com.nova.runtime.app cp /data/local/tmp/Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task files/models/

adb shell run-as com.nova.runtime.app ls -lh files/models/
```

## Suggested sources

| File | Source |
|------|--------|
| `embedding-mini.onnx` | Exported from [all-MiniLM-L6-v2](https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2) (see `minilm-onnx-export/`) |
| `Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task` | [litert-community/Gemma3-1B-IT](https://huggingface.co/litert-community/Gemma3-1B-IT) → `Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task` |
| `llm-light.onnx` | [onnx-community/Qwen2.5-0.5B-Instruct](https://huggingface.co/onnx-community/Qwen2.5-0.5B-Instruct) → `onnx/model_q4f16.onnx` |
| `llm-full.onnx` | [onnx-community/Qwen2.5-1.5B-Instruct](https://huggingface.co/onnx-community/Qwen2.5-1.5B-Instruct) → `onnx/model_q4.onnx` |
| `whisper-tiny.onnx` | [onnx-community/whisper-tiny.en](https://huggingface.co/onnx-community/whisper-tiny.en) → `onnx/model.onnx` |
| `image-encoder.onnx` | Mobile-friendly CLIP / vision encoder ONNX (planned — see MODEL_SETUP.md) |

Full onboarding, tokenizer assets, and troubleshooting: **[MODEL_SETUP.md](../../../../../MODEL_SETUP.md)** at the repo root.
