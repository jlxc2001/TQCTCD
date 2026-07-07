# JLXC 提词器

这是一个 Android 原生提词器工程，包含：

- 文稿列表：新建、编辑、长按删除、点击开始提词
- 提词显示：黑底白字，支持自定义背景色、文字颜色、字号
- 镜像显示、屏幕朝向设置
- 模式一：自动滚动，长按提词页面显示速度条
- 模式二：语音识别跟读，已读句子变暗，支持回读上一段/上一句自动退回
- 模式三：蓝牙/局域网遥控滚动
- 遥控协议：HTTP + UDP，默认端口 47230

## 重要：语音识别模型

源码 ZIP 不直接内置几十 MB 的 AAR 和中文 ASR 模型，避免源码包过大。

请使用 GitHub Actions 打包，工作流会自动运行：

```bash
bash scripts/download_sherpa_asr.sh
```

该脚本会下载：

1. `sherpa-onnx-static-link-onnxruntime-1.12.31.aar`
2. `sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23` 中文流式模型

最终 APK 会明显大于 48KB。只有最终 APK 里包含模型后，语音识别模式才会使用真正的本地离线 ASR。

如果你本地打包，也需要先运行：

```bash
bash scripts/download_sherpa_asr.sh
```

然后再执行：

```bash
gradle :app:assembleDebug --no-daemon --stacktrace
```

## GitHub Actions 打包

上传到 GitHub 后：

1. 打开仓库 Actions
2. 选择 Android Build
3. 点击 Run workflow
4. 构建完成后，从 Artifacts 下载 APK

## 遥控测试

打开提词端 APP 后，电脑浏览器访问：

```text
http://提词器手机IP:47230/api/ping
```

正常会返回：

```json
{"ok":true,"app":"JLXC Teleprompter","remote":true,"version":2,"http":true,"udp":true}
```

打开一篇文稿后，可以测试滚动：

```text
http://提词器手机IP:47230/api/remote/scroll?dy=300
```

`dy > 0`：继续往后滚动。  
`dy < 0`：回退到前文。

## 语音识别率设置

软件设置页面中有“识别率设置”。

- 阈值低：允许文案临时改几个字、口误后继续识别，但误跳风险更高
- 阈值高：更严格，误跳少，但临场改词时可能不跟随
- 建议默认：72%

回读上一句/上一段自动退回也会使用这个阈值。
