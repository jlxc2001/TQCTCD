package com.jlxc.teleprompter;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.jlxc.teleprompter.settings.AppSettings;
import com.jlxc.teleprompter.ui.UI;

public class AppSettingsActivity extends Activity {
    private AppSettings s;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        s = new AppSettings(this);
        LinearLayout root = UI.vertical(this);
        UI.backBar(this, root, "软件设置");

        EditText bg = colorEdit("背景颜色", colorToHex(s.backgroundColor()), root);
        EditText fg = colorEdit("文字颜色", colorToHex(s.textColor()), root);
        Button applyColor = UI.button(this, "保存颜色");
        root.addView(applyColor);
        applyColor.setOnClickListener(v -> {
            try {
                s.setBackgroundColor(Color.parseColor(bg.getText().toString().trim()));
                s.setTextColor(Color.parseColor(fg.getText().toString().trim()));
                Toast.makeText(this, "颜色已保存", Toast.LENGTH_SHORT).show();
            } catch (Exception e) { Toast.makeText(this, "颜色格式错误，例如 #000000", Toast.LENGTH_SHORT).show(); }
        });

        root.addView(UI.label(this, "文字大小"));
        TextView sizeText = UI.card(this, "当前：" + (int) s.textSizeSp() + "sp", "建议 28～56sp，根据支架距离调整");
        root.addView(sizeText);
        SeekBar size = new SeekBar(this);
        size.setMax(80);
        size.setProgress((int) s.textSizeSp());
        size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int v = Math.max(18, progress);
                s.setTextSizeSp(v);
                sizeText.setText("当前：" + v + "sp\n建议 28～56sp，根据支架距离调整");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        root.addView(size);

        Switch mirror = new Switch(this);
        mirror.setText("开始提词页面镜像显示");
        mirror.setTextColor(Color.WHITE);
        mirror.setTextSize(16);
        mirror.setChecked(s.mirrorPrompt());
        mirror.setOnCheckedChangeListener((buttonView, isChecked) -> s.setMirrorPrompt(isChecked));
        root.addView(mirror);

        root.addView(UI.label(this, "开始提词后的屏幕朝向"));
        Spinner orientation = new Spinner(this);
        orientation.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"跟随系统", "竖屏", "横屏", "反向横屏"}));
        orientation.setSelection(s.orientationMode());
        orientation.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) { s.setOrientationMode(position); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        root.addView(orientation);

        root.addView(UI.label(this, "提词模式"));
        Spinner mode = new Spinner(this);
        mode.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"模式一：自动滚动字幕", "模式二：语音识别字幕", "模式三：遥控控制字幕"}));
        mode.setSelection(s.mode());
        mode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) { s.setMode(position); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        root.addView(mode);

        root.addView(UI.label(this, "语音识别率设置"));
        TextView thText = UI.card(this, "当前阈值：" + percent(s.voiceThreshold()), "低：允许临场改字；高：更严格，误跳少。建议 65%～78%。");
        root.addView(thText);
        SeekBar th = new SeekBar(this);
        th.setMax(100);
        th.setProgress((int)(s.voiceThreshold() * 100));
        th.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float v = Math.max(45, Math.min(95, progress)) / 100f;
                s.setVoiceThreshold(v);
                thText.setText("当前阈值：" + percent(v) + "\n低：允许临场改字；高：更严格，误跳少。建议 65%～78%。");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        root.addView(th);

        setContentView(root);
    }

    private EditText colorEdit(String label, String value, LinearLayout root) {
        root.addView(UI.label(this, label));
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setText(value);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(Color.GRAY);
        root.addView(e, new LinearLayout.LayoutParams(-1, -2));
        return e;
    }

    private String colorToHex(int color) { return String.format("#%06X", 0xFFFFFF & color); }
    private String percent(float v) { return ((int)(v * 100)) + "%"; }
}
