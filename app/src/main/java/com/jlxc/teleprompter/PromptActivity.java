package com.jlxc.teleprompter;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.jlxc.teleprompter.align.ScriptAligner;
import com.jlxc.teleprompter.asr.AsrEngine;
import com.jlxc.teleprompter.asr.AsrEngineFactory;
import com.jlxc.teleprompter.data.Script;
import com.jlxc.teleprompter.data.ScriptStore;
import com.jlxc.teleprompter.remote.RemoteCommandListener;
import com.jlxc.teleprompter.remote.RemoteServer;
import com.jlxc.teleprompter.settings.AppSettings;
import com.jlxc.teleprompter.util.TextUtil;

public class PromptActivity extends Activity implements RemoteCommandListener {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private AppSettings settings;
    private Script script;
    private ScrollView scrollView;
    private TextView contentView;
    private TextView statusView;
    private LinearLayout speedPanel;
    private long lastAutoTick;
    private boolean paused;
    private RemoteServer remoteServer;
    private AsrEngine asrEngine;
    private ScriptAligner aligner;

    private final Runnable autoRunnable = new Runnable() {
        @Override public void run() {
            if (!paused && settings.mode() == AppSettings.MODE_AUTO) {
                long now = System.currentTimeMillis();
                if (lastAutoTick == 0) lastAutoTick = now;
                float dt = Math.max(0, now - lastAutoTick) / 1000f;
                int dy = (int) (settings.autoSpeedPxPerSec() * dt);
                if (dy != 0) scrollView.scrollBy(0, dy);
                lastAutoTick = now;
                handler.postDelayed(this, 16);
            }
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        settings = new AppSettings(this);
        setRequestedOrientation(settings.androidOrientation());
        String id = getIntent().getStringExtra("scriptId");
        script = new ScriptStore(this).get(id);
        if (script == null) { finish(); return; }
        aligner = new ScriptAligner(script.content);
        buildUi();
        startMode();
        startRemoteIfNeeded();
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(settings.backgroundColor());

        scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        contentView = new TextView(this);
        contentView.setTextColor(settings.textColor());
        contentView.setTextSize(settings.textSizeSp());
        contentView.setLineSpacing(TextUtil.dp(this, 8), 1.0f);
        contentView.setPadding(TextUtil.dp(this, 32), TextUtil.dp(this, 80), TextUtil.dp(this, 32), TextUtil.dp(this, 120));
        contentView.setText(script.content);
        if (settings.mirrorPrompt()) contentView.setScaleX(-1f);
        scrollView.addView(contentView, new ScrollView.LayoutParams(-1, -2));
        root.addView(scrollView, new FrameLayout.LayoutParams(-1, -1));

        statusView = new TextView(this);
        statusView.setTextColor(Color.GRAY);
        statusView.setTextSize(13);
        statusView.setPadding(TextUtil.dp(this, 12), TextUtil.dp(this, 8), TextUtil.dp(this, 12), TextUtil.dp(this, 8));
        statusView.setText(modeName() + " · 长按屏幕显示速度条/状态");
        root.addView(statusView, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        speedPanel = new LinearLayout(this);
        speedPanel.setOrientation(LinearLayout.VERTICAL);
        speedPanel.setPadding(TextUtil.dp(this, 16), TextUtil.dp(this, 12), TextUtil.dp(this, 16), TextUtil.dp(this, 14));
        speedPanel.setBackgroundColor(Color.argb(220, 20, 22, 26));
        TextView speedLabel = new TextView(this);
        speedLabel.setTextColor(Color.WHITE);
        speedLabel.setTextSize(15);
        speedLabel.setText("自动滚动速度：" + (int) settings.autoSpeedPxPerSec() + " px/s");
        speedPanel.addView(speedLabel);
        SeekBar speed = new SeekBar(this);
        speed.setMax(260);
        speed.setProgress((int) settings.autoSpeedPxPerSec());
        speed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int v = Math.max(0, progress);
                settings.setAutoSpeedPxPerSec(v);
                speedLabel.setText("自动滚动速度：" + v + " px/s");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        speedPanel.addView(speed);
        TextView tip = new TextView(this);
        tip.setTextColor(Color.LTGRAY);
        tip.setTextSize(12);
        tip.setText("遥控协议：HTTP/UDP 端口 " + settings.remotePort() + "，SCROLL 正数继续读，负数回退。音量键/方向键/鼠标滚轮也可控制。 ");
        speedPanel.addView(tip);
        speedPanel.setVisibility(View.GONE);
        root.addView(speedPanel, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        root.setOnLongClickListener(v -> {
            speedPanel.setVisibility(speedPanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            return true;
        });
        contentView.setOnLongClickListener(v -> {
            speedPanel.setVisibility(speedPanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            return true;
        });
        setContentView(root);
    }

    private void startMode() {
        if (settings.mode() == AppSettings.MODE_AUTO) {
            lastAutoTick = System.currentTimeMillis();
            handler.post(autoRunnable);
        } else if (settings.mode() == AppSettings.MODE_VOICE) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1001);
            } else {
                startAsr();
            }
        } else {
            statusView.setText("遥控模式 · 等待蓝牙遥控器或局域网控制端");
        }
    }

    private void startAsr() {
        asrEngine = AsrEngineFactory.create(this);
        statusView.setText("语音识别模式 · 正在启动 " + asrEngine.name());
        asrEngine.start(new AsrEngine.Listener() {
            @Override public void onPartialText(String text) { onRecognized(text, false); }
            @Override public void onFinalText(String text) { onRecognized(text, true); }
            @Override public void onError(String message) { statusView.setText("语音识别：" + message); }
            @Override public void onReady(String engineName) { statusView.setText("语音识别模式 · " + engineName + " · 支持回读上一段"); }
        });
    }

    private void onRecognized(String text, boolean fin) {
        if (paused || text == null || text.trim().isEmpty()) return;
        ScriptAligner.AlignmentResult r = aligner.onRecognizedText(text, settings.voiceThreshold());
        updateStyledText();
        scrollToChar(r.scrollCharOffset);
        statusView.setText((fin ? "最终" : "实时") + "识别 · 匹配 " + (int)(r.score * 100) + "%" + (r.backtracked ? " · 已自动回读上一段" : "") + "\n" + text);
    }

    private void updateStyledText() {
        SpannableString span = new SpannableString(script.content);
        int completedColor = settings.completedTextColor();
        for (ScriptAligner.Sentence s : aligner.sentences()) {
            if (s.index < aligner.completedUntilExclusive()) {
                span.setSpan(new ForegroundColorSpan(completedColor), s.start, s.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        contentView.setText(span);
    }

    private void scrollToChar(int offset) {
        contentView.post(() -> {
            if (contentView.getLayout() == null) return;
            int safe = Math.max(0, Math.min(offset, contentView.length()));
            int line = contentView.getLayout().getLineForOffset(safe);
            int y = Math.max(0, contentView.getLayout().getLineTop(line) - scrollView.getHeight() / 3);
            scrollView.smoothScrollTo(0, y);
        });
    }

    private void startRemoteIfNeeded() {
        if (!settings.remoteEnabled()) return;
        remoteServer = new RemoteServer(settings.remotePort(), this);
        remoteServer.start();
    }

    @Override public void onRemoteScroll(float dy) { scrollView.scrollBy(0, (int) dy); }
    @Override public void onRemotePause(boolean p) {
        paused = p;
        statusView.setText((paused ? "已暂停" : "已继续") + " · " + modeName());
    }
    @Override public void onRemoteTop() { scrollView.smoothScrollTo(0, 0); }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        int step = Math.max(60, (int) settings.autoSpeedPxPerSec() * 2);
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_PAGE_DOWN) {
            scrollView.scrollBy(0, step);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_PAGE_UP) {
            scrollView.scrollBy(0, -step);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            paused = !paused;
            statusView.setText((paused ? "已暂停" : "已继续") + " · " + modeName());
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override public boolean onGenericMotionEvent(MotionEvent event) {
        if ((event.getSource() & android.view.InputDevice.SOURCE_CLASS_POINTER) != 0 && event.getAction() == MotionEvent.ACTION_SCROLL) {
            float v = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
            if (v != 0) {
                scrollView.scrollBy(0, (int) (-v * 90));
                return true;
            }
        }
        return super.onGenericMotionEvent(event);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) startAsr();
        else Toast.makeText(this, "没有麦克风权限，语音识别模式无法使用", Toast.LENGTH_LONG).show();
    }

    private String modeName() {
        int m = settings.mode();
        if (m == AppSettings.MODE_AUTO) return "自动滚动模式";
        if (m == AppSettings.MODE_VOICE) return "语音识别模式";
        return "遥控模式";
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (remoteServer != null) remoteServer.stop();
        if (asrEngine != null) asrEngine.stop();
    }
}
