#!/usr/bin/env bash
# Download whisper-tiny ONNX for offline voice commands.
#
# NOVA uses local Whisper ASR — this model is required for offline voice.
# Not committed to git (~40 MB). Same URL used by first-run download on device.
# See MODEL_SETUP.md for full onboarding.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET="${REPO_ROOT}/app/src/main/assets/models/whisper-tiny.onnx"
MODEL_URL="${WHISPER_ONNX_URL:-https://huggingface.co/onnx-community/whisper-tiny.en/resolve/main/onnx/model.onnx}"

mkdir -p "$(dirname "${TARGET}")"

echo "Downloading whisper-tiny.onnx (~40 MB)..."
curl -L "${MODEL_URL}" -o "${TARGET}.part"
BYTES=$(wc -c < "${TARGET}.part" | tr -d ' ')
MIN_BYTES=10000000
if [ "${BYTES}" -lt "${MIN_BYTES}" ]; then
  rm -f "${TARGET}.part"
  echo "ERROR: Download too small (${BYTES} bytes) — HuggingFace may have returned an HTML error page." >&2
  echo "Try: hf download onnx-community/whisper-tiny.en onnx/model.onnx --local-dir $(dirname "${TARGET}")" >&2
  echo "Then: mv $(dirname "${TARGET}")/onnx/model.onnx ${TARGET}" >&2
  exit 1
fi
mv "${TARGET}.part" "${TARGET}"
echo "Saved to ${TARGET} (${BYTES} bytes)"
echo "Rebuild and reinstall the app: ./gradlew :app:installDebug"
