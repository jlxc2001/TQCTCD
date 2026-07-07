# Build fix notes

本版本修复 GitHub Actions 编译失败：

```text
Inconsistent JVM-target compatibility detected for tasks 'compileDebugJavaWithJavac' (1.8) and 'compileDebugKotlin' (17).
```

修复方式：

- `app/build.gradle` 中新增 `compileOptions`，Java 编译目标统一为 17。
- `app/build.gradle` 中新增 `kotlinOptions { jvmTarget = '17' }`。
- 新增 `gradle.properties`，抑制 compileSdk 35 与 AGP 8.5.2 的警告，并保留 Kotlin JVM target 校验。
- 版本号更新为 `0.3.1-xlarge-asr-jvmfix`。

这个修复不改 ASR 模型逻辑，仍然使用 xlarge 中文本地离线模型。
