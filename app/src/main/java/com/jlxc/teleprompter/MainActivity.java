package com.jlxc.teleprompter;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.jlxc.teleprompter.ui.UI;

public class MainActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = UI.vertical(this);
        TextView title = UI.title(this, "JLXC 提词器");
        root.addView(title);

        Button start = UI.button(this, "开始提词");
        Button settings = UI.button(this, "软件设置");
        Button remote = UI.button(this, "遥控设置");
        root.addView(start);
        root.addView(settings);
        root.addView(remote);

        start.setOnClickListener(v -> startActivity(new Intent(this, ScriptListActivity.class)));
        settings.setOnClickListener(v -> startActivity(new Intent(this, AppSettingsActivity.class)));
        remote.setOnClickListener(v -> startActivity(new Intent(this, RemoteSettingsActivity.class)));
        setContentView(root);
    }
}
