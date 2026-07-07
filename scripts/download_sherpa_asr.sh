#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
LIB_DIR="$ROOT_DIR/app/libs"
ASSET_ASR_DIR="$ROOT_DIR/app/src/main/assets/asr"

# 大模型优先版：默认打包 sherpa-onnx 中文 xlarge 流式 Transducer 模型。
# 适合骁龙 8 Elite / 8E 这类旗舰设备，优先保证识别率和稳定性。
MODEL_NAME="sherpa-onnx-streaming-zipformer-zh-xlarge-int8-2025-06-30"
MODEL_DIR="$ASSET_ASR_DIR/$MODEL_NAME"
MODEL_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/$MODEL_NAME.tar.bz2"

AAR_NAME="sherpa-onnx-static-link-onnxruntime-1.12.31.aar"
AAR_URL="https://huggingface.co/csukuangfj2/sherpa-onnx-libs/resolve/main/android/aar/1.12.31/$AAR_NAME"

mkdir -p "$LIB_DIR" "$ASSET_ASR_DIR"

if [ ! -s "$LIB_DIR/$AAR_NAME" ]; then
  echo "Downloading sherpa-onnx Android AAR: $AAR_NAME"
  curl -L --fail --retry 8 --retry-delay 5 --connect-timeout 30 -o "$LIB_DIR/$AAR_NAME" "$AAR_URL"
else
  echo "AAR already exists: $LIB_DIR/$AAR_NAME"
fi

# 防止 HuggingFace/GitHub 下载异常时把 HTML 错误页当作 AAR 打进去。
if [ "$(stat -c%s "$LIB_DIR/$AAR_NAME")" -lt 10000000 ]; then
  echo "ERROR: AAR file is too small; download likely failed: $LIB_DIR/$AAR_NAME" >&2
  ls -lh "$LIB_DIR/$AAR_NAME" >&2
  exit 1
fi
unzip -tq "$LIB_DIR/$AAR_NAME" >/dev/null

if [ ! -f "$MODEL_DIR/tokens.txt" ] || [ ! -f "$MODEL_DIR/encoder.int8.onnx" ] || [ ! -f "$MODEL_DIR/decoder.onnx" ] || [ ! -f "$MODEL_DIR/joiner.int8.onnx" ]; then
  echo "Downloading Chinese xlarge streaming ASR model: $MODEL_NAME"
  TMP_FILE="${RUNNER_TEMP:-/tmp}/$MODEL_NAME.tar.bz2"
  rm -f "$TMP_FILE"
  curl -L --fail --retry 8 --retry-delay 5 --connect-timeout 30 -o "$TMP_FILE" "$MODEL_URL"
  if [ "$(stat -c%s "$TMP_FILE")" -lt 100000000 ]; then
    echo "ERROR: Model archive is too small; download likely failed: $TMP_FILE" >&2
    ls -lh "$TMP_FILE" >&2
    exit 1
  fi
  rm -rf "$MODEL_DIR"
  tar -xjf "$TMP_FILE" -C "$ASSET_ASR_DIR"
  rm -f "$TMP_FILE"
  rm -rf "$MODEL_DIR/test_wavs"
else
  echo "Model already exists: $MODEL_DIR"
fi

# 避免旧 14M 小模型残留进 APK，保证本版本只使用 xlarge。
find "$ASSET_ASR_DIR" -mindepth 1 -maxdepth 1 -type d ! -name "$MODEL_NAME" -exec rm -rf {} +

# 最终校验：缺任何一个文件都直接失败，不让空壳 APK 产出。
test -s "$MODEL_DIR/encoder.int8.onnx"
test -s "$MODEL_DIR/decoder.onnx"
test -s "$MODEL_DIR/joiner.int8.onnx"
test -s "$MODEL_DIR/tokens.txt"

echo "Done. Current ASR runtime and assets:"
ls -lh "$LIB_DIR/$AAR_NAME"
find "$ASSET_ASR_DIR" -maxdepth 2 -type f -exec ls -lh {} \; | sort -k9
