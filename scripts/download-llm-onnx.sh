#!/usr/bin/env bash
# Download quantized LLM ONNX models for NOVA (llm-light + llm-full).
#
# NOVA is local-first — these models are required for on-device reasoning.
# Not committed to git due to size (~1.7 GB combined).
# Same HuggingFace URLs used by first-run ModelDownloadManager on device.
# See MODEL_SETUP.md for full onboarding.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET_DIR="${REPO_ROOT}/app/src/main/assets/models"
mkdir -p "${TARGET_DIR}"

if ! command -v huggingface-cli >/dev/null 2>&1; then
  echo "Install huggingface-cli: pip install 'huggingface_hub[cli]'"
  exit 1
fi

TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT

echo "Downloading llm-light (~480 MB) from Qwen2.5-0.5B-Instruct..."
huggingface-cli download onnx-community/Qwen2.5-0.5B-Instruct \
  onnx/model_q4f16.onnx \
  --local-dir "${TMP}/light"

echo "Downloading llm-full (~1.2 GB) from Qwen2.5-1.5B-Instruct..."
huggingface-cli download onnx-community/Qwen2.5-1.5B-Instruct \
  onnx/model_q4.onnx \
  --local-dir "${TMP}/full"

cp "${TMP}/light/onnx/model_q4f16.onnx" "${TARGET_DIR}/llm-light.onnx"
cp "${TMP}/full/onnx/model_q4.onnx" "${TARGET_DIR}/llm-full.onnx"

echo "Installed:"
ls -lh "${TARGET_DIR}/llm-light.onnx" "${TARGET_DIR}/llm-full.onnx"
echo "Rebuild: ./gradlew :app:installDebug"
