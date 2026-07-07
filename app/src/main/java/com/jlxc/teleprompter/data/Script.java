package com.jlxc.teleprompter.data;

public class Script {
    public String id;
    public String title;
    public String content;
    public long createdAt;
    public long updatedAt;

    public Script(String id, String title, String content, long createdAt, long updatedAt) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
