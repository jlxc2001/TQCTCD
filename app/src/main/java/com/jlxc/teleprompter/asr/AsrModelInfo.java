package com.jlxc.teleprompter.asr;

import android.content.Context;

import java.io.InputStream;

public final class AsrModelInfo {
    public static final String MODEL_DIR = "asr/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23";
    public static final String ENCODER = MODEL_DIR + "/encoder-epoch-99-avg-1.int8.onnx";
    public static final String DECODER = MODEL_DIR + "/decoder-epoch-99-avg-1.onnx";
    public static final String JOINER = MODEL_DIR + "/joiner-epoch-99-avg-1.int8.onnx";
    public static final String TOKENS = MODEL_DIR + "/tokens.txt";

    private AsrModelInfo() {}

    public static boolean hasBundledModel(Context context) {
        return assetExists(context, ENCODER) && assetExists(context, DECODER)
                && assetExists(context, JOINER) && assetExists(context, TOKENS);
    }

    public static String statusText(Context context) {
        if (hasBundledModel(context)) {
            return "已检测到 sherpa-onnx 中文离线模型：zh-14M，小体积实时模型。最终 GitHub Actions 打出来的 APK 会明显大于 48KB。";
        }
        return "未检测到内置模型。请用本工程的 GitHub Actions 构建，或先运行 scripts/download_sherpa_asr.sh 下载 AAR 与中文模型；否则会回退到系统语音识别，很多国产机不可用。";
    }

    private static boolean assetExists(Context context, String name) {
        try (InputStream ignored = context.getAssets().open(name)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
