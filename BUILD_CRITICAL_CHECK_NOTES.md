# 0.3.3 critical build fix

This version fixes the D8 duplicate-class failure reported by GitHub Actions:

`Type com.jlxc.teleprompter.asr.SherpaOnnxAsrEngine is defined multiple times`

Root cause: older ZIP versions used the class name `SherpaOnnxAsrEngine`. If a new ZIP was copied over an existing GitHub repo without deleting old files, stale `SherpaOnnxAsrEngine.java` and the newer `SherpaOnnxAsrEngine.kt` could both be compiled.

Fixes:

- The real engine is renamed to `XlargeSherpaOnnxAsrEngine.kt`.
- `AsrEngineFactory.java` now instantiates `XlargeSherpaOnnxAsrEngine`.
- Gradle excludes stale `SherpaOnnxAsrEngine.java/.kt` as a safety net.
- GitHub Actions removes stale `SherpaOnnxAsrEngine.java/.kt` before building.
- Gradle itself runs `scripts/download_sherpa_asr.sh` before debug compile/asset/AAR tasks, so even a plain `gradle :app:assembleDebug` will download the real sherpa AAR and xlarge model instead of producing an empty APK.
- `gradle clean :app:assembleDebug` is used in Actions.
- `scripts/ci_static_check.sh` now detects duplicate simple class names before the slow build starts.
