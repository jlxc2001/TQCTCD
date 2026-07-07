#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
LIB_DIR="$ROOT_DIR/app/libs"
ASSET_ASR_DIR="$ROOT_DIR/app/src/main/assets/asr"

# 大模型优先版：默认打包 sherpa-onnx 中文 xlarge 流式 Transducer 模型。
# 这个模型比 zh-14M 小模型大得多，适合高通旗舰/顶级安卓手机本地离线识别。
MODEL_NAME="sherpa-onnx-streaming-zipformer-zh-xlarge-int8-2025-06-30"
MODEL_DIR="$ASSET_ASR_DIR/$MODEL_NAME"
MODEL_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/$MODEL_NAME.tar.bz2"

AAR_NAME="sherpa-onnx-static-link-onnxruntime-1.12.31.aar"
AAR_URL="https://huggingface.co/csukuangfj2/sherpa-onnx-libs/resolve/main/android/aar/1.12.31/$AAR_NAME?download=true"

mkdir -p "$LIB_DIR" "$ASSET_ASR_DIR"

if [ ! -s "$LIB_DIR/$AAR_NAME" ]; then
  echo "Downloading sherpa-onnx Android AAR: $AAR_NAME"
  curl -L --fail --retry 8 --retry-delay 5 --connect-timeout 30 -o "$LIB_DIR/$AAR_NAME" "$AAR_URL"
else
  echo "AAR already exists: $LIB_DIR/$AAR_NAME"
fi

if [ ! -f "$MODEL_DIR/tokens.txt" ] || [ ! -f "$MODEL_DIR/encoder.int8.onnx" ] || [ ! -f "$MODEL_DIR/decoder.onnx" ] || [ ! -f "$MODEL_DIR/joiner.int8.onnx" ]; then
  echo "Downloading Chinese xlarge streaming ASR model: $MODEL_NAME"
  TMP_FILE="${RUNNER_TEMP:-/tmp}/$MODEL_NAME.tar.bz2"
  rm -f "$TMP_FILE"
  curl -L --fail --retry 8 --retry-delay 5 --connect-timeout 30 -o "$TMP_FILE" "$MODEL_URL"
  rm -rf "$MODEL_DIR"
  tar -xjf "$TMP_FILE" -C "$ASSET_ASR_DIR"
  rm -f "$TMP_FILE"
  rm -rf "$MODEL_DIR/test_wavs"
else
  echo "Model already exists: $MODEL_DIR"
fi

# 避免旧 14M 小模型残留进 APK，保证本版本只使用 xlarge。
find "$ASSET_ASR_DIR" -mindepth 1 -maxdepth 1 -type d ! -name "$MODEL_NAME" -exec rm -rf {} +

echo "Done. Current ASR assets:"
find "$ASSET_ASR_DIR" -maxdepth 2 -type f | sed "s#^$ROOT_DIR/##" | sort
