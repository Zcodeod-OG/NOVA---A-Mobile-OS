#!/usr/bin/env bash
# Download whisper-tiny ONNX for offline voice commands.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET="${REPO_ROOT}/app/src/main/assets/models/whisper-tiny.onnx"
MODEL_URL="${WHISPER_ONNX_URL:-https://huggingface.co/onnx-community/whisper-tiny.en/resolve/main/onnx/model.onnx}"

mkdir -p "$(dirname "${TARGET}")"

echo "Downloading whisper-tiny.onnx (~40 MB)..."
curl -L "${MODEL_URL}" -o "${TARGET}"
echo "Saved to ${TARGET}"
echo "Rebuild and reinstall the app: ./gradlew :app:installDebug"
