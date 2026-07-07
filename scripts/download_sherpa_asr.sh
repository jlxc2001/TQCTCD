#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
LIB_DIR="$ROOT_DIR/app/libs"
ASSET_ASR_DIR="$ROOT_DIR/app/src/main/assets/asr"
MODEL_NAME="sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23"
MODEL_DIR="$ASSET_ASR_DIR/$MODEL_NAME"
AAR_NAME="sherpa-onnx-static-link-onnxruntime-1.12.31.aar"
AAR_URL="https://huggingface.co/csukuangfj2/sherpa-onnx-libs/resolve/main/android/aar/1.12.31/$AAR_NAME?download=true"
MODEL_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/$MODEL_NAME.tar.bz2"

mkdir -p "$LIB_DIR" "$ASSET_ASR_DIR"

if [ ! -s "$LIB_DIR/$AAR_NAME" ]; then
  echo "Downloading sherpa-onnx Android AAR..."
  curl -L --retry 5 --retry-delay 3 -o "$LIB_DIR/$AAR_NAME" "$AAR_URL"
else
  echo "AAR already exists: $LIB_DIR/$AAR_NAME"
fi

if [ ! -f "$MODEL_DIR/tokens.txt" ] || [ ! -f "$MODEL_DIR/encoder-epoch-99-avg-1.int8.onnx" ]; then
  echo "Downloading Chinese streaming ASR model..."
  TMP_FILE="${RUNNER_TEMP:-/tmp}/$MODEL_NAME.tar.bz2"
  rm -f "$TMP_FILE"
  curl -L --retry 5 --retry-delay 3 -o "$TMP_FILE" "$MODEL_URL"
  rm -rf "$MODEL_DIR"
  tar -xjf "$TMP_FILE" -C "$ASSET_ASR_DIR"
  rm -f "$TMP_FILE"
  rm -rf "$MODEL_DIR/test_wavs"
else
  echo "Model already exists: $MODEL_DIR"
fi

echo "Done. Current ASR assets:"
find "$ASSET_ASR_DIR" -maxdepth 2 -type f | sed "s#^$ROOT_DIR/##" | sort
