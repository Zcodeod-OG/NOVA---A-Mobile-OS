# NOVA On-Device ONNX Models

These model files are **not** committed to git due to size. Place the following files in this directory before building or running the app:

| File | Purpose | Suggested source |
|------|---------|------------------|
| `embedding-mini.onnx` | Semantic search embeddings | [all-MiniLM-L6-v2](https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2) ONNX export |
| `llm-light.onnx` | Lightweight on-device LLM | Qwen2.5-0.5B-Instruct or Phi-3-mini ONNX (Q4) |
| `llm-full.onnx` | Full on-device LLM | Same family, larger quantization |
| `whisper-tiny.onnx` | Voice command ASR | [whisper-tiny.en](https://huggingface.co/onnx-community/whisper-tiny.en) ONNX export |

Download Whisper only:

```bash
./scripts/download-whisper-onnx.sh
```

## Setup

### Option A — Copy into assets (recommended for dev)

```bash
# From repo root
./scripts/setup-models.sh /path/to/your/models
```

Or manually copy all four `.onnx` files into:

```
app/src/main/assets/models/
```

### Option B — Push to device at runtime (recommended for large LLM files)

Models load from `filesDir/models/`. Use `/data/local/tmp` as a staging area:

```bash
adb push llm-light.onnx /data/local/tmp/
adb shell run-as com.nova.runtime.app cp /data/local/tmp/llm-light.onnx files/models/

adb push llm-full.onnx /data/local/tmp/
adb shell run-as com.nova.runtime.app cp /data/local/tmp/llm-full.onnx files/models/

adb shell run-as com.nova.runtime.app ls -lh files/models/
```

Download scripts:

```bash
./scripts/download-whisper-onnx.sh
./scripts/download-llm-onnx.sh
```

## Fallback behavior

| Model missing | Fallback |
|---------------|----------|
| `whisper-tiny.onnx` | Device speech recognition (Google) when online; ONNX Whisper when model is installed |
| `embedding-mini.onnx` | Hash embeddings for dev; photo/document search falls back to keyword/OCR |
| LLM models | Rule-based / lightweight inference stubs |
