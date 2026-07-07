package com.jlxc.teleprompter.follow;

public class SentenceItem {
    public enum ReadState { READ, CURRENT, UNREAD }

    public final int index;
    public final int start;
    public final int end;
    public final String raw;
    public final String normalized;
    public ReadState state = ReadState.UNREAD;
    public float progress = 0f;

    public SentenceItem(int index, int start, int end, String raw, String normalized) {
        this.index = index;
        this.start = start;
        this.end = end;
        this.raw = raw;
        this.normalized = normalized;
    }
}
