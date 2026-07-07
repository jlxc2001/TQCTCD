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
 * 真正上线建议替换成 SherpaOnnxAsrEngine。
 */
public class AndroidSpeechRecognizerEngine implements AsrEngine, RecognitionListener {
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private Listener listener;
    private boolean running;
    private boolean restarting;

    public AndroidSpeechRecognizerEngine(Context context) { this.context = context.getApplicationContext(); }

    @Override public void start(Listener listener) {
        this.listener = listener;
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            if (listener != null) listener.onError("系统语音识别不可用");
            return;
        }
        running = true;
        recognizer = SpeechRecognizer.createSpeechRecognizer(context);
        recognizer.setRecognitionListener(this);
        if (listener != null) listener.onReady(name());
        startListening();
    }

    private void startListening() {
        if (!running || recognizer == null) return;
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.CHINA.toString());
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        try { recognizer.startListening(i); } catch (Exception e) { scheduleRestart(); }
    }

    @Override public void stop() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        if (recognizer != null) {
            try { recognizer.stopListening(); } catch (Exception ignored) {}
            try { recognizer.destroy(); } catch (Exception ignored) {}
        }
        recognizer = null;
    }

    @Override public boolean isAvailable() { return SpeechRecognizer.isRecognitionAvailable(context); }
    @Override public String name() { return "系统语音识别调试引擎"; }

    @Override public void onReadyForSpeech(Bundle params) {}
    @Override public void onBeginningOfSpeech() {}
    @Override public void onRmsChanged(float rmsdB) {}
    @Override public void onBufferReceived(byte[] buffer) {}
    @Override public void onEndOfSpeech() { scheduleRestart(); }
    @Override public void onError(int error) {
        if (listener != null) listener.onError("ASR error=" + error);
        scheduleRestart();
    }
    @Override public void onResults(Bundle results) {
        ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (list != null && !list.isEmpty() && listener != null) listener.onFinalText(list.get(0));
        scheduleRestart();
    }
    @Override public void onPartialResults(Bundle partialResults) {
        ArrayList<String> list = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (list != null && !list.isEmpty() && listener != null) listener.onPartialText(list.get(0));
    }
    @Override public void onEvent(int eventType, Bundle params) {}

    private void scheduleRestart() {
        if (!running || restarting) return;
        restarting = true;
        handler.postDelayed(() -> {
            restarting = false;
            startListening();
        }, 250);
    }
}
