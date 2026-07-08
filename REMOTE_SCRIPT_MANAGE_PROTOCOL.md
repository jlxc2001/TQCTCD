# 遥控端文稿管理与远程提词协议

默认端口：47230。所有接口均为局域网 HTTP，UTF-8 JSON。

## 能力检测

GET /api/ping

返回字段包含：scriptUpload、scriptManage、scriptEdit、scriptDelete、remotePrompt、remoteStartPrompt、remoteStopPrompt。

## 文稿列表

GET /api/remote/scripts

只返回摘要，不返回正文。

## 获取单篇文稿全文

GET /api/remote/scripts/get?id=<scriptId>

返回：id、title、content、length、createdAt、updatedAt。

## 新增文稿

POST /api/remote/scripts/add
Content-Type: application/json; charset=utf-8

{
  "title": "标题",
  "content": "完整提词内容"
}

## 编辑文稿

POST /api/remote/scripts/update?id=<scriptId>
Content-Type: application/json; charset=utf-8

{
  "title": "新标题",
  "content": "新的完整提词内容"
}

也可以把 id 放在 JSON 里：

{
  "id": "scriptId",
  "title": "新标题",
  "content": "新的完整提词内容"
}

## 删除文稿

POST /api/remote/scripts/delete?id=<scriptId>

或：

DELETE /api/remote/scripts/delete?id=<scriptId>

如果删除的是当前正在提词的文稿，提词端会自动关闭提词页面。

## 远程开始提词

POST /api/remote/prompt/start?id=<scriptId>

或 JSON：

{ "id": "scriptId" }

提词端会直接打开对应文稿的提词页面。

## 远程关闭提词

POST /api/remote/prompt/stop

## 当前提词状态

GET /api/remote/prompt/status

返回：active、scriptId、title。
