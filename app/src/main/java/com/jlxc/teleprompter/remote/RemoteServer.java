package com.jlxc.teleprompter.remote;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.jlxc.teleprompter.data.Script;
import com.jlxc.teleprompter.data.ScriptStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RemoteServer {
    private static final int MAX_UPLOAD_BYTES = 1024 * 1024 * 2; // 2MB text script limit.

    private final Context appContext;
    private final ScriptStore scriptStore;
    private final int port;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean running;
    private volatile boolean httpReady;
    private volatile boolean udpReady;
    private volatile String lastError = "";
    private ServerSocket serverSocket;
    private DatagramSocket udpSocket;
    private RemoteCommandListener listener;

    public RemoteServer(Context context, int port, RemoteCommandListener listener) {
        this.appContext = context.getApplicationContext();
        this.scriptStore = new ScriptStore(this.appContext);
        this.port = port;
        this.listener = listener;
    }

    /** Backward-compatible constructor for old call sites. */
    public RemoteServer(int port, RemoteCommandListener listener) {
        this.appContext = null;
        this.scriptStore = null;
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
                s.setSoTimeout(8000);
                HttpRequest req = readRequest(s.getInputStream());
                if (req == null) return;
                HttpResult result = route(req.method, req.path, req.headers, req.body);
                writeResponse(s, result);
            } catch (Exception e) {
                try {
                    HttpResult result = new HttpResult(400, "Bad Request", "{\"ok\":false,\"error\":\"" + jsonEscape(e.getMessage()) + "\"}");
                    writeResponse(socket, result);
                } catch (Exception ignored) {}
            }
        }, "teleprompter-http-client").start();
    }

    private HttpRequest readRequest(InputStream in) throws Exception {
        ByteArrayOutputStream headerBytes = new ByteArrayOutputStream();
        int prev3 = -1, prev2 = -1, prev1 = -1;
        while (true) {
            int b = in.read();
            if (b < 0) return null;
            headerBytes.write(b);
            if (headerBytes.size() > 32768) throw new IllegalArgumentException("request header too large");
            if ((prev3 == 13 && prev2 == 10 && prev1 == 13 && b == 10) ||
                    (prev1 == 10 && b == 10)) {
                break;
            }
            prev3 = prev2;
            prev2 = prev1;
            prev1 = b;
        }

        String headerText = headerBytes.toString("UTF-8");
        String[] lines = headerText.split("\r?\n");
        if (lines.length == 0 || lines[0].trim().isEmpty()) return null;
        String[] requestParts = lines[0].split(" ");
        String method = requestParts.length >= 1 ? requestParts[0].trim().toUpperCase(Locale.US) : "GET";
        String path = requestParts.length >= 2 ? requestParts[1] : "/";

        Map<String, String> headers = new HashMap<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            int colon = line.indexOf(':');
            if (colon > 0) headers.put(line.substring(0, colon).trim().toLowerCase(Locale.US), line.substring(colon + 1).trim());
        }

        int len = 0;
        try { len = Integer.parseInt(headers.getOrDefault("content-length", "0")); } catch (Exception ignored) {}
        if (len < 0) len = 0;
        if (len > MAX_UPLOAD_BYTES) throw new IllegalArgumentException("body too large");
        byte[] bodyBytes = new byte[len];
        int off = 0;
        while (off < len) {
            int n = in.read(bodyBytes, off, len - off);
            if (n < 0) break;
            off += n;
        }
        String body = new String(bodyBytes, 0, off, StandardCharsets.UTF_8);
        return new HttpRequest(method, path, headers, body);
    }

    private void writeResponse(Socket s, HttpResult result) throws Exception {
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
    }

    private HttpResult route(String method, String path, Map<String, String> headers, String body) {
        if ("OPTIONS".equals(method)) return HttpResult.ok("{\"ok\":true}");

        String endpoint = path;
        String query = "";
        int q = path.indexOf('?');
        if (q >= 0) { endpoint = path.substring(0, q); query = path.substring(q + 1); }
        Map<String, String> params = parseQuery(query);

        if ("/".equals(endpoint) || "/api/ping".equals(endpoint)) {
            return HttpResult.ok("{\"ok\":true,\"app\":\"JLXC Teleprompter\",\"remote\":true,\"version\":3,\"http\":" + httpReady + ",\"udp\":" + udpReady + ",\"scriptUpload\":true}");
        }
        if ("/api/remote/status".equals(endpoint)) {
            return HttpResult.ok("{\"ok\":true,\"app\":\"JLXC Teleprompter\",\"port\":" + port + ",\"http\":" + httpReady + ",\"udp\":" + udpReady + ",\"scriptUpload\":true}");
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
        if ("/api/remote/scripts".equals(endpoint)) {
            if ("GET".equals(method)) return listScripts();
            if ("POST".equals(method)) return createScript(params, headers, body);
            return new HttpResult(405, "Method Not Allowed", "{\"ok\":false,\"error\":\"method not allowed\"}");
        }
        if ("/api/remote/scripts/add".equals(endpoint) || "/api/remote/script/add".equals(endpoint)) {
            if (!"POST".equals(method)) return new HttpResult(405, "Method Not Allowed", "{\"ok\":false,\"error\":\"method not allowed\"}");
            return createScript(params, headers, body);
        }
        return new HttpResult(404, "Not Found", "{\"ok\":false,\"error\":\"unknown endpoint\"}");
    }

    private HttpResult listScripts() {
        if (scriptStore == null) return new HttpResult(500, "Server Error", "{\"ok\":false,\"error\":\"script store unavailable\"}");
        try {
            List<Script> scripts = scriptStore.all();
            JSONArray arr = new JSONArray();
            for (Script s : scripts) {
                JSONObject o = new JSONObject();
                o.put("id", s.id);
                o.put("title", s.title);
                o.put("length", s.content == null ? 0 : s.content.length());
                o.put("createdAt", s.createdAt);
                o.put("updatedAt", s.updatedAt);
                arr.put(o);
            }
            JSONObject out = new JSONObject();
            out.put("ok", true);
            out.put("count", scripts.size());
            out.put("scripts", arr);
            return HttpResult.ok(out.toString());
        } catch (Exception e) {
            return new HttpResult(500, "Server Error", "{\"ok\":false,\"error\":\"" + jsonEscape(e.getMessage()) + "\"}");
        }
    }

    private HttpResult createScript(Map<String, String> queryParams, Map<String, String> headers, String body) {
        if (scriptStore == null) return new HttpResult(500, "Server Error", "{\"ok\":false,\"error\":\"script store unavailable\"}");
        try {
            Map<String, String> fields = new HashMap<>(queryParams);
            String contentType = headers.getOrDefault("content-type", "").toLowerCase(Locale.US);
            if (contentType.contains("application/json")) {
                JSONObject o = new JSONObject(body == null ? "{}" : body);
                if (o.has("title")) fields.put("title", o.optString("title", ""));
                if (o.has("content")) fields.put("content", o.optString("content", ""));
                if (o.has("text")) fields.put("content", o.optString("text", ""));
            } else if (contentType.contains("application/x-www-form-urlencoded")) {
                fields.putAll(parseQuery(body));
            } else {
                // text/plain: title can be in query string; body is the script content.
                if (body != null && !body.isEmpty() && !fields.containsKey("content")) fields.put("content", body);
            }

            String title = firstNonEmpty(fields.get("title"), fields.get("name"), fields.get("filename"));
            String content = firstNonEmpty(fields.get("content"), fields.get("text"), "");
            if (content == null || content.trim().isEmpty()) {
                return new HttpResult(400, "Bad Request", "{\"ok\":false,\"error\":\"content is empty\"}");
            }
            if (content.getBytes(StandardCharsets.UTF_8).length > MAX_UPLOAD_BYTES) {
                return new HttpResult(413, "Payload Too Large", "{\"ok\":false,\"error\":\"script too large\"}");
            }
            Script created = scriptStore.create(title, content);
            JSONObject out = new JSONObject();
            out.put("ok", true);
            out.put("id", created.id);
            out.put("title", created.title);
            out.put("length", created.content == null ? 0 : created.content.length());
            out.put("createdAt", created.createdAt);
            out.put("updatedAt", created.updatedAt);
            return HttpResult.ok(out.toString());
        } catch (Exception e) {
            return new HttpResult(400, "Bad Request", "{\"ok\":false,\"error\":\"" + jsonEscape(e.getMessage()) + "\"}");
        }
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
                if (eq >= 0) {
                    m.put(URLDecoder.decode(kv.substring(0, eq), "UTF-8"), URLDecoder.decode(kv.substring(eq + 1), "UTF-8"));
                } else if (!kv.isEmpty()) {
                    m.put(URLDecoder.decode(kv, "UTF-8"), "");
                }
            } catch (Exception ignored) {}
        }
        return m;
    }

    private String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String v : values) if (v != null && !v.trim().isEmpty()) return v.trim();
        return "";
    }

    private static String jsonEscape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }


    private static class HttpRequest {
        final String method;
        final String path;
        final Map<String, String> headers;
        final String body;
        HttpRequest(String method, String path, Map<String, String> headers, String body) {
            this.method = method;
            this.path = path;
            this.headers = headers;
            this.body = body == null ? "" : body;
        }
    }

    private static class HttpResult {
        final int code;
        final String message;
        final String body;
        HttpResult(int code, String message, String body) { this.code = code; this.message = message; this.body = body; }
        static HttpResult ok(String body) { return new HttpResult(200, "OK", body); }
    }
}
