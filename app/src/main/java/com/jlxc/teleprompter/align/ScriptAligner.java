package com.jlxc.teleprompter.align;

import com.jlxc.teleprompter.util.TextUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 提词器语音跟读/回读核心。
 *
 * 设计重点：
 * 1. 不是从全文硬匹配，而是在当前进度附近搜索，降低跳飞概率。
 * 2. 搜索窗口包含前 3 句，因此用户发现上一句口误并回读时，可以自动退回上一句。
 * 3. 识别率阈值可调，允许用户文案临场小改字仍然继续跟随。
 */
public class ScriptAligner {
    public static class Sentence {
        public final int index;
        public final int start;
        public final int end;
        public final String raw;
        public final String normalized;
        Sentence(int index, int start, int end, String raw) {
            this.index = index;
            this.start = start;
            this.end = end;
            this.raw = raw;
            this.normalized = TextUtil.normalizeForMatch(raw);
        }
    }

    public static class AlignmentResult {
        public final int activeIndex;
        public final int completedUntilExclusive;
        public final float score;
        public final boolean backtracked;
        public final int scrollCharOffset;
        public final String matchedSentence;

        AlignmentResult(int activeIndex, int completedUntilExclusive, float score, boolean backtracked, int scrollCharOffset, String matchedSentence) {
            this.activeIndex = activeIndex;
            this.completedUntilExclusive = completedUntilExclusive;
            this.score = score;
            this.backtracked = backtracked;
            this.scrollCharOffset = scrollCharOffset;
            this.matchedSentence = matchedSentence;
        }
    }

    private final String script;
    private final List<Sentence> sentences = new ArrayList<>();
    private int activeIndex = 0;
    private int completedUntilExclusive = 0;
    private String lastRecognized = "";

    public ScriptAligner(String script) {
        this.script = script == null ? "" : script;
        List<int[]> ranges = TextUtil.splitSentences(this.script);
        for (int i = 0; i < ranges.size(); i++) {
            int[] r = ranges.get(i);
            sentences.add(new Sentence(i, r[0], r[1], this.script.substring(r[0], r[1])));
        }
    }

    public List<Sentence> sentences() { return sentences; }
    public int activeIndex() { return activeIndex; }
    public int completedUntilExclusive() { return completedUntilExclusive; }

    public AlignmentResult onRecognizedText(String recognized, float threshold) {
        String norm = TextUtil.normalizeForMatch(recognized);
        if (norm.isEmpty() || sentences.isEmpty()) {
            return currentResult(0f, false, "");
        }

        // ASR 常返回累计文本。若发现新文本包含旧文本，只取新增尾部；否则用完整文本匹配。
        String matchNorm = norm;
        if (!lastRecognized.isEmpty() && norm.startsWith(lastRecognized) && norm.length() > lastRecognized.length()) {
            String tail = norm.substring(lastRecognized.length());
            if (tail.length() >= 2) matchNorm = tail;
        }
        lastRecognized = norm;

        int start = Math.max(0, activeIndex - 3);
        int end = Math.min(sentences.size() - 1, activeIndex + 6);
        int bestIndex = activeIndex;
        float bestScore = 0f;

        for (int i = start; i <= end; i++) {
            Sentence s = sentences.get(i);
            float score = sentenceScore(matchNorm, s.normalized);

            // 两句连续朗读时，允许与当前句+下一句联合匹配。
            if (i + 1 < sentences.size()) {
                String joined = s.normalized + sentences.get(i + 1).normalized;
                score = Math.max(score, sentenceScore(matchNorm, joined));
            }
            if (score > bestScore) {
                bestScore = score;
                bestIndex = i;
            }
        }

        if (bestScore < threshold) {
            return currentResult(bestScore, false, "");
        }

        boolean backtracked = bestIndex < activeIndex;

        if (backtracked) {
            // 回读核心：检测到用户读的是前面的句子，立即把“当前句”和已完成位置退回。
            activeIndex = bestIndex;
            completedUntilExclusive = bestIndex;
        } else {
            Sentence best = sentences.get(bestIndex);
            float coverage = best.normalized.isEmpty() ? 1f : Math.min(1f, (float) matchNorm.length() / Math.max(1, best.normalized.length()));
            if (coverage > 0.45f || bestScore > Math.max(threshold, 0.82f)) {
                completedUntilExclusive = Math.max(completedUntilExclusive, bestIndex + 1);
                activeIndex = Math.min(sentences.size() - 1, Math.max(activeIndex, bestIndex + 1));
            } else {
                activeIndex = bestIndex;
            }
        }

        Sentence s = sentences.get(Math.max(0, Math.min(activeIndex, sentences.size() - 1)));
        return new AlignmentResult(activeIndex, completedUntilExclusive, bestScore, backtracked, s.start, s.raw);
    }

    public void jumpToSentence(int index) {
        if (sentences.isEmpty()) return;
        activeIndex = Math.max(0, Math.min(index, sentences.size() - 1));
        completedUntilExclusive = Math.min(completedUntilExclusive, activeIndex);
        lastRecognized = "";
    }

    private AlignmentResult currentResult(float score, boolean backtracked, String matched) {
        if (sentences.isEmpty()) return new AlignmentResult(0, 0, score, backtracked, 0, matched);
        Sentence s = sentences.get(Math.max(0, Math.min(activeIndex, sentences.size() - 1)));
        return new AlignmentResult(activeIndex, completedUntilExclusive, score, backtracked, s.start, matched);
    }

    private float sentenceScore(String heard, String sentence) {
        if (heard.isEmpty() || sentence.isEmpty()) return 0f;
        if (sentence.contains(heard) || heard.contains(sentence)) {
            float coverage = Math.min(heard.length(), sentence.length()) / (float) Math.max(heard.length(), sentence.length());
            return 0.72f + coverage * 0.28f;
        }

        // 短句容易误判，短句要求略高；长句允许窗口截取对齐。
        int h = heard.length();
        int s = sentence.length();
        float best;
        if (s > h + 6) {
            best = 0f;
            int window = Math.min(s, Math.max(4, h + 4));
            for (int i = 0; i + window <= s; i += Math.max(1, window / 3)) {
                String sub = sentence.substring(i, i + window);
                best = Math.max(best, TextUtil.levenshteinSimilarity(heard, sub));
            }
            best = Math.max(best, TextUtil.levenshteinSimilarity(heard, sentence.substring(Math.max(0, s - window))));
        } else {
            best = TextUtil.levenshteinSimilarity(heard, sentence);
        }
        return best;
    }
}
