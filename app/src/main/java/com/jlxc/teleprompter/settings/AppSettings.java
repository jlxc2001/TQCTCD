package com.jlxc.teleprompter.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;

public class AppSettings {
    public static final int MODE_AUTO = 0;
    public static final int MODE_VOICE = 1;
    public static final int MODE_REMOTE = 2;

    private final SharedPreferences sp;
    public AppSettings(Context c) { sp = c.getSharedPreferences("app_settings", Context.MODE_PRIVATE); }

    public int backgroundColor() { return sp.getInt("bg", Color.BLACK); }
    public void setBackgroundColor(int color) { sp.edit().putInt("bg", color).apply(); }

    public int textColor() { return sp.getInt("fg", Color.WHITE); }
    public void setTextColor(int color) { sp.edit().putInt("fg", color).apply(); }

    public int completedTextColor() { return sp.getInt("completed_fg", Color.rgb(80, 80, 80)); }

    public float textSizeSp() { return sp.getFloat("text_size", 36f); }
    public void setTextSizeSp(float spValue) { sp.edit().putFloat("text_size", spValue).apply(); }

    public boolean mirrorPrompt() { return sp.getBoolean("mirror_prompt", false); }
    public void setMirrorPrompt(boolean on) { sp.edit().putBoolean("mirror_prompt", on).apply(); }

    public int orientationMode() { return sp.getInt("orientation", 0); }
    public void setOrientationMode(int m) { sp.edit().putInt("orientation", m).apply(); }
    public int androidOrientation() {
        int m = orientationMode();
        if (m == 1) return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        if (m == 2) return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        if (m == 3) return ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
        return ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    }

    public int mode() { return sp.getInt("mode", MODE_AUTO); }
    public void setMode(int mode) { sp.edit().putInt("mode", mode).apply(); }

    public float autoSpeedPxPerSec() { return sp.getFloat("auto_speed", 42f); }
    public void setAutoSpeedPxPerSec(float v) { sp.edit().putFloat("auto_speed", v).apply(); }

    public float voiceThreshold() { return sp.getFloat("voice_threshold", 0.72f); }
    public void setVoiceThreshold(float v) { sp.edit().putFloat("voice_threshold", v).apply(); }

    /**
     * 识别音量增益。1.0 表示原始音量；顶配机可以适当提高到 1.5～2.5。
     * 过高会削波，反而可能降低识别稳定性，因此限制在 0.25～5.0。
     */
    public float voiceInputGain() { return sp.getFloat("voice_input_gain", 1.0f); }
    public void setVoiceInputGain(float v) {
        float safe = Math.max(0.25f, Math.min(5.0f, v));
        sp.edit().putFloat("voice_input_gain", safe).apply();
    }


    /**
     * 语音模式显示提前量。ASR 有天然延迟，默认把高亮提前 1 句，
     * 让屏幕上最亮的内容更接近用户“马上要读”的位置。
     */
    public int voiceDisplayLeadSentences() { return sp.getInt("voice_display_lead", 1); }
    public void setVoiceDisplayLeadSentences(int v) { sp.edit().putInt("voice_display_lead", Math.max(0, Math.min(2, v))).apply(); }

    /**
     * 允许语音跟读向前回读的句数。0 表示禁止自动回读，默认 3 句。
     * 数值越大越容易追回前文，但也更容易被相似短句误触发，因此限制在 0～20。
     */
    public int voiceBacktrackSentences() { return sp.getInt("voice_backtrack_sentences", 3); }
    public void setVoiceBacktrackSentences(int v) { sp.edit().putInt("voice_backtrack_sentences", Math.max(0, Math.min(20, v))).apply(); }

    /**
     * 允许语音跟读向后跳读的句数。1 表示只允许自然推进到下一句，默认 12 句。
     * 数值越大越适合跳过内容，但远距离跳转会自动提高匹配阈值。
     */
    public int voiceForwardJumpSentences() { return sp.getInt("voice_forward_jump_sentences", 12); }
    public void setVoiceForwardJumpSentences(int v) { sp.edit().putInt("voice_forward_jump_sentences", Math.max(1, Math.min(40, v))).apply(); }

    public int remotePort() { return sp.getInt("remote_port", 47230); }
    public void setRemotePort(int port) { sp.edit().putInt("remote_port", port).apply(); }

    public boolean remoteEnabled() { return sp.getBoolean("remote_enabled", true); }
    public void setRemoteEnabled(boolean enabled) { sp.edit().putBoolean("remote_enabled", enabled).apply(); }
}
