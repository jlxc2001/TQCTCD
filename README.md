# JLXC 提词器

这是一个 Android 原生提词器工程，包含：

- 文稿列表、新建、编辑、删除
- 黑底白字提词页面
- 自定义背景色、文字颜色、字号
- 镜像显示、屏幕朝向
- 自动滚动模式
- 语音跟读匹配与“回读上一段”算法
- 蓝牙键盘/鼠标滚轮控制
- 局域网 HTTP + UDP 遥控协议
- GitHub Actions 打包配置

## 重要说明：语音识别

当前工程默认没有直接内置 sherpa-onnx 中文离线模型和 `.so`，因为完整中文流式模型通常会让 APK 体积达到数百 MB。

当前可运行的兜底方案是 Android 系统 `SpeechRecognizer`。它只适合开发调试：

- 有些国产系统没有可用的系统语音识别服务，会提示“系统语音识别不可用”。
- 有些系统语音识别需要联网。
- 正式版建议接入 `sherpa-onnx` 本地流式中文模型。

语音跟读、回读上一段、跳读匹配的核心逻辑已经和 ASR 引擎解耦：

```text
app/src/main/java/com/jlxc/teleprompter/align/ScriptAligner.java
```

后续只需要替换：

```text
app/src/main/java/com/jlxc/teleprompter/asr/SherpaOnnxAsrEngine.java
```

## 重要说明：局域网遥控

修复版已经把遥控服务改成 App 级单例服务：

- 打开 APP 主界面后就会启动 `/api/ping`
- 在“遥控设置”里可以看到服务状态
- 真正滚动控制需要进入某篇文稿的“开始提词”页面后才会生效
- HTTP 与 UDP 都绑定到 `0.0.0.0:47230`

默认端口：

```text
47230
```

连接检测：

```text
GET http://提词器手机IP:47230/api/ping
```

滚动控制：

```text
POST http://提词器手机IP:47230/api/remote/scroll?dy=80
```

UDP 高频滚动：

```text
SCROLL 80
SCROLL -80
```

`dy > 0` 表示继续往后读，提词内容向上滚动。  
`dy < 0` 表示回退到前文，提词内容向下滚动。

## 手机互连排查

如果电脑能 ping 两台手机，但控制端连接失败，按这个顺序查：

1. 提词端 APP 是否已经打开。
2. 提词端“遥控设置”里是否显示 HTTP/UDP 已启动。
3. 控制端填写的 IP 是否是同一 Wi-Fi/热点网段的 IP。
4. 两台手机是否连在同一个热点或同一个路由器。
5. 手机热点是否开启了“设备隔离 / AP 隔离 / 访客网络隔离”。
6. 端口是否一致，默认都是 47230。
7. 在电脑浏览器打开：`http://提词器IP:47230/api/ping`，如果返回 JSON，说明提词端服务正常。

## GitHub Actions 打包

工作流文件：

```text
.github/workflows/android-build.yml
```

进入 GitHub 仓库：

```text
Actions → Android Build → Run workflow
```

打包完成后，在 Artifacts 下载 debug APK。
