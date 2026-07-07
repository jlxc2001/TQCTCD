# xlarge ASR 版本说明

本版本默认使用：

`sherpa-onnx-streaming-zipformer-zh-xlarge-int8-2025-06-30`

它不是上一版的 `zh-14M` 小模型。GitHub Actions 会在构建时自动下载模型，并打包进 APK。

关键文件：

- `encoder.int8.onnx`
- `decoder.onnx`
- `joiner.int8.onnx`
- `tokens.txt`

本版本做了以下调整：

1. `AsrModelInfo` 指向 xlarge 模型目录和新文件名。
2. `SherpaOnnxAsrEngine` 使用 `modelType = "zipformer2"`。
3. `AsrEngineFactory` 不再回退到系统 SpeechRecognizer，避免无模型时假装识别。
4. `scripts/download_sherpa_asr.sh` 会删除旧小模型目录，保证 APK 只打包 xlarge。
5. `app/build.gradle` 默认只打包 `arm64-v8a`，更适合骁龙 8 Elite / 8 Elite Gen 系列旗舰机。
6. 对 `.onnx` 文件设置 `noCompress`，减少运行时解包/读取开销。

注意：源码 ZIP 仍然很小，因为没有把 700MB+ 模型直接放进源码包。最终 GitHub Actions 生成的 APK 才会包含模型。
