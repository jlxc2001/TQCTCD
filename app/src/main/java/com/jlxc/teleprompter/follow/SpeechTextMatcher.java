package com.jlxc.teleprompter.follow;

import com.jlxc.teleprompter.util.TextUtil;

import java.util.List;

/**
 * ASR 文本与当前句附近的短句做模糊匹配。
 * 目标不是全文跳转，而是稳定判断“当前正在读哪一句”。
 */
public class SpeechTextMatcher {
    public static class FollowMatch {
        public final boolean accepted;
        public final int index;
        public final float score;
        public final float progress;
        public final boolean backtracked;
        public final String debug;

        FollowMatch(boolean accepted, int index, float score, float progress, boolean backtracked, String debug) {
            this.accepted = accepted;
            this.index = index;
            this.score = score;
            this.progress = progress;
            this.backtracked = backtracked;
            this.debug = debug;
        }
    }

    private String lastNormalized = "";

    public void reset() {
        lastNormalized = "";
    }

    public FollowMatch findBestMatch(String recognized,
                                     List<SentenceItem> sentences,
                                     int currentIndex,
                                     int backRange,
                                     int forwardRange,
                                     float userThreshold) {
        if (sentences == null || sentences.isEmpty()) {
            return new FollowMatch(false, 0, 0f, 0f, false, "empty script");
        }
        String norm = TextUtil.normalizeForMatch(recognized);
        if (norm.isEmpty()) {
            return new FollowMatch(false, currentIndex, 0f, 0f, false, "empty asr");
        }

        // 流式 ASR 可能给累计文本，也可能给当前片段。为了避免上一段文本长期干扰，
        // 同时用完整文本和尾部文本匹配，选分数最高者。
        String tail = norm;
        int maxTail = 80;
        if (tail.length() > maxTail) tail = tail.substring(tail.length() - maxTail);
        if (!lastNormalized.isEmpty() && norm.startsWith(lastNormalized) && norm.length() > lastNormalized.length()) {
            String delta = norm.substring(lastNormalized.length());
            if (delta.length() >= 2) tail = delta.length() > maxTail ? delta.substring(delta.length() - maxTail) : delta;
        }
        lastNormalized = norm;

        int start = Math.max(0, currentIndex - backRange);
        int end = Math.min(sentences.size() - 1, currentIndex + forwardRange);
        int bestIndex = currentIndex;
        float bestScore = 0f;
        float bestProgress = 0f;
        String bestDebug = "";

        for (int i = start; i <= end; i++) {
            SentenceItem sentence = sentences.get(i);
            CandidateScore a = scoreOne(norm, tail, sentence.normalized);
            CandidateScore b = scoreOne(tail, tail, sentence.normalized);
            CandidateScore c = a.score >= b.score ? a : b;

            // 如果一句话跨过当前句和下一句，允许“当前句+下一句”辅助判断，
            // 但真正激活的索引仍然是当前候选 i。
            if (i + 1 < sentences.size()) {
                String joined = sentence.normalized + sentences.get(i + 1).normalized;
                CandidateScore joinedScore = scoreOne(norm, tail, joined);
                if (joinedScore.score > c.score) {
                    c = new CandidateScore(joinedScore.score, Math.min(1f, joinedScore.progress));
                }
            }

            float distance = Math.abs(i - currentIndex);
            float positionWeight = 1f - Math.min(distance, 12f) / 12f;
            float finalScore = c.score * 0.9f + positionWeight * 0.1f;

            if (finalScore > bestScore) {
                bestScore = finalScore;
                bestIndex = i;
                bestProgress = c.progress;
                bestDebug = "range=" + start + "-" + end + ", tail=" + tail;
            }
        }

        float required = requiredThreshold(bestIndex, currentIndex, userThreshold);
        boolean accepted = bestScore >= required;
        boolean backtracked = accepted && bestIndex < currentIndex;
        return new FollowMatch(accepted, bestIndex, bestScore, clamp(bestProgress), backtracked, bestDebug);
    }

    private float requiredThreshold(int index, int currentIndex, float base) {
        if (index == currentIndex || index == currentIndex + 1) return clampThreshold(base - 0.05f);
        if (index < currentIndex && currentIndex - index <= 5) return clampThreshold(base);
        if (index > currentIndex + 3) return clampThreshold(base + 0.08f);
        return clampThreshold(base + 0.03f);
    }

    private float clampThreshold(float v) {
        return Math.max(0.35f, Math.min(0.95f, v));
    }

    private CandidateScore scoreOne(String fullHeard, String tailHeard, String sentence) {
        String heard = fullHeard == null ? "" : fullHeard;
        String tail = tailHeard == null ? "" : tailHeard;
        if (sentence == null || sentence.isEmpty() || (heard.isEmpty() && tail.isEmpty())) return new CandidateScore(0f, 0f);

        CandidateScore s1 = scoreDirect(heard, sentence);
        CandidateScore s2 = scoreDirect(tail, sentence);
        return s1.score >= s2.score ? s1 : s2;
    }

    private CandidateScore scoreDirect(String heard, String sentence) {
        if (heard == null || heard.isEmpty() || sentence.isEmpty()) return new CandidateScore(0f, 0f);

        float progress;
        float containsScore = 0f;
        if (heard.contains(sentence)) {
            containsScore = 1f;
            progress = 1f;
        } else if (sentence.contains(heard)) {
            progress = heard.length() / (float) Math.max(1, sentence.length());
            containsScore = 0.70f + progress * 0.25f;
        } else {
            int lcs = lcsLength(heard, sentence);
            progress = lcs / (float) Math.max(1, sentence.length());
            float lcsSim = lcs * 2f / Math.max(1, heard.length() + sentence.length());
            float edit = bestWindowEditSimilarity(heard, sentence);
            containsScore = lcsSim * 0.65f + edit * 0.35f;
        }

        int lcs = lcsLength(heard, sentence);
        float lcsRatio = lcs * 2f / Math.max(1, heard.length() + sentence.length());
        float editScore = bestWindowEditSimilarity(heard, sentence);
        float score = lcsRatio * 0.60f + containsScore * 0.30f + editScore * 0.10f;
        return new CandidateScore(clamp(score), clamp(progress));
    }

    private float bestWindowEditSimilarity(String heard, String sentence) {
        if (heard.isEmpty() || sentence.isEmpty()) return 0f;
        if (sentence.length() <= heard.length() + 6) return TextUtil.levenshteinSimilarity(heard, sentence);
        int window = Math.min(sentence.length(), Math.max(4, heard.length() + 4));
        int step = Math.max(1, window / 3);
        float best = 0f;
        for (int i = 0; i + window <= sentence.length(); i += step) {
            best = Math.max(best, TextUtil.levenshteinSimilarity(heard, sentence.substring(i, i + window)));
        }
        best = Math.max(best, TextUtil.levenshteinSimilarity(heard, sentence.substring(sentence.length() - window)));
        return best;
    }

    private int lcsLength(String a, String b) {
        int n = a.length();
        int m = b.length();
        if (n == 0 || m == 0) return 0;
        int[] prev = new int[m + 1];
        int[] cur = new int[m + 1];
        for (int i = 1; i <= n; i++) {
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                if (ca == b.charAt(j - 1)) cur[j] = prev[j - 1] + 1;
                else cur[j] = Math.max(prev[j], cur[j - 1]);
            }
            int[] tmp = prev; prev = cur; cur = tmp;
            java.util.Arrays.fill(cur, 0);
        }
        return prev[m];
    }

    private float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static class CandidateScore {
        final float score;
        final float progress;
        CandidateScore(float score, float progress) {
            this.score = score;
            this.progress = progress;
        }
    }
}
