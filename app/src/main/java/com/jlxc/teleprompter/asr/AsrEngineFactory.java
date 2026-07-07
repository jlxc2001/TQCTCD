package com.jlxc.teleprompter.asr;

import android.content.Context;

public final class AsrEngineFactory {
    private AsrEngineFactory() {}

    public static AsrEngine create(Context context) {
        // 大模型稳定版：只使用 sherpa-onnx 本地模型。
        // 不再自动回退到 Android 系统 SpeechRecognizer，避免国产机/无 GMS 环境下出现“看起来开启了，其实没有本地模型”的误判。
        return new XlargeSherpaOnnxAsrEngine(context);
    }
}
