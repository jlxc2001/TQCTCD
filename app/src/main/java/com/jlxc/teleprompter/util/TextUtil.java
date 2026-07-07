package com.jlxc.teleprompter.util;

import java.util.ArrayList;
import java.util.List;

public final class TextUtil {
    private TextUtil() {}

    public static int dp(android.content.Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static String normalizeForMatch(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            // 全角英数转半角，大小写统一，去标点/空白。中文保留。
            if (ch >= 'Ａ' && ch <= 'Ｚ') ch = (char) ('A' + ch - 'Ａ');
            else if (ch >= 'ａ' && ch <= 'ｚ') ch = (char) ('a' + ch - 'ａ');
            else if (ch >= '０' && ch <= '９') ch = (char) ('0' + ch - '０');
            if (Character.isLetterOrDigit(ch) || isCjk(ch)) {
                out.append(Character.toLowerCase(ch));
            }
        }
        return out.toString();
    }

    public static boolean isCjk(char ch) {
        Character.UnicodeBlock b = Character.UnicodeBlock.of(ch);
        return b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || b == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || b == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || b == Character.UnicodeBlock.HIRAGANA
                || b == Character.UnicodeBlock.KATAKANA
                || b == Character.UnicodeBlock.HANGUL_SYLLABLES;
    }

    public static List<int[]> splitSentences(String text) {
        List<int[]> res = new ArrayList<>();
        if (text == null || text.isEmpty()) return res;
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (isSentenceEnd(ch) || ch == '\n') {
                int end = i + 1;
                if (trimNonContent(text, start, end)) res.add(new int[]{start, end});
                start = end;
            }
        }
        if (start < text.length() && trimNonContent(text, start, text.length())) {
            res.add(new int[]{start, text.length()});
        }
        if (res.isEmpty()) res.add(new int[]{0, text.length()});
        return res;
    }

    private static boolean trimNonContent(String text, int start, int end) {
        for (int i = start; i < end; i++) {
            if (Character.isLetterOrDigit(text.charAt(i)) || isCjk(text.charAt(i))) return true;
        }
        return false;
    }

    private static boolean isSentenceEnd(char ch) {
        return ch == '。' || ch == '！' || ch == '？' || ch == '!' || ch == '?' || ch == '.' || ch == '；' || ch == ';';
    }

    public static float levenshteinSimilarity(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        if (a.equals(b)) return 1f;
        if (a.isEmpty() || b.isEmpty()) return 0f;
        int n = a.length(), m = b.length();
        int[] prev = new int[m + 1];
        int[] cur = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;
        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = ca == b.charAt(j - 1) ? 0 : 1;
                int v = Math.min(prev[j] + 1, cur[j - 1] + 1);
                cur[j] = Math.min(v, prev[j - 1] + cost);
            }
            int[] tmp = prev; prev = cur; cur = tmp;
        }
        int d = prev[m];
        return 1f - (float) d / Math.max(n, m);
    }
}
