package com.jlxc.teleprompter.follow;

import com.jlxc.teleprompter.util.TextUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 将文稿拆成适合提词器跟读的短句。逗号也作为切分点，但会把过短片段合并，
 * 避免一句只有一两个字导致 ASR 匹配乱跳。
 */
public final class SentenceSplitter {
    private SentenceSplitter() {}

    public static List<SentenceItem> split(String text) {
        String source = text == null ? "" : text;
        List<int[]> rawRanges = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < source.length(); i++) {
            char ch = source.charAt(i);
            if (isBreak(ch)) {
                addIfMeaningful(source, rawRanges, start, i + 1);
                start = i + 1;
            }
        }
        addIfMeaningful(source, rawRanges, start, source.length());
        if (rawRanges.isEmpty() && !source.isEmpty()) rawRanges.add(new int[]{0, source.length()});

        List<int[]> merged = mergeTooShort(source, rawRanges);
        List<SentenceItem> out = new ArrayList<>();
        for (int i = 0; i < merged.size(); i++) {
            int[] r = merged.get(i);
            String raw = source.substring(r[0], r[1]);
            out.add(new SentenceItem(i, r[0], r[1], raw, TextUtil.normalizeForMatch(raw)));
        }
        if (!out.isEmpty()) out.get(0).state = SentenceItem.ReadState.CURRENT;
        return out;
    }

    private static boolean isBreak(char ch) {
        return ch == '。' || ch == '，' || ch == ',' || ch == '、'
                || ch == '？' || ch == '?' || ch == '！' || ch == '!'
                || ch == '.' || ch == '；' || ch == ';' || ch == '\n' || ch == '\r';
    }

    private static void addIfMeaningful(String text, List<int[]> ranges, int start, int end) {
        if (start >= end) return;
        for (int i = start; i < end; i++) {
            char ch = text.charAt(i);
            if (isContentChar(ch)) {
                ranges.add(new int[]{start, end});
                return;
            }
        }
    }

    private static List<int[]> mergeTooShort(String text, List<int[]> ranges) {
        List<int[]> merged = new ArrayList<>();
        int pendingStart = -1;
        int pendingEnd = -1;
        for (int[] r : ranges) {
            if (pendingStart < 0) {
                pendingStart = r[0];
                pendingEnd = r[1];
            } else if (contentLength(text, pendingStart, pendingEnd) < 4) {
                pendingEnd = r[1];
            } else {
                merged.add(new int[]{pendingStart, pendingEnd});
                pendingStart = r[0];
                pendingEnd = r[1];
            }
        }
        if (pendingStart >= 0) {
            if (!merged.isEmpty() && contentLength(text, pendingStart, pendingEnd) < 4) {
                merged.get(merged.size() - 1)[1] = pendingEnd;
            } else {
                merged.add(new int[]{pendingStart, pendingEnd});
            }
        }
        return merged;
    }

    private static boolean isContentChar(char ch) {
        return Character.isLetterOrDigit(ch) || (TextUtil.isCjk(ch) && !isBreak(ch) && !Character.isWhitespace(ch));
    }

    private static int contentLength(String text, int start, int end) {
        int count = 0;
        for (int i = start; i < end; i++) {
            char ch = text.charAt(i);
            if (isContentChar(ch)) count++;
        }
        return count;
    }
}
