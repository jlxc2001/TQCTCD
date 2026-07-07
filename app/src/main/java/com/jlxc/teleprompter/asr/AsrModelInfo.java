package com.jlxc.teleprompter.asr;

import android.content.Context;

import java.io.InputStream;

public final class AsrModelInfo {
    public static final String MODEL_NAME = "sherpa-onnx-streaming-zipformer-zh-xlarge-int8-2025-06-30";
    public static final String MODEL_LABEL = "中文 xlarge 流式大模型 int8 / 2025-06-30";
    public static final String MODEL_DIR = "asr/" + MODEL_NAME;
    public static final String ENCODER = MODEL_DIR + "/encoder.int8.onnx";
    public static final String DECODER = MODEL_DIR + "/decoder.onnx";
    public static final String JOINER = MODEL_DIR + "/joiner.int8.onnx";
    public static final String TOKENS = MODEL_DIR + "/tokens.txt";
    public static final String BPE = MODEL_DIR + "/bpe.model";

    private AsrModelInfo() {}

    public static boolean hasBundledModel(Context context) {
        return assetExists(context, ENCODER) && assetExists(context, DECODER)
                && assetExists(context, JOINER) && assetExists(context, TOKENS);
    }

    public static String statusText(Context context) {
        if (hasBundledModel(context)) {
            return "已检测到 sherpa-onnx " + MODEL_LABEL + "。本版本不再使用 zh-14M 小模型，也不会回退到系统语音识别。";
        }
        return "未检测到内置 xlarge 大模型。请用本工程的 GitHub Actions 构建，或先运行 scripts/download_sherpa_asr.sh 下载 AAR 与 xlarge 中文模型；否则语音识别模式会明确报错，不会假装识别。";
    }

    public static String shortName() {
        return MODEL_NAME;
    }

    private static boolean assetExists(Context context, String name) {
        try (InputStream ignored = context.getAssets().open(name)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
