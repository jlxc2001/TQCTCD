# sherpa-onnx 编译修复说明

本版修复 GitHub Actions 中的：

```text
Unresolved reference: k2fsa
```

原因：sherpa-onnx Android AAR 主要提供 JNI/native runtime，示例工程还需要 Kotlin API 包装类。官方 discussion #1950 也说明，如果 Android 示例中的 Kotlin API 文件缺失，需要把 `OnlineRecognizer.kt`、`OnlineStream.kt` 等文件复制到 `com/k2fsa/sherpa/onnx` 下。

本版已加入：

- `app/src/main/java/com/k2fsa/sherpa/onnx/OnlineRecognizer.kt`
- `app/src/main/java/com/k2fsa/sherpa/onnx/OnlineStream.kt`
- `FeatureConfig.kt`
- `QnnConfig.kt`
- `HomophoneReplacerConfig.kt`

同时修复了 `SherpaOnnxAsrEngine.kt` 中 Kotlin 字符串里误用 `$name` 的问题，改为 `${name()}`。

GitHub Actions 仍会在构建前下载：

- sherpa-onnx AAR
- 中文 xlarge ASR 模型

