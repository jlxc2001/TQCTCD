package com.jlxc.teleprompter.remote;

public interface RemoteCommandListener {
    void onRemoteScroll(float dy);
    void onRemotePause(boolean paused);
    void onRemoteTop();
    void onRemoteStopPrompt();
}
