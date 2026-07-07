#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

if grep -R "flatDir" -n app/build.gradle settings.gradle build.gradle; then
  echo "ERROR: flatDir must not be used because settings.gradle uses FAIL_ON_PROJECT_REPOS." >&2
  exit 1
fi

grep -q "implementation files(sherpaAar)" app/build.gradle

test -f app/src/main/java/com/k2fsa/sherpa/onnx/OnlineRecognizer.kt
test -f app/src/main/java/com/k2fsa/sherpa/onnx/OnlineStream.kt
test -f app/src/main/java/com/k2fsa/sherpa/onnx/FeatureConfig.kt
test -f app/src/main/java/com/k2fsa/sherpa/onnx/HomophoneReplacerConfig.kt

grep -q "sherpa-onnx-static-link-onnxruntime-1.12.31.aar" app/build.gradle
grep -q "sherpa-onnx-streaming-zipformer-zh-xlarge-int8-2025-06-30" scripts/download_sherpa_asr.sh

echo "Static CI check passed."
