package com.jlxc.teleprompter.asr;

public interface AsrEngine {
    interface Listener {
        void onPartialText(String text);
        void onFinalText(String text);
        void onError(String message);
        void onReady(String engineName);
    }

    void start(Listener listener);
    void stop();
    boolean isAvailable();
    String name();
}
