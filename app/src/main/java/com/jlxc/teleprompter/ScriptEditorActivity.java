package com.jlxc.teleprompter;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.jlxc.teleprompter.data.Script;
import com.jlxc.teleprompter.data.ScriptStore;
import com.jlxc.teleprompter.ui.UI;

public class ScriptEditorActivity extends Activity {
    private ScriptStore store;
    private String scriptId;
    private EditText titleEdit;
    private EditText contentEdit;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        UI.hideStatusBar(this);
        store = new ScriptStore(this);
        scriptId = getIntent().getStringExtra("scriptId");
        LinearLayout root = UI.vertical(this);
        UI.backBar(this, root, scriptId == null ? "新建提词文本" : "编辑提词文本");

        titleEdit = new EditText(this);
        titleEdit.setHint("标题，可不填");
        titleEdit.setSingleLine(true);
        titleEdit.setTextColor(android.graphics.Color.WHITE);
        titleEdit.setHintTextColor(android.graphics.Color.GRAY);
        root.addView(titleEdit, new LinearLayout.LayoutParams(-1, -2));

        contentEdit = new EditText(this);
        contentEdit.setHint("输入提词文本");
        contentEdit.setMinLines(14);
        contentEdit.setGravity(android.view.Gravity.TOP);
        contentEdit.setTextColor(android.graphics.Color.WHITE);
        contentEdit.setHintTextColor(android.graphics.Color.GRAY);
        root.addView(contentEdit, new LinearLayout.LayoutParams(-1, 0, 1));

        Button save = UI.button(this, "保存");
        root.addView(save);
        save.setOnClickListener(v -> save());

        if (scriptId != null) {
            Script s = store.get(scriptId);
            if (s != null) {
                titleEdit.setText(s.title);
                contentEdit.setText(s.content);
            }
        }
        setContentView(root);
    }

    private void save() {
        String title = titleEdit.getText().toString();
        String content = contentEdit.getText().toString();
        if (content.trim().isEmpty()) {
            Toast.makeText(this, "内容不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        if (scriptId == null) store.create(title, content);
        else store.update(scriptId, title, content);
        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) UI.hideStatusBar(this);
    }

}
