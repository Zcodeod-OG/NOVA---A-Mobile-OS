#!/usr/bin/env bash
# Copy large ONNX model files into app assets for local development.
#
# NOVA is local-first: all models are required for full functionality.
# embedding-mini.onnx (~86 MB) is already committed in app/src/main/assets/models/.
# This script installs the three large models that are NOT in git.
#
# Alternative: use ./scripts/download-whisper-onnx.sh and ./scripts/download-llm-onnx.sh
# or rely on first-run download on device (see MODEL_SETUP.md).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET_DIR="${REPO_ROOT}/app/src/main/assets/models"
SOURCE_DIR="${1:-}"

# Already bundled in git — skip if present in target
BUNDLED_IN_GIT=(
  "embedding-mini.onnx"
)

# Must be supplied via SOURCE_DIR or download scripts
LARGE_MODELS=(
  "llm-light.onnx"
  "llm-full.onnx"
  "whisper-tiny.onnx"
)

if [[ -z "${SOURCE_DIR}" ]]; then
  echo "Usage: $0 /path/to/models-directory"
  echo ""
  echo "Copies large ONNX models into app/src/main/assets/models/."
  echo "embedding-mini.onnx is already in git and is skipped if present."
  echo ""
  echo "Expected in source directory: ${LARGE_MODELS[*]}"
  echo ""
  echo "Or download directly:"
  echo "  ./scripts/download-whisper-onnx.sh"
  echo "  ./scripts/download-llm-onnx.sh"
  exit 1
fi

mkdir -p "${TARGET_DIR}"

for file in "${BUNDLED_IN_GIT[@]}"; do
  if [[ -f "${TARGET_DIR}/${file}" ]]; then
    echo "Skipping ${file} (already bundled in assets)"
  elif [[ -f "${SOURCE_DIR%/}/${file}" ]]; then
    cp "${SOURCE_DIR%/}/${file}" "${TARGET_DIR}/"
    echo "Copied ${file}"
  fi
done

for file in "${LARGE_MODELS[@]}"; do
  src="${SOURCE_DIR%/}/${file}"
  if [[ ! -f "${src}" ]]; then
    echo "Missing required model: ${src}"
    echo "Download with ./scripts/download-whisper-onnx.sh and ./scripts/download-llm-onnx.sh"
    exit 1
  fi
  cp "${src}" "${TARGET_DIR}/"
  echo "Copied ${file}"
done

echo "Models installed to ${TARGET_DIR}"
echo "Rebuild: ./gradlew :app:installDebug"
