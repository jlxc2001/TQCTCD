package com.jlxc.teleprompter.remote;

import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
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
    private volatile boolean httpReady;
    private volatile boolean udpReady;
    private volatile String lastError = "";
    private ServerSocket serverSocket;
    private DatagramSocket udpSocket;
    private RemoteCommandListener listener;

    public RemoteServer(int port, RemoteCommandListener listener) {
        this.port = port;
        this.listener = listener;
    }

    public void setListener(RemoteCommandListener listener) {
        this.listener = listener;
    }

    public void start() {
        if (running) return;
        running = true;
        httpReady = false;
        udpReady = false;
        lastError = "";
        startHttp();
        startUdp();
    }

    public void stop() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
        try { if (udpSocket != null) udpSocket.close(); } catch (Exception ignored) {}
        serverSocket = null;
        udpSocket = null;
        httpReady = false;
        udpReady = false;
    }

    public boolean isRunning() { return running; }
    public boolean isHttpReady() { return httpReady; }
    public boolean isUdpReady() { return udpReady; }
    public int port() { return port; }
    public String lastError() { return lastError == null ? "" : lastError; }

    private void startHttp() {
        new Thread(() -> {
            try {
                ServerSocket ss = new ServerSocket();
                ss.setReuseAddress(true);
                ss.bind(new InetSocketAddress(InetAddress.getByName("0.0.0.0"), port));
                serverSocket = ss;
                httpReady = true;
                while (running) {
                    Socket socket = serverSocket.accept();
                    handleSocket(socket);
                }
            } catch (Exception e) {
                lastError = "HTTP 服务启动失败：" + e.getClass().getSimpleName() + ": " + e.getMessage();
                httpReady = false;
            }
        }, "teleprompter-http").start();
    }

    private void handleSocket(Socket socket) {
        new Thread(() -> {
            try (Socket s = socket) {
                s.setSoTimeout(2500);
                BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                String requestLine = br.readLine();
                if (requestLine == null) return;
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.isEmpty()) break;
                }
                String[] parts = requestLine.split(" ");
                String path = parts.length >= 2 ? parts[1] : "/";
                HttpResult result = route(path);
                byte[] bytes = result.body.getBytes(StandardCharsets.UTF_8);
                OutputStream os = s.getOutputStream();
                os.write(("HTTP/1.1 " + result.code + " " + result.message + "\r\n" +
                        "Content-Type: application/json; charset=utf-8\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                        "Access-Control-Allow-Headers: *\r\n" +
                        "Content-Length: " + bytes.length + "\r\n" +
                        "Connection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                os.write(bytes);
                os.flush();
            } catch (Exception ignored) {}
        }, "teleprompter-http-client").start();
    }

    private HttpResult route(String path) {
        String endpoint = path;
        String query = "";
        int q = path.indexOf('?');
        if (q >= 0) { endpoint = path.substring(0, q); query = path.substring(q + 1); }
        Map<String, String> params = parseQuery(query);

        if ("/".equals(endpoint) || "/api/ping".equals(endpoint)) {
            return HttpResult.ok("{\"ok\":true,\"app\":\"JLXC Teleprompter\",\"remote\":true,\"version\":2,\"http\":" + httpReady + ",\"udp\":" + udpReady + "}");
        }
        if ("/api/remote/status".equals(endpoint)) {
            return HttpResult.ok("{\"ok\":true,\"app\":\"JLXC Teleprompter\",\"port\":" + port + ",\"http\":" + httpReady + ",\"udp\":" + udpReady + "}");
        }
        if ("/api/remote/scroll".equals(endpoint)) {
            float dy = parseFloat(params.get("dy"), 0f);
            dispatchScroll(dy);
            return HttpResult.ok("{\"ok\":true}");
        }
        if ("/api/remote/pause".equals(endpoint)) {
            boolean paused = Boolean.parseBoolean(params.get("paused"));
            dispatchPause(paused);
            return HttpResult.ok("{\"ok\":true}");
        }
        if ("/api/remote/top".equals(endpoint)) {
            dispatchTop();
            return HttpResult.ok("{\"ok\":true}");
        }
        return new HttpResult(404, "Not Found", "{\"ok\":false,\"error\":\"unknown endpoint\"}");
    }

    private void startUdp() {
        new Thread(() -> {
            try {
                DatagramSocket ds = new DatagramSocket(null);
                ds.setReuseAddress(true);
                ds.bind(new InetSocketAddress(InetAddress.getByName("0.0.0.0"), port));
                udpSocket = ds;
                udpReady = true;
                byte[] buf = new byte[256];
                while (running) {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    udpSocket.receive(p);
                    String cmd = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8).trim();
                    handleUdp(cmd);
                }
            } catch (Exception e) {
                lastError = "UDP 服务启动失败：" + e.getClass().getSimpleName() + ": " + e.getMessage();
                udpReady = false;
            }
        }, "teleprompter-udp").start();
    }

    private void handleUdp(String cmd) {
        if (cmd == null || cmd.isEmpty()) return;
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

    private void dispatchScroll(float dy) { main.post(() -> { RemoteCommandListener l = listener; if (l != null) l.onRemoteScroll(dy); }); }
    private void dispatchPause(boolean paused) { main.post(() -> { RemoteCommandListener l = listener; if (l != null) l.onRemotePause(paused); }); }
    private void dispatchTop() { main.post(() -> { RemoteCommandListener l = listener; if (l != null) l.onRemoteTop(); }); }

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

    private static class HttpResult {
        final int code;
        final String message;
        final String body;
        HttpResult(int code, String message, String body) { this.code = code; this.message = message; this.body = body; }
        static HttpResult ok(String body) { return new HttpResult(200, "OK", body); }
    }
}
