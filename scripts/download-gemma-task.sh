#!/usr/bin/env bash
# Download MediaPipe Gemma 3 1B IT (.task) for grounded document Q&A.
# Requires: huggingface-cli + accepting the Gemma license on Hugging Face while logged in.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET_DIR="${REPO_ROOT}/app/src/main/assets/models"
mkdir -p "${TARGET_DIR}"

FILE_NAME="Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task"

if ! command -v huggingface-cli >/dev/null 2>&1; then
  echo "Install huggingface-cli: pip install 'huggingface_hub[cli]'"
  exit 1
fi

TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT

echo "Downloading ${FILE_NAME} (accept Gemma license on HF if prompted)..."
huggingface-cli download litert-community/Gemma3-1B-IT \
  "${FILE_NAME}" \
  --local-dir "${TMP}"

cp "${TMP}/${FILE_NAME}" "${TARGET_DIR}/${FILE_NAME}"
echo "Installed:"
ls -lh "${TARGET_DIR}/${FILE_NAME}"
echo "Rebuild/install the app, or adb push into files/models/."
