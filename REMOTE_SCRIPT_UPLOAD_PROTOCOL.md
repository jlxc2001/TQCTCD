# 遥控端新增文稿协议

本版本新增“遥控端给提词端新增提词文件”的局域网接口。提词端默认端口仍为 `47230`。

## 1. 能力检测

GET:

```text
http://提词器IP:47230/api/ping
```

返回里包含：

```json
{
  "ok": true,
  "scriptUpload": true
}
```

控制端看到 `scriptUpload: true` 即可显示“发送文稿到提词器”功能。

## 2. 新增文稿，推荐 JSON

POST:

```text
http://提词器IP:47230/api/remote/scripts/add
Content-Type: application/json; charset=utf-8
```

Body:

```json
{
  "title": "文稿标题",
  "content": "完整提词内容"
}
```

成功返回：

```json
{
  "ok": true,
  "id": "新文稿ID",
  "title": "文稿标题",
  "length": 123,
  "createdAt": 1780000000000,
  "updatedAt": 1780000000000
}
```

## 3. 新增文稿，纯文本备用

POST:

```text
http://提词器IP:47230/api/remote/scripts/add?title=文稿标题
Content-Type: text/plain; charset=utf-8
```

Body 直接放完整文稿。

## 4. 获取文稿列表

GET:

```text
http://提词器IP:47230/api/remote/scripts
```

返回文稿摘要列表，不返回全文，避免局域网传输太大。

## 5. 限制

- 单篇远程上传上限：2MB UTF-8 文本。
- UDP 仍只用于高频滚动，不用于上传文稿。
- 遥控端必须和提词端处于同一 Wi-Fi/热点局域网。
