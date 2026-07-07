# Deep fix notes

This version fixes the GitHub Actions failure caused by `flatDir` being declared in `app/build.gradle` while `settings.gradle` uses `RepositoriesMode.FAIL_ON_PROJECT_REPOS`.

Changes:

- Removed `repositories { flatDir { ... } }` from `app/build.gradle`.
- Replaced the local AAR dependency with `implementation files(sherpaAar)`.
- Replaced the handwritten sherpa Kotlin wrapper with official `v1.12.31` Kotlin API files to match `sherpa-onnx-static-link-onnxruntime-1.12.31.aar`.
- Added AAR/model download validation so HTML/error pages are not packaged as runtime assets.
- Added `scripts/ci_static_check.sh` to fail early if `flatDir` or missing wrapper files reappear.
