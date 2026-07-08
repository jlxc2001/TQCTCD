请基于我现有的安卓“提词器遥控器 APP”继续修改，不要重做项目。现在提词端 APP 已经新增了“遥控端通过局域网新增提词文件”的接口，请在控制端加入发送文稿功能。

一、现有遥控协议保持不变

提词端默认 IP 和端口仍由用户输入，默认端口 47230。

已有接口：
- GET /api/ping
- POST /api/remote/scroll?dy=80
- UDP: SCROLL 80
- POST /api/remote/pause?paused=true
- POST /api/remote/top

请不要破坏已有音量键控制、虚拟触控板控制、UDP 滚动控制。

二、新增功能目标

遥控端手机一般是我写文案的设备，提词端一般是平板。我要在遥控端 APP 里直接新建/粘贴文稿，然后通过局域网发送到提词端，让提词端自动新增一篇提词文件。

三、连接检测

控制端点击连接后，仍然先请求：

GET http://提词器IP:47230/api/ping

提词端返回 JSON，例如：

{
  "ok": true,
  "app": "JLXC Teleprompter",
  "remote": true,
  "version": 3,
  "http": true,
  "udp": true,
  "scriptUpload": true
}

如果 `ok=true` 且 `scriptUpload=true`，控制端显示“支持远程发送文稿”。
如果没有 `scriptUpload` 字段，也不要影响原有滚动遥控，只是隐藏或禁用发送文稿功能，并提示“当前提词端版本不支持远程文稿”。

四、控制端 UI 新增入口

在控制端主界面或连接成功后的遥控界面新增一个按钮：

“发送文稿到提词器”

点击后进入文稿发送页面。

五、文稿发送页面 UI

页面包含：

1. 标题输入框
   - hint：文稿标题，可留空
2. 正文多行输入框
   - hint：粘贴提词内容
   - 支持大量文本
3. 字数统计
   - 实时显示：当前 1234 字
4. 按钮：发送到提词器
5. 按钮：清空
6. 状态提示：未发送 / 发送中 / 发送成功 / 发送失败

可选增强：
- 从剪贴板粘贴按钮
- 从 txt 文件导入按钮
- 最近发送记录

六、新增文稿接口

优先使用 JSON POST。

请求：

POST http://提词器IP:47230/api/remote/scripts/add
Content-Type: application/json; charset=utf-8

Body：

{
  "title": "文稿标题",
  "content": "完整提词内容"
}

成功返回：

{
  "ok": true,
  "id": "新文稿ID",
  "title": "文稿标题",
  "length": 123,
  "createdAt": 1780000000000,
  "updatedAt": 1780000000000
}

发送成功后提示：

“已发送到提词器：标题，123 字”

七、备用纯文本接口

如果 JSON 发送失败，可以备用：

POST http://提词器IP:47230/api/remote/scripts/add?title=URL编码后的标题
Content-Type: text/plain; charset=utf-8

Body 直接放完整文稿。

八、获取提词端文稿列表

可选实现一个“查看提词端文稿”按钮。

GET http://提词器IP:47230/api/remote/scripts

返回：

{
  "ok": true,
  "count": 2,
  "scripts": [
    {
      "id": "...",
      "title": "...",
      "length": 123,
      "createdAt": 1780000000000,
      "updatedAt": 1780000000000
    }
  ]
}

九、错误处理

请处理以下情况：

1. IP 或端口为空：提示用户输入。
2. 未连接：提示先连接提词器。
3. 正文为空：禁止发送。
4. 请求超时：提示检查两台设备是否在同一局域网。
5. 返回 ok=false：显示服务端 error。
6. 文稿太大：提示单篇文稿不要超过 2MB。
7. 提词端不支持 scriptUpload：隐藏或禁用发送文稿按钮。

十、网络要求

- 只使用局域网 HTTP，不使用公网服务器。
- 不依赖 Google 服务。
- 不需要账号登录。
- 保持国产安卓手机兼容。

十一、实现建议

新增类：

RemoteScriptClient：
- ping()
- uploadScript(title, content)
- listScripts()

新增 Activity 或 Fragment：

ScriptSendActivity：
- 标题输入
- 正文输入
- 字数统计
- 发送按钮
- 状态提示

十二、不要破坏已有功能

必须保留：
- 音量键控制滚动
- 虚拟触控板控制滚动
- UDP 高频滚动
- HTTP 兼容滚动
- 暂停/回到顶部接口
- IP/端口保存
- 深色 UI 风格
- 青绿色强调色 #39c5bb

十三、验收标准

1. 提词端打开 APP 并启用遥控服务。
2. 控制端输入提词端 IP 和端口，连接成功。
3. 控制端显示支持远程发送文稿。
4. 在控制端粘贴标题和正文。
5. 点击发送。
6. 提词端新增一篇文稿。
7. 提词端进入“开始提词”列表后能看到新文稿。
8. 原有遥控滚动功能仍然正常。

请输出完整修改后的控制端 Android 工程代码，不要只给片段。


---
新版已加入远程编辑、删除、开始提词、关闭提词。详见 REMOTE_SCRIPT_MANAGE_PROTOCOL.md 和 REMOTE_CONTROLLER_SCRIPT_MANAGE_PROMPT.md。
