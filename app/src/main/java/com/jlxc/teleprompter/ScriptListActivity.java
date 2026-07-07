package com.jlxc.teleprompter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.jlxc.teleprompter.data.Script;
import com.jlxc.teleprompter.data.ScriptStore;
import com.jlxc.teleprompter.remote.RemoteServerHub;
import com.jlxc.teleprompter.ui.UI;
import com.jlxc.teleprompter.util.TextUtil;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ScriptListActivity extends Activity {
    private ScriptStore store;
    private LinearLayout list;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        RemoteServerHub.ensureStarted(this);
        store = new ScriptStore(this);
        LinearLayout root = UI.vertical(this);
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        TextView title = UI.title(this, "‹  开始提词");
        title.setOnClickListener(v -> finish());
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView plus = new TextView(this);
        plus.setText("＋");
        plus.setTextColor(UI.ACCENT);
        plus.setTextSize(34);
        plus.setGravity(Gravity.CENTER);
        plus.setOnClickListener(v -> startActivity(new Intent(this, ScriptEditorActivity.class)));
        top.addView(plus, new LinearLayout.LayoutParams(TextUtil.dp(this, 56), TextUtil.dp(this, 56)));
        root.addView(top);

        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    @Override protected void onResume() { super.onResume(); render(); }

    private void render() {
        list.removeAllViews();
        List<Script> scripts = store.all();
        if (scripts.isEmpty()) {
            TextView empty = UI.card(this, "还没有文稿", "点击右上角 + 新建提词文本");
            list.addView(empty);
            return;
        }
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
        for (Script s : scripts) {
            String sub = fmt.format(new Date(s.updatedAt)) + " · " + s.content.length() + " 字";
            TextView card = UI.card(this, s.title, sub);
            card.setOnClickListener(v -> {
                Intent i = new Intent(this, PromptActivity.class);
                i.putExtra("scriptId", s.id);
                startActivity(i);
            });
            card.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle(s.title)
                        .setItems(new String[]{"编辑文本内容", "删除"}, (d, which) -> {
                            if (which == 0) {
                                Intent i = new Intent(this, ScriptEditorActivity.class);
                                i.putExtra("scriptId", s.id);
                                startActivity(i);
                            } else {
                                new AlertDialog.Builder(this)
                                        .setTitle("删除文稿？")
                                        .setMessage("删除后不可恢复。")
                                        .setPositiveButton("删除", (dd, w) -> { store.delete(s.id); render(); })
                                        .setNegativeButton("取消", null).show();
                            }
                        }).show();
                return true;
            });
            list.addView(card);
        }
    }
}
