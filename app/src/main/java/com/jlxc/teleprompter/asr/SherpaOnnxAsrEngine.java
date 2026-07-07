package com.jlxc.teleprompter.asr;

import android.content.Context;

/**
 * sherpa-onnx 本地模型接入占位层。
 *
 * 这个工程包不直接内置模型和 .so，因为中文流式模型可能几百 MB。
 * 接入方式：
 * 1. 按 sherpa-onnx Android 文档放入 libsherpa-onnx-jni.so / libonnxruntime.so。
 * 2. 把中文流式模型放入 app/src/main/assets/asr/。
 * 3. 用官方 SherpaOnnx demo 中的识别器实现替换本类 start/stop。
 *
 * 外部 UI、跟读匹配、回读上一段逻辑都已经和 AsrEngine 解耦，替换这里即可。
 */
public class SherpaOnnxAsrEngine implements AsrEngine {
    private final Context context;
    public SherpaOnnxAsrEngine(Context context) { this.context = context.getApplicationContext(); }

    @Override public void start(Listener listener) {
        if (listener != null) listener.onError("未检测到内置 sherpa-onnx 模型；当前工程已预留本地 ASR 接口。请放入模型后替换 SherpaOnnxAsrEngine。 ");
    }
    @Override public void stop() {}
    @Override public boolean isAvailable() { return false; }
    @Override public String name() { return "sherpa-onnx 本地 ASR"; }
}
