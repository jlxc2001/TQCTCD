package com.jlxc.teleprompter.follow;

import com.jlxc.teleprompter.util.TextUtil;

import java.util.List;

/**
 * ASR 文本与当前句附近的短句做模糊匹配。
 *
 * 这一版重点解决“必须读一句停一句才会跳下一句”的问题：
 * 1. sherpa-onnx 的 partial 结果经常是从本轮识别开头累计输出的长文本；
 * 2. 如果只把整段 ASR 文本和单句比较，第一句会因为距离权重一直胜出；
 * 3. 所以这里增加“连续阅读进度”判断：当 ASR 文本已经覆盖当前句，并且开始覆盖下一句时，
 *    立即把 CURRENT 推进到下一句，不需要用户停顿。
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
    private int pendingBacktrackIndex = -1;
    private int pendingBacktrackHits = 0;

    public void reset() {
        lastNormalized = "";
        pendingBacktrackIndex = -1;
        pendingBacktrackHits = 0;
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
        int safeCurrent = Math.max(0, Math.min(currentIndex, sentences.size() - 1));
        String norm = TextUtil.normalizeForMatch(recognized);
        if (norm.isEmpty()) {
            return new FollowMatch(false, safeCurrent, 0f, 0f, false, "empty asr");
        }

        // 永远保留最近一段，而不是只拿 delta。只拿 delta 会导致“连续读”时下一句匹配不到，
        // 表现为必须停顿等 final 结果才推进。
        String recent = norm.substring(Math.max(0, norm.length() - 120));
        String delta = "";
        if (!lastNormalized.isEmpty() && norm.startsWith(lastNormalized) && norm.length() > lastNormalized.length()) {
            delta = norm.substring(lastNormalized.length());
            if (delta.length() > 120) delta = delta.substring(delta.length() - 120);
        }
        String tail = delta.length() >= 6 ? delta : recent;
        lastNormalized = norm;

        int safeBackRange = Math.max(0, backRange);
        int safeForwardRange = Math.max(1, forwardRange);
        int start = Math.max(0, safeCurrent - safeBackRange);
        int end = Math.min(sentences.size() - 1, safeCurrent + safeForwardRange);

        FollowMatch sequence = findSequentialProgress(norm, recent, tail, sentences, safeCurrent, start, end, safeBackRange, safeForwardRange, userThreshold);
        FollowMatch local = findLocalBest(norm, recent, tail, sentences, safeCurrent, start, end, safeBackRange, safeForwardRange, userThreshold);

        FollowMatch backtrack = null;
        if (local.accepted && local.index < safeCurrent) {
            backtrack = stabilizeBacktrack(local, safeCurrent, userThreshold);
        } else {
            clearPendingBacktrack();
        }

        // 连续阅读推进优先。比如 ASR 已经包含“第1句+第2句的一半”，就应该立刻切到第2句，
        // 不要因为第1句完整匹配分数更高而停在第1句。
        if (sequence.accepted && sequence.index >= safeCurrent) {
            clearPendingBacktrack();
            if (sequence.index > safeCurrent || sequence.progress > local.progress + 0.12f) return sequence;
        }

        // 回读上一句/上一段必须经过“强匹配或连续两次命中”确认，避免 ASR 偶发错字导致字幕上下抖。
        if (backtrack != null) {
            if (backtrack.accepted) return backtrack;
            if (!sequence.accepted) return backtrack;
        }

        if (sequence.accepted && sequence.score >= local.score - 0.08f) {
            clearPendingBacktrack();
            return sequence;
        }
        if (local.accepted && local.index >= safeCurrent) clearPendingBacktrack();
        return local;
    }

    /**
     * 用“全文覆盖率”判断连续阅读位置，解决不停顿时不推进的问题。
     */
    private FollowMatch findSequentialProgress(String full,
                                               String recent,
                                               String tail,
                                               List<SentenceItem> sentences,
                                               int currentIndex,
                                               int start,
                                               int end,
                                               int backRange,
                                               int forwardRange,
                                               float userThreshold) {
        int bestIndex = currentIndex;
        float bestProgress = sentenceCoverage(full, sentences.get(currentIndex).normalized);
        float bestScore = Math.max(0.1f, bestProgress);
        String debug = "sequential";

        float base = clampThreshold(userThreshold);
        float previousRequired = Math.max(0.52f, base - 0.22f);
        float currentRequired = Math.max(0.28f, base - 0.38f);

        for (int i = currentIndex; i <= end; i++) {
            String sentence = sentences.get(i).normalized;
            if (sentence == null || sentence.isEmpty()) continue;

            float fullCoverage = sentenceCoverage(full, sentence);
            float recentCoverage = Math.max(sentenceCoverage(recent, sentence), sentenceCoverage(tail, sentence));
            float progress = Math.max(fullCoverage, recentCoverage);

            boolean previousSentencesCovered = true;
            if (i > currentIndex) {
                for (int k = currentIndex; k < i; k++) {
                    float c = sentenceCoverage(full, sentences.get(k).normalized);
                    if (c < previousRequired) {
                        previousSentencesCovered = false;
                        break;
                    }
                }
            }

            if (!previousSentencesCovered) continue;

            // 当前句只要有进度就更新句内滚动。下一句/后续句只要开始被明显覆盖，立即推进。
            float requiredProgress;
            if (i == currentIndex) requiredProgress = Math.max(0.18f, currentRequired);
            else if (i == currentIndex + 1) requiredProgress = Math.max(0.22f, currentRequired);
            else requiredProgress = Math.max(0.45f, currentRequired + 0.12f);
            if (progress < requiredProgress) continue;

            float score = progress;
            if (i > currentIndex) score += 0.18f; // 鼓励“已读完当前句后开始下一句”的自然推进
            score -= Math.max(0, i - currentIndex) * 0.015f;
            score = clamp(score);

            if (i > bestIndex || score > bestScore) {
                bestIndex = i;
                bestProgress = progress;
                bestScore = score;
                debug = "sequential coverage=" + progress + ", recent=" + recent;
            }
        }

        float required = requiredThreshold(bestIndex, currentIndex, userThreshold, backRange, forwardRange);
        // 连续推进不能按普通单句匹配阈值卡太死，否则用户必须停顿。这里按进度单独放宽。
        boolean accepted = bestIndex == currentIndex
                ? bestProgress >= 0.12f
                : bestScore >= Math.max(0.42f, required - 0.25f);
        return new FollowMatch(accepted, bestIndex, bestScore, clamp(bestProgress), false, debug);
    }

    private FollowMatch findLocalBest(String norm,
                                      String recent,
                                      String tail,
                                      List<SentenceItem> sentences,
                                      int currentIndex,
                                      int start,
                                      int end,
                                      int backRange,
                                      int forwardRange,
                                      float userThreshold) {
        int bestIndex = currentIndex;
        float bestScore = 0f;
        float bestProgress = 0f;
        String bestDebug = "local";

        for (int i = start; i <= end; i++) {
            SentenceItem sentence = sentences.get(i);
            CandidateScore fullScore = scoreOne(norm, recent, sentence.normalized);
            CandidateScore tailScore = scoreOne(tail, recent, sentence.normalized);
            CandidateScore c = fullScore.score >= tailScore.score ? fullScore : tailScore;

            // 如果一句话跨过当前句和下一句，允许“候选句+下一句”辅助判断。
            if (i + 1 < sentences.size()) {
                String joined = sentence.normalized + sentences.get(i + 1).normalized;
                CandidateScore joinedScore = scoreOne(norm, recent, joined);
                if (joinedScore.score > c.score) {
                    c = new CandidateScore(joinedScore.score, Math.min(1f, joinedScore.progress));
                }
            }

            float distance = Math.abs(i - currentIndex);
            float maxDistance = Math.max(1f, Math.max(backRange, forwardRange));
            float positionWeight = 1f - Math.min(distance, maxDistance) / maxDistance;
            float finalScore = c.score * 0.88f + positionWeight * 0.12f;

            if (finalScore > bestScore) {
                bestScore = finalScore;
                bestIndex = i;
                bestProgress = c.progress;
                bestDebug = "local range=" + start + "-" + end + ", tail=" + tail;
            }
        }

        float required = requiredThreshold(bestIndex, currentIndex, userThreshold, backRange, forwardRange);
        boolean backtracked = bestIndex < currentIndex;
        if (backtracked) {
            // 回读是重要功能，但不能因为一句短句的模糊匹配就立刻回跳。
            // 这里额外提高阈值并要求覆盖率，真正回读时仍会在 1～2 个 partial 内确认。
            required = Math.max(required + 0.10f, clampThreshold(userThreshold + 0.08f));
        }
        boolean accepted = bestScore >= required && (!backtracked || bestProgress >= 0.58f);
        return new FollowMatch(accepted, bestIndex, bestScore, clamp(bestProgress), backtracked, bestDebug);
    }

    private FollowMatch stabilizeBacktrack(FollowMatch candidate, int currentIndex, float userThreshold) {
        if (candidate == null || candidate.index >= currentIndex) return candidate;

        if (candidate.index == pendingBacktrackIndex) pendingBacktrackHits++;
        else {
            pendingBacktrackIndex = candidate.index;
            pendingBacktrackHits = 1;
        }

        float base = clampThreshold(userThreshold);
        boolean veryStrong = candidate.score >= clampThreshold(base + 0.18f) && candidate.progress >= 0.78f;
        boolean confirmed = veryStrong || pendingBacktrackHits >= 2;

        if (confirmed) {
            clearPendingBacktrack();
            return candidate;
        }

        return new FollowMatch(false, currentIndex, candidate.score, 0f, false,
                "backtrack pending " + pendingBacktrackHits + "/2 -> " + candidate.index);
    }

    private void clearPendingBacktrack() {
        pendingBacktrackIndex = -1;
        pendingBacktrackHits = 0;
    }

    private float requiredThreshold(int index, int currentIndex, float base, int backRange, int forwardRange) {
        if (index == currentIndex || index == currentIndex + 1) return clampThreshold(base - 0.05f);
        if (index < currentIndex) {
            int distance = currentIndex - index;
            // 用户允许的回读范围内使用基础阈值；越远越严格。
            if (distance <= Math.max(1, backRange)) return clampThreshold(base + Math.max(0, distance - 3) * 0.02f);
            return clampThreshold(base + 0.18f);
        }
        if (index > currentIndex + Math.min(3, Math.max(1, forwardRange))) return clampThreshold(base + 0.08f);
        return clampThreshold(base + 0.03f);
    }

    private float clampThreshold(float v) {
        return Math.max(0.35f, Math.min(0.95f, v));
    }

    /** 句子有多少比例已经出现在 ASR 文本中。更适合累计 partial。 */
    private float sentenceCoverage(String heard, String sentence) {
        if (heard == null || sentence == null || heard.isEmpty() || sentence.isEmpty()) return 0f;
        if (heard.contains(sentence)) return 1f;
        if (sentence.contains(heard)) return Math.min(1f, heard.length() / (float) Math.max(1, sentence.length()));
        int lcs = lcsLength(heard, sentence);
        return clamp(lcs / (float) Math.max(1, sentence.length()));
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
