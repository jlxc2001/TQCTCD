请基于我现有的安卓“提词器遥控器 APP”继续修改，不要重做项目。现在提词端已经支持远程文稿管理和远程开始/关闭提词，请在遥控端加入完整的文稿管理功能。

一、保留现有功能

已有功能不能破坏：IP/端口连接、音量键滚动、虚拟触控板滚动、UDP 高频滚动、HTTP 兼容滚动、暂停/继续、回到顶部、发送文稿到提词器、深色 UI、青绿色强调色 #39c5bb。

二、新增目标

遥控端手机可以管理提词端平板里的文稿：
1. 查看提词端文稿列表。
2. 新增文稿。
3. 编辑已有文稿标题和正文。
4. 删除已有文稿。
5. 选择某篇文稿并让提词端直接开始提词。
6. 远程关闭提词端正在进行的提词页面。

三、能力检测

连接后请求：
GET http://提词端IP:47230/api/ping

如果返回 ok=true 且 scriptManage=true、remotePrompt=true，则显示“支持远程文稿管理/远程开始提词”。如果字段不存在，只保留旧遥控功能。

四、接口协议

1）文稿列表：
GET /api/remote/scripts

返回：
{
  "ok": true,
  "count": 2,
  "activePrompt": true,
  "activeScriptId": "...",
  "scripts": [
    {"id":"...","title":"...","length":123,"createdAt":1780000000000,"updatedAt":1780000000000}
  ]
}

2）获取全文：
GET /api/remote/scripts/get?id=URL编码后的id

返回 id、title、content、length、createdAt、updatedAt。编辑页面打开前必须先请求这个接口。

3）新增文稿：
POST /api/remote/scripts/add
Content-Type: application/json; charset=utf-8

{"title":"标题","content":"完整提词内容"}

4）编辑文稿：
POST /api/remote/scripts/update?id=URL编码后的id
Content-Type: application/json; charset=utf-8

{"title":"新标题","content":"新的完整提词内容"}

5）删除文稿：
POST /api/remote/scripts/delete?id=URL编码后的id

删除前弹确认框。删除成功后刷新列表。如果删除的是正在提词的文稿，提词端会自动关闭提词。

6）远程开始提词：
POST /api/remote/prompt/start?id=URL编码后的id

成功后提示“已在提词端开始：标题”，并刷新提词状态。

7）远程关闭提词：
POST /api/remote/prompt/stop

成功后提示“已关闭提词”。

8）提词状态：
GET /api/remote/prompt/status

返回：
{"ok":true,"active":true,"scriptId":"...","title":"..."}

五、遥控端 UI 建议

连接成功后新增入口：
“文稿管理”

文稿管理页面：
1. 顶部显示连接的 IP:端口。
2. 显示当前提词状态：未提词 / 正在提词：标题。
3. 按钮：刷新列表。
4. 按钮：新增文稿。
5. 按钮：关闭提词。
6. 下方列表显示提词端文稿，每个卡片显示标题、字数、更新时间。
7. 每个文稿卡片提供按钮：开始提词、编辑、删除。

新增/编辑页面：
1. 标题输入框。
2. 正文多行输入框。
3. 字数统计。
4. 从剪贴板粘贴按钮。
5. 保存到提词器按钮。

六、错误处理

需要处理 IP/端口为空、未连接、接口超时、提词端不支持新协议、文稿为空、文稿过大、删除确认、重复点击、网络断开。所有错误不要闪退，要显示中文提示。

七、实现建议

新增 RemoteScriptClient：ping、listScripts、getScript、addScript、updateScript、deleteScript、startPrompt、stopPrompt、getPromptStatus。

新增 ScriptManageActivity：列表/状态/刷新/新增/关闭提词。
新增 ScriptEditActivity：新增和编辑共用。

八、验收标准

1. 连接提词端后可以看到文稿列表。
2. 可以在遥控端新增文稿，并在提词端列表看到。
3. 可以编辑提词端已有文稿。
4. 可以删除提词端文稿。
5. 可以点击某篇文稿让提词端直接开始提词。
6. 可以远程关闭提词端提词页面。
7. 原有滚动遥控功能不受影响。

请输出完整可编译 Android 工程，不要只给片段。
