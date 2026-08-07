#!/usr/bin/env bash
# Copy ONNX model files into app assets for local development.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET_DIR="${REPO_ROOT}/app/src/main/assets/models"
SOURCE_DIR="${1:-}"

REQUIRED=(
  "embedding-mini.onnx"
  "llm-light.onnx"
  "llm-full.onnx"
  "whisper-tiny.onnx"
)

if [[ -z "${SOURCE_DIR}" ]]; then
  echo "Usage: $0 /path/to/models-directory"
  echo "Expected files: ${REQUIRED[*]}"
  exit 1
fi

mkdir -p "${TARGET_DIR}"

for file in "${REQUIRED[@]}"; do
  src="${SOURCE_DIR%/}/${file}"
  if [[ ! -f "${src}" ]]; then
    echo "Missing required model: ${src}"
    exit 1
  fi
  cp "${src}" "${TARGET_DIR}/"
  echo "Copied ${file}"
done

echo "Models installed to ${TARGET_DIR}"
