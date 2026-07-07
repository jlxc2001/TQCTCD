# JLXC Teleprompter

一个安卓原生提词器工程雏形，包含：

- 主界面：开始提词 / 软件设置 / 遥控设置
- 文稿列表：新建、点击开始、长按编辑/删除
- 提词页面：黑底白字，支持自定义背景色/文字色/字号
- 屏幕朝向设置、镜像显示
- 模式一：自动滚动，长按提词页面显示速度条
- 模式二：语音识别跟读，内置“回读上一段/上一句”匹配算法
- 模式三：遥控控制，支持局域网 HTTP + UDP 协议
- 蓝牙鼠标滚轮、方向键、音量键控制滚动

## 遥控协议

默认端口：47230

### Ping

```http
GET /api/ping
```

返回：

```json
{"ok":true,"app":"JLXC Teleprompter","remote":true,"version":1}
```

### HTTP 滚动

```http
POST /api/remote/scroll?dy=80
```

- `dy > 0`：提词内容向上滚动，继续往后读
- `dy < 0`：提词内容向下滚动，回退到前文

### UDP 高频滚动

UTF-8 文本：

```text
SCROLL 80
SCROLL -80
PAUSE true
PAUSE false
TOP
```

## 回读上一段算法

核心在：

```text
app/src/main/java/com/jlxc/teleprompter/align/ScriptAligner.java
```

它会把文稿切成句子，并在当前进度附近进行模糊匹配。搜索窗口包含当前句前 3 句，因此用户上一句有口误、重读上一句时，会自动把进度退回上一句。

可调参数：软件设置里的“语音识别率设置”。

- 阈值低：允许临场改字
- 阈值高：更严格，误跳少
- 建议：65%～78%

## 本地 ASR 接入说明

工程已经预留：

```text
app/src/main/java/com/jlxc/teleprompter/asr/AsrEngine.java
app/src/main/java/com/jlxc/teleprompter/asr/SherpaOnnxAsrEngine.java
app/src/main/assets/asr/
```

由于中文流式模型体积可能几百 MB，本包没有内置真实模型。当前 `AndroidSpeechRecognizerEngine` 只是开发调试兜底，不保证完全离线。

正式版本建议：

1. 按 sherpa-onnx Android 文档放入 `libsherpa-onnx-jni.so` 和 `libonnxruntime.so`
2. 把中文/中英流式模型放入 `app/src/main/assets/asr/`
3. 用 sherpa-onnx 官方 Android Demo 的识别器代码替换 `SherpaOnnxAsrEngine`
4. 保持 `AsrEngine.Listener` 输出识别文本即可，无需改 UI 和回读算法

## 构建

用 Android Studio 打开本目录，等待 Gradle 同步后运行。

建议环境：

- Android Studio 2024+ / 2025+
- JDK 17
- compileSdk 35
- minSdk 23

