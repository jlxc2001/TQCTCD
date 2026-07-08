package com.jlxc.teleprompter.remote;

import android.content.Context;

import com.jlxc.teleprompter.settings.AppSettings;

public final class RemoteServerHub {
    private static RemoteServer server;
    private static int port = -1;
    private static RemoteCommandListener activeListener;
    private static String activeScriptId = "";
    private static String activeScriptTitle = "";

    private RemoteServerHub() {}

    public static synchronized void ensureStarted(Context context) {
        AppSettings settings = new AppSettings(context.getApplicationContext());
        if (!settings.remoteEnabled()) {
            stop();
            return;
        }
        int wantedPort = settings.remotePort();
        if (server != null && port == wantedPort && server.isRunning()) return;
        stop();
        port = wantedPort;
        server = new RemoteServer(context.getApplicationContext(), port, new RemoteCommandListener() {
            @Override public void onRemoteScroll(float dy) {
                RemoteCommandListener l = activeListener;
                if (l != null) l.onRemoteScroll(dy);
            }
            @Override public void onRemotePause(boolean paused) {
                RemoteCommandListener l = activeListener;
                if (l != null) l.onRemotePause(paused);
            }
            @Override public void onRemoteTop() {
                RemoteCommandListener l = activeListener;
                if (l != null) l.onRemoteTop();
            }
            @Override public void onRemoteStopPrompt() {
                RemoteCommandListener l = activeListener;
                if (l != null) l.onRemoteStopPrompt();
            }
        });
        server.start();
    }

    public static synchronized void setActiveListener(RemoteCommandListener listener) {
        activeListener = listener;
    }

    public static synchronized void clearActiveListener(RemoteCommandListener listener) {
        if (activeListener == listener) activeListener = null;
    }

    public static synchronized void setActivePrompt(String scriptId, String title) {
        activeScriptId = scriptId == null ? "" : scriptId;
        activeScriptTitle = title == null ? "" : title;
    }

    public static synchronized void clearActivePrompt(String scriptId) {
        if (scriptId == null || scriptId.equals(activeScriptId)) {
            activeScriptId = "";
            activeScriptTitle = "";
        }
    }

    public static synchronized String activeScriptId() { return activeScriptId == null ? "" : activeScriptId; }
    public static synchronized String activeScriptTitle() { return activeScriptTitle == null ? "" : activeScriptTitle; }
    public static synchronized boolean hasActivePrompt() { return activeScriptId != null && !activeScriptId.isEmpty(); }

    public static synchronized void restart(Context context) {
        stop();
        ensureStarted(context);
    }

    public static synchronized void stop() {
        if (server != null) server.stop();
        server = null;
        port = -1;
    }

    public static synchronized String statusText() {
        if (server == null) return "遥控服务：未启动";
        String err = server.lastError();
        if (err != null && !err.isEmpty()) return "遥控服务：异常 · " + err;
        return "遥控服务：HTTP " + (server.isHttpReady() ? "已启动" : "启动中") + " / UDP " + (server.isUdpReady() ? "已启动" : "启动中") + " / 端口 " + server.port();
    }
}
