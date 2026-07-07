package com.jlxc.teleprompter.asr;

import android.content.Context;

public final class AsrEngineFactory {
    private AsrEngineFactory() {}

    public static AsrEngine create(Context context) {
        SherpaOnnxAsrEngine sherpa = new SherpaOnnxAsrEngine(context);
        if (sherpa.isAvailable()) return sherpa;
        return new AndroidSpeechRecognizerEngine(context);
    }
}
