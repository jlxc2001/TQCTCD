package com.jlxc.teleprompter.asr;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;
import java.util.Locale;

/**
 * 系统 SpeechRecognizer 仅作为开发调试兜底，不保证完全离线。
 * 国产系统如果没有可用的系统语音识别服务，会返回“系统语音识别不可用”。
 * 真正上线建议替换成 SherpaOnnxAsrEngine。
 */
public class AndroidSpeechRecognizerEngine implements AsrEngine, RecognitionListener {
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private Listener listener;
    private boolean running;
    private boolean restarting;
    private long lastStartMs;

    public AndroidSpeechRecognizerEngine(Context context) { this.context = context; }

    @Override public void start(Listener listener) {
        this.listener = listener;
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            if (listener != null) listener.onError("系统语音识别不可用；需要接入 sherpa-onnx 本地模型后才能完全离线识别");
            return;
        }
        running = true;
        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context);
            recognizer.setRecognitionListener(this);
            if (listener != null) listener.onReady(name());
            startListening();
        } catch (Exception e) {
            if (listener != null) listener.onError("创建系统语音识别失败：" + e.getMessage());
        }
    }

    private void startListening() {
        if (!running || recognizer == null) return;
        long now = System.currentTimeMillis();
        if (now - lastStartMs < 350) {
            scheduleRestart(350);
            return;
        }
        lastStartMs = now;
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.SIMPLIFIED_CHINESE.toLanguageTag());
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.SIMPLIFIED_CHINESE.toLanguageTag());
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 900L);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 650L);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2500L);
        try { recognizer.startListening(i); } catch (Exception e) {
            if (listener != null) listener.onError("启动系统语音识别失败：" + e.getMessage());
            scheduleRestart(600);
        }
    }

    @Override public void stop() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        if (recognizer != null) {
            try { recognizer.cancel(); } catch (Exception ignored) {}
            try { recognizer.destroy(); } catch (Exception ignored) {}
        }
        recognizer = null;
    }

    @Override public boolean isAvailable() { return SpeechRecognizer.isRecognitionAvailable(context); }
    @Override public String name() { return "系统语音识别调试引擎"; }

    @Override public void onReadyForSpeech(Bundle params) {
        if (listener != null) listener.onReady(name() + " · 正在听");
    }
    @Override public void onBeginningOfSpeech() {}
    @Override public void onRmsChanged(float rmsdB) {}
    @Override public void onBufferReceived(byte[] buffer) {}
    @Override public void onEndOfSpeech() { scheduleRestart(250); }
    @Override public void onError(int error) {
        if (listener != null) listener.onError("ASR " + errorName(error));
        scheduleRestart(error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ? 900 : 350);
    }
    @Override public void onResults(Bundle results) {
        String best = bestText(results);
        if (best != null && listener != null) listener.onFinalText(best);
        scheduleRestart(250);
    }
    @Override public void onPartialResults(Bundle partialResults) {
        String best = bestText(partialResults);
        if (best != null && listener != null) listener.onPartialText(best);
    }
    @Override public void onEvent(int eventType, Bundle params) {}

    private String bestText(Bundle b) {
        if (b == null) return null;
        ArrayList<String> list = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (list == null || list.isEmpty()) return null;
        for (String s : list) if (s != null && !s.trim().isEmpty()) return s;
        return null;
    }

    private void scheduleRestart(long delayMs) {
        if (!running || restarting) return;
        restarting = true;
        handler.postDelayed(() -> {
            restarting = false;
            startListening();
        }, delayMs);
    }

    private String errorName(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO: return "录音错误";
            case SpeechRecognizer.ERROR_CLIENT: return "客户端错误";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "没有麦克风权限";
            case SpeechRecognizer.ERROR_NETWORK: return "网络错误，系统引擎可能需要联网";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "网络超时";
            case SpeechRecognizer.ERROR_NO_MATCH: return "没有匹配到语音";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "识别器忙，正在重启";
            case SpeechRecognizer.ERROR_SERVER: return "系统识别服务错误";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "没有检测到说话";
            default: return "错误码=" + error;
        }
    }
}
