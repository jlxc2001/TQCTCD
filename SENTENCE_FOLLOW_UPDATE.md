# 0.3.4 Sentence Follow Update

本版本重点更新语音识别提词模式：

- 文稿按短句拆分显示，不再整段 TextView 跳转。
- 已读句逐句变暗，当前句保持最亮，未读句保持正常亮度。
- 当前句自动平滑居中，滚动目标为“当前句中心 - 屏幕高度 / 2”。
- 加入句内进度，读到一句中段时页面会轻微继续下移，不再只在句子切换时动一下。
- 支持回读上一句/上一段：匹配到前面句子时，当前句退回，后面原本已读的句子恢复为未读状态。
- 识别率设置继续生效：当前句/下一句略放宽，远距离跳读更严格。
- 保留自动滚动、遥控滚动、蓝牙/方向键/音量键、HTTP/UDP 协议和 xlarge 本地 ASR。

新增/修改核心文件：

- `app/src/main/java/com/jlxc/teleprompter/PromptActivity.java`
- `app/src/main/java/com/jlxc/teleprompter/follow/SentenceItem.java`
- `app/src/main/java/com/jlxc/teleprompter/follow/SentenceSplitter.java`
- `app/src/main/java/com/jlxc/teleprompter/follow/SpeechTextMatcher.java`
- `app/src/main/java/com/jlxc/teleprompter/follow/FollowReadState.java`
