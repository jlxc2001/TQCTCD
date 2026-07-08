# 0.3.8 Keep Screen On

- 提词页面 `PromptActivity` 启动后自动添加 `FLAG_KEEP_SCREEN_ON`。
- 在 `onResume()` 再次确认常亮，防止切后台回来后失效。
- 退出提词页面时清除常亮 flag。
- 仅提词页面保持屏幕常亮，主界面/设置页不强制常亮。
