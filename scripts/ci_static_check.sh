#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

if grep -R "flatDir" -n app/build.gradle settings.gradle build.gradle; then
  echo "ERROR: flatDir must not be used because settings.gradle uses FAIL_ON_PROJECT_REPOS." >&2
  exit 1
fi

# The old class name caused D8 duplicate-class failures when users copied a new ZIP over an old repo.
if find app/src/main/java -type f \( -name 'SherpaOnnxAsrEngine.java' -o -name 'SherpaOnnxAsrEngine.kt' \) | grep -q .; then
  echo "ERROR: stale SherpaOnnxAsrEngine.java/.kt exists. Remove it; this version uses XlargeSherpaOnnxAsrEngine.kt." >&2
  find app/src/main/java -type f \( -name 'SherpaOnnxAsrEngine.java' -o -name 'SherpaOnnxAsrEngine.kt' \) >&2
  exit 1
fi

# No duplicate simple class/interface/object/enum names across Java/Kotlin source files.
python3 - <<'PY'
from pathlib import Path
import re, sys
seen = {}
pattern = re.compile(r'\b(?:class|interface|object|enum\s+class|enum)\s+([A-Za-z_][A-Za-z0-9_]*)')
for path in Path('app/src/main/java').rglob('*'):
    if path.suffix not in ('.java', '.kt'):
        continue
    text = path.read_text(errors='ignore')
    for match in pattern.finditer(text):
        name = match.group(1)
        seen.setdefault(name, set()).add(str(path))
bad = {k: sorted(v) for k, v in seen.items() if len(v) > 1}
if bad:
    print('ERROR: duplicate class/interface/object names detected:', file=sys.stderr)
    for k, paths in bad.items():
        print(k + ':', file=sys.stderr)
        for x in paths:
            print('  ' + x, file=sys.stderr)
    sys.exit(1)
PY

grep -q "implementation files(sherpaAar)" app/build.gradle
grep -q "ensureSherpaAsrAssets" app/build.gradle
grep -q "XlargeSherpaOnnxAsrEngine" app/src/main/java/com/jlxc/teleprompter/asr/AsrEngineFactory.java
test -f app/src/main/java/com/jlxc/teleprompter/asr/XlargeSherpaOnnxAsrEngine.kt

test -f app/src/main/java/com/k2fsa/sherpa/onnx/OnlineRecognizer.kt
test -f app/src/main/java/com/k2fsa/sherpa/onnx/OnlineStream.kt
test -f app/src/main/java/com/k2fsa/sherpa/onnx/FeatureConfig.kt
test -f app/src/main/java/com/k2fsa/sherpa/onnx/HomophoneReplacerConfig.kt

grep -q "sherpa-onnx-static-link-onnxruntime-1.12.31.aar" app/build.gradle
grep -q "sherpa-onnx-streaming-zipformer-zh-xlarge-int8-2025-06-30" scripts/download_sherpa_asr.sh

echo "Static CI check passed."
