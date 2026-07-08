package com.jlxc.teleprompter;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.jlxc.teleprompter.asr.AsrModelInfo;
import com.jlxc.teleprompter.settings.AppSettings;
import com.jlxc.teleprompter.ui.UI;

import java.util.Locale;

public class AppSettingsActivity extends Activity {
    private static final int REQ_MIC_FOR_METER = 2102;

    private AppSettings s;
    private final Handler meterHandler = new Handler(Looper.getMainLooper());
    private volatile boolean meterRunning = false;
    private Thread meterThread;
    private AudioRecord meterRecord;
    private ProgressBar gainMeter;
    private TextView gainMeterText;
    private Button micPermissionButton;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        UI.hideStatusBar(this);
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
            } catch (Exception e) {
                Toast.makeText(this, "颜色格式错误，例如 #000000", Toast.LENGTH_SHORT).show();
            }
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
        orientation.setAdapter(UI.darkSpinnerAdapter(this,
                new String[]{"跟随系统", "竖屏", "横屏", "反向横屏"}));
        orientation.setSelection(s.orientationMode());
        orientation.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) { s.setOrientationMode(position); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        root.addView(orientation);

        root.addView(UI.label(this, "提词模式"));
        Spinner mode = new Spinner(this);
        mode.setAdapter(UI.darkSpinnerAdapter(this,
                new String[]{"模式一：自动滚动字幕", "模式二：语音识别字幕", "模式三：遥控控制字幕"}));
        mode.setSelection(s.mode());
        mode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) { s.setMode(position); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        root.addView(mode);

        root.addView(UI.label(this, "语音识别率设置"));
        root.addView(UI.card(this, "内置 ASR 模型状态", AsrModelInfo.statusText(this)));
        Button thButton = UI.button(this, "识别率设置：当前 " + percent(s.voiceThreshold()));
        root.addView(thButton);
        TextView thText = UI.card(this, "当前匹配阈值：" + percent(s.voiceThreshold()), "低：允许临场改字/口误；高：更严格，误跳少。建议 65%～78%。回读上一段时也使用这个阈值。 ");
        root.addView(thText);
        SeekBar th = new SeekBar(this);
        th.setMax(100);
        th.setProgress((int)(s.voiceThreshold() * 100));
        th.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float v = Math.max(45, Math.min(95, progress)) / 100f;
                s.setVoiceThreshold(v);
                thButton.setText("识别率设置：当前 " + percent(v));
                thText.setText("当前匹配阈值：" + percent(v) + "\n低：允许临场改字/口误；高：更严格，误跳少。建议 65%～78%。回读上一段时也使用这个阈值。 ");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        root.addView(th);
        Button resetVoice = UI.button(this, "重置识别率为 72%");
        resetVoice.setOnClickListener(v -> {
            s.setVoiceThreshold(0.72f);
            th.setProgress(72);
            thButton.setText("识别率设置：当前 72%");
            thText.setText("当前匹配阈值：72%\n低：允许临场改字/口误；高：更严格，误跳少。建议 65%～78%。回读上一段时也使用这个阈值。 ");
            Toast.makeText(this, "识别率已重置", Toast.LENGTH_SHORT).show();
        });
        root.addView(resetVoice);

        root.addView(UI.label(this, "识别音量增益"));
        TextView gainText = UI.card(this,
                "当前增益：" + gainLabel(s.voiceInputGain()),
                "用于放大送入本地 ASR 的麦克风音量。声音偏小时可调到 150%～250%；过高会削波，可能让识别更不稳定。下方音量条显示的是增益后的实时输入音量。 ");
        root.addView(gainText);
        SeekBar gain = new SeekBar(this);
        gain.setMax(475); // 25% ~ 500%
        gain.setProgress(Math.max(0, Math.min(475, (int)(s.voiceInputGain() * 100f) - 25)));
        gain.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int percent = Math.max(25, Math.min(500, progress + 25));
                float v = percent / 100f;
                s.setVoiceInputGain(v);
                gainText.setText("当前增益：" + gainLabel(v) + "\n用于放大送入本地 ASR 的麦克风音量。声音偏小时可调到 150%～250%；过高会削波，可能让识别更不稳定。下方音量条显示的是增益后的实时输入音量。 ");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        root.addView(gain);

        gainMeterText = UI.card(this, "增益后实时音量：等待麦克风", "打开麦克风权限后，对着手机说话，这里会显示增益后的音量条。 ");
        root.addView(gainMeterText);
        gainMeter = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        gainMeter.setMax(100);
        gainMeter.setProgress(0);
        LinearLayout.LayoutParams meterLp = new LinearLayout.LayoutParams(-1, 36);
        meterLp.setMargins(0, 0, 0, 16);
        root.addView(gainMeter, meterLp);
        micPermissionButton = UI.button(this, "授予麦克风权限并显示实时音量");
        micPermissionButton.setOnClickListener(v -> requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC_FOR_METER));
        root.addView(micPermissionButton);

        root.addView(UI.label(this, "语音高亮提前量"));
        TextView leadText = UI.card(this, "当前：提前 " + s.voiceDisplayLeadSentences() + " 句", "ASR 有天然延迟。默认提前 1 句，屏幕高亮会显示你接下来要读的句子；如果你觉得太超前，可以改成 0。 ");
        root.addView(leadText);
        SeekBar lead = new SeekBar(this);
        lead.setMax(2);
        lead.setProgress(s.voiceDisplayLeadSentences());
        lead.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int v = Math.max(0, Math.min(2, progress));
                s.setVoiceDisplayLeadSentences(v);
                leadText.setText("当前：提前 " + v + " 句\nASR 有天然延迟。默认提前 1 句，屏幕高亮会显示你接下来要读的句子；如果你觉得太超前，可以改成 0。 ");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        root.addView(lead);

        setContentView(UI.scrollWrap(this, root));
        startMeterIfPermitted();
    }

    private void startMeterIfPermitted() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            if (micPermissionButton != null) micPermissionButton.setVisibility(View.GONE);
            startVolumeMeter();
        } else {
            if (micPermissionButton != null) micPermissionButton.setVisibility(View.VISIBLE);
            if (gainMeterText != null) gainMeterText.setText("增益后实时音量：未授权麦克风\n点击按钮授予权限后显示实时音量条。 ");
        }
    }

    private void startVolumeMeter() {
        if (meterRunning) return;
        meterRunning = true;
        meterThread = new Thread(() -> {
            final int sampleRate = 16000;
            int minBuffer = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            int bufferSize = Math.max(minBuffer > 0 ? minBuffer : sampleRate / 2, sampleRate / 2);
            AudioRecord record = null;
            try {
                record = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
                meterRecord = record;
                if (record.getState() != AudioRecord.STATE_INITIALIZED) throw new IllegalStateException("AudioRecord 初始化失败");
                record.startRecording();
                short[] buf = new short[bufferSize / 2];
                while (meterRunning) {
                    int n = record.read(buf, 0, buf.length);
                    if (n <= 0) continue;
                    float gain = s.voiceInputGain();
                    double sum = 0;
                    double peak = 0;
                    for (int i = 0; i < n; i++) {
                        double sample = (buf[i] / 32768.0) * gain;
                        if (sample > 1.0) sample = 1.0;
                        else if (sample < -1.0) sample = -1.0;
                        double abs = Math.abs(sample);
                        if (abs > peak) peak = abs;
                        sum += sample * sample;
                    }
                    double rms = Math.sqrt(sum / Math.max(1, n));
                    int level = Math.max(0, Math.min(100, (int)(Math.sqrt(rms) * 115.0)));
                    int peakLevel = Math.max(0, Math.min(100, (int)(peak * 100.0)));
                    meterHandler.post(() -> {
                        if (!meterRunning) return;
                        if (gainMeter != null) gainMeter.setProgress(level);
                        if (gainMeterText != null) {
                            gainMeterText.setText("增益后实时音量：" + level + "% · 峰值 " + peakLevel + "%\n当前增益 " + gainLabel(s.voiceInputGain()) + "。如果经常满格，说明增益过高，建议调低。 ");
                        }
                    });
                }
            } catch (Throwable t) {
                meterHandler.post(() -> {
                    if (gainMeterText != null) gainMeterText.setText("增益后实时音量：启动失败\n" + t.getClass().getSimpleName() + ": " + (t.getMessage() == null ? "未知错误" : t.getMessage()));
                });
            } finally {
                try { if (record != null) record.stop(); } catch (Exception ignored) {}
                try { if (record != null) record.release(); } catch (Exception ignored) {}
                meterRecord = null;
                meterRunning = false;
            }
        }, "JLXC-Volume-Meter");
        meterThread.start();
    }

    private void stopVolumeMeter() {
        meterRunning = false;
        try { if (meterRecord != null) meterRecord.stop(); } catch (Exception ignored) {}
        try { if (meterRecord != null) meterRecord.release(); } catch (Exception ignored) {}
        meterRecord = null;
        try { if (meterThread != null) meterThread.interrupt(); } catch (Exception ignored) {}
        meterThread = null;
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

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MIC_FOR_METER) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) startMeterIfPermitted();
            else Toast.makeText(this, "未授予麦克风权限，无法显示实时音量条", Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onDestroy() {
        stopVolumeMeter();
        super.onDestroy();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) UI.hideStatusBar(this);
    }

    private String colorToHex(int color) { return String.format("#%06X", 0xFFFFFF & color); }
    private String percent(float v) { return ((int)(v * 100)) + "%"; }
    private String gainLabel(float v) { return String.format(Locale.CHINA, "%d%%（%.2fx）", Math.round(v * 100f), v); }
}
