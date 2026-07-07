package com.jlxc.teleprompter.follow;

import java.util.List;

/** 维护已读/当前/未读状态。回读时会把后面句子恢复成 UNREAD。 */
public class FollowReadState {
    private final List<SentenceItem> sentences;
    private int currentIndex = 0;
    private float currentProgress = 0f;

    public FollowReadState(List<SentenceItem> sentences) {
        this.sentences = sentences;
        applyState(0, 0f);
    }

    public int currentIndex() { return currentIndex; }
    public float currentProgress() { return currentProgress; }

    public void moveTo(int newIndex, float progress) {
        if (sentences == null || sentences.isEmpty()) return;
        int safe = Math.max(0, Math.min(newIndex, sentences.size() - 1));
        float p = Math.max(0f, Math.min(1f, progress));
        if (safe == currentIndex) {
            // 同一句内部只允许进度前进；如果 ASR 重新开始给短片段，则不让进度突然后退。
            p = Math.max(currentProgress, p);
        }
        applyState(safe, p);
    }

    public void resetTo(int index) {
        applyState(Math.max(0, Math.min(index, sentences.size() - 1)), 0f);
    }

    private void applyState(int index, float progress) {
        currentIndex = index;
        currentProgress = progress;
        if (sentences == null) return;
        for (int i = 0; i < sentences.size(); i++) {
            SentenceItem item = sentences.get(i);
            if (i < currentIndex) {
                item.state = SentenceItem.ReadState.READ;
                item.progress = 1f;
            } else if (i == currentIndex) {
                item.state = SentenceItem.ReadState.CURRENT;
                item.progress = currentProgress;
            } else {
                item.state = SentenceItem.ReadState.UNREAD;
                item.progress = 0f;
            }
        }
    }
}
