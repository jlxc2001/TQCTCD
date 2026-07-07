package com.jlxc.teleprompter.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ScriptStore {
    private static final String SP = "scripts_store";
    private static final String KEY = "scripts_json";
    private final SharedPreferences sp;

    public ScriptStore(Context c) { sp = c.getSharedPreferences(SP, Context.MODE_PRIVATE); }

    public List<Script> all() {
        List<Script> list = new ArrayList<>();
        String raw = sp.getString(KEY, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new Script(
                        o.optString("id"),
                        o.optString("title"),
                        o.optString("content"),
                        o.optLong("createdAt"),
                        o.optLong("updatedAt")));
            }
        } catch (Exception ignored) {}
        return list;
    }

    public Script get(String id) {
        for (Script s : all()) if (s.id.equals(id)) return s;
        return null;
    }

    public Script create(String title, String content) {
        long now = System.currentTimeMillis();
        Script s = new Script(UUID.randomUUID().toString(), cleanTitle(title, content), content, now, now);
        List<Script> list = all();
        list.add(0, s);
        saveAll(list);
        return s;
    }

    public void update(String id, String title, String content) {
        List<Script> list = all();
        long now = System.currentTimeMillis();
        for (Script s : list) {
            if (s.id.equals(id)) {
                s.title = cleanTitle(title, content);
                s.content = content;
                s.updatedAt = now;
            }
        }
        saveAll(list);
    }

    public void delete(String id) {
        List<Script> list = all();
        for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).id.equals(id)) list.remove(i);
        saveAll(list);
    }

    private String cleanTitle(String title, String content) {
        if (title != null && !title.trim().isEmpty()) return title.trim();
        if (content != null && !content.trim().isEmpty()) {
            String t = content.trim().replace('\n', ' ');
            return t.length() > 20 ? t.substring(0, 20) + "…" : t;
        }
        return "未命名文稿";
    }

    private void saveAll(List<Script> list) {
        JSONArray arr = new JSONArray();
        try {
            for (Script s : list) {
                JSONObject o = new JSONObject();
                o.put("id", s.id);
                o.put("title", s.title);
                o.put("content", s.content);
                o.put("createdAt", s.createdAt);
                o.put("updatedAt", s.updatedAt);
                arr.put(o);
            }
        } catch (Exception ignored) {}
        sp.edit().putString(KEY, arr.toString()).apply();
    }
}
