# 0.3.5 live sentence follow fix

本次修复针对语音提词模式的真实使用问题：

1. 不再需要“读一句停一句”才推进。
   - `SpeechTextMatcher` 增加连续 partial 识别进度判断。
   - 当 ASR 累计文本已经覆盖当前句并开始覆盖下一句时，立即推进 CURRENT 句。
   - 保留上一句/上一段回读匹配能力。

2. 已读/当前/未读状态更明显。
   - READ：文字透明度降到约 28%。
   - CURRENT：100% 文字 + 加粗 + 半透明高亮背景。
   - UNREAD：约 68% 透明度，避免和当前句混在一起。

3. 提词页面进入沉浸式全屏。
   - 隐藏系统状态栏。
   - 隐藏导航栏。
   - ASR 调试文字不再显示在屏幕顶部，长按打开控制面板时才显示最近状态。

4. 修复 `PromptActivity.java` 中可能导致编译失败的重复 `statusView.setText` 残留。

版本：0.3.5-live-sentence-follow
