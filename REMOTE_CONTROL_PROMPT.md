# 控制端 APP 生成提示词摘要

控制端需要连接提词端 IP:47230。

连接检测：
GET /api/ping

滚动：
POST /api/remote/scroll?dy=80
UDP: SCROLL 80

暂停：
POST /api/remote/pause?paused=true
UDP: PAUSE true

回到顶部：
POST /api/remote/top
UDP: TOP

统一约定：
dy>0 提词内容向上滚动，继续往后读。
dy<0 提词内容向下滚动，回退到前文。
