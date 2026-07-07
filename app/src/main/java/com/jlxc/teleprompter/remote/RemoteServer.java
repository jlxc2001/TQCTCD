package com.jlxc.teleprompter.remote;

import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class RemoteServer {
    private final int port;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean running;
    private ServerSocket serverSocket;
    private DatagramSocket udpSocket;
    private RemoteCommandListener listener;

    public RemoteServer(int port, RemoteCommandListener listener) {
        this.port = port;
        this.listener = listener;
    }

    public void start() {
        if (running) return;
        running = true;
        startHttp();
        startUdp();
    }

    public void stop() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
        try { if (udpSocket != null) udpSocket.close(); } catch (Exception ignored) {}
    }

    private void startHttp() {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                while (running) {
                    Socket socket = serverSocket.accept();
                    handleSocket(socket);
                }
            } catch (Exception ignored) {}
        }, "teleprompter-http").start();
    }

    private void handleSocket(Socket socket) {
        new Thread(() -> {
            try (Socket s = socket) {
                BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
                String requestLine = br.readLine();
                if (requestLine == null) return;
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.isEmpty()) break;
                }
                String[] parts = requestLine.split(" ");
                String path = parts.length >= 2 ? parts[1] : "/";
                String body = route(path);
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                OutputStream os = s.getOutputStream();
                os.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: " + bytes.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                os.write(bytes);
                os.flush();
            } catch (Exception ignored) {}
        }, "teleprompter-http-client").start();
    }

    private String route(String path) {
        String endpoint = path;
        String query = "";
        int q = path.indexOf('?');
        if (q >= 0) { endpoint = path.substring(0, q); query = path.substring(q + 1); }
        Map<String, String> params = parseQuery(query);

        if ("/api/ping".equals(endpoint)) {
            return "{\"ok\":true,\"app\":\"JLXC Teleprompter\",\"remote\":true,\"version\":1}";
        }
        if ("/api/remote/scroll".equals(endpoint)) {
            float dy = parseFloat(params.get("dy"), 0f);
            dispatchScroll(dy);
            return "{\"ok\":true}";
        }
        if ("/api/remote/pause".equals(endpoint)) {
            boolean paused = Boolean.parseBoolean(params.get("paused"));
            dispatchPause(paused);
            return "{\"ok\":true}";
        }
        if ("/api/remote/top".equals(endpoint)) {
            dispatchTop();
            return "{\"ok\":true}";
        }
        return "{\"ok\":false,\"error\":\"unknown endpoint\"}";
    }

    private void startUdp() {
        new Thread(() -> {
            try {
                udpSocket = new DatagramSocket(port);
                byte[] buf = new byte[256];
                while (running) {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    udpSocket.receive(p);
                    String cmd = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8).trim();
                    handleUdp(cmd);
                }
            } catch (Exception ignored) {}
        }, "teleprompter-udp").start();
    }

    private void handleUdp(String cmd) {
        if (cmd.startsWith("SCROLL")) {
            String[] p = cmd.split("\\s+");
            if (p.length >= 2) dispatchScroll(parseFloat(p[1], 0f));
        } else if (cmd.startsWith("PAUSE")) {
            String[] p = cmd.split("\\s+");
            if (p.length >= 2) dispatchPause(Boolean.parseBoolean(p[1]));
        } else if ("TOP".equals(cmd)) {
            dispatchTop();
        }
    }

    private void dispatchScroll(float dy) { main.post(() -> { if (listener != null) listener.onRemoteScroll(dy); }); }
    private void dispatchPause(boolean paused) { main.post(() -> { if (listener != null) listener.onRemotePause(paused); }); }
    private void dispatchTop() { main.post(() -> { if (listener != null) listener.onRemoteTop(); }); }

    private float parseFloat(String s, float def) { try { return Float.parseFloat(s); } catch (Exception e) { return def; } }

    private Map<String, String> parseQuery(String q) {
        Map<String, String> m = new HashMap<>();
        if (q == null || q.isEmpty()) return m;
        for (String kv : q.split("&")) {
            int eq = kv.indexOf('=');
            try {
                if (eq >= 0) m.put(URLDecoder.decode(kv.substring(0, eq), "UTF-8"), URLDecoder.decode(kv.substring(eq + 1), "UTF-8"));
            } catch (Exception ignored) {}
        }
        return m;
    }
}
