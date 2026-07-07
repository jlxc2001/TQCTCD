package com.jlxc.teleprompter;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.jlxc.teleprompter.asr.AsrEngine;
import com.jlxc.teleprompter.asr.AsrEngineFactory;
import com.jlxc.teleprompter.data.Script;
import com.jlxc.teleprompter.data.ScriptStore;
import com.jlxc.teleprompter.follow.FollowReadState;
import com.jlxc.teleprompter.follow.SentenceItem;
import com.jlxc.teleprompter.follow.SentenceSplitter;
import com.jlxc.teleprompter.follow.SpeechTextMatcher;
import com.jlxc.teleprompter.remote.RemoteCommandListener;
import com.jlxc.teleprompter.remote.RemoteServerHub;
import com.jlxc.teleprompter.settings.AppSettings;
import com.jlxc.teleprompter.util.TextUtil;

import java.util.ArrayList;
import java.util.List;

public class PromptActivity extends Activity implements RemoteCommandListener {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private AppSettings settings;
    private Script script;
    private ScrollView scrollView;
    private TextView contentView;
    private TextView statusView;
    private TextView speedTipView;
    private LinearLayout speedPanel;
    private LinearLayout sentenceContainer;
    private final List<TextView> sentenceViews = new ArrayList<>();
    private List<SentenceItem> sentenceItems = new ArrayList<>();
    private FollowReadState followState;
    private SpeechTextMatcher speechMatcher;
    private long lastAutoTick;
    private boolean paused;
    private AsrEngine asrEngine;
    private int targetCenterScrollY = 0;
    private String lastStatusText = "";

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

    private final Runnable centerScrollRunnable = new Runnable() {
        @Override public void run() {
            if (scrollView == null) return;
            int current = scrollView.getScrollY();
            int diff = targetCenterScrollY - current;
            if (Math.abs(diff) <= 2) {
                scrollView.scrollTo(0, targetCenterScrollY);
                return;
            }
            int step = (int) (diff * 0.18f);
            if (step == 0) step = diff > 0 ? 1 : -1;
            scrollView.scrollTo(0, current + step);
            handler.postDelayed(this, 16);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        enterImmersiveMode();
        settings = new AppSettings(this);
        setRequestedOrientation(settings.androidOrientation());
        String id = getIntent().getStringExtra("scriptId");
        script = new ScriptStore(this).get(id);
        if (script == null) { finish(); return; }
        buildUi();
        startMode();
        startRemoteIfNeeded();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterImmersiveMode();
    }

    private void enterImmersiveMode() {
        Window window = getWindow();
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        View decor = window.getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(lp);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = decor.getWindowInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        }
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(settings.backgroundColor());

        scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scrollView.setVerticalScrollBarEnabled(false);

        if (settings.mode() == AppSettings.MODE_VOICE) {
            buildSentencePromptUi(root);
        } else {
            buildPlainPromptUi(root);
        }

        // 提词页面默认保持干净，不再把 ASR 调试文字压在顶部；调试信息放到长按面板里。
        statusView = new TextView(this);
        statusView.setVisibility(View.GONE);
        root.addView(statusView, new FrameLayout.LayoutParams(1, 1, Gravity.TOP));

        buildSpeedPanel(root);

        root.setOnLongClickListener(v -> {
            toggleSpeedPanel();
            return true;
        });
        setContentView(root);
        enterImmersiveMode();
    }

    private void buildPlainPromptUi(FrameLayout root) {
        contentView = new TextView(this);
        contentView.setTextColor(settings.textColor());
        contentView.setTextSize(settings.textSizeSp());
        contentView.setLineSpacing(TextUtil.dp(this, 8), 1.0f);
        contentView.setPadding(TextUtil.dp(this, 32), TextUtil.dp(this, 80), TextUtil.dp(this, 32), TextUtil.dp(this, 120));
        contentView.setText(script.content);
        if (settings.mirrorPrompt()) contentView.setScaleX(-1f);
        contentView.setOnLongClickListener(v -> {
            toggleSpeedPanel();
            return true;
        });
        scrollView.addView(contentView, new ScrollView.LayoutParams(-1, -2));
        root.addView(scrollView, new FrameLayout.LayoutParams(-1, -1));
    }

    private void buildSentencePromptUi(FrameLayout root) {
        sentenceItems = SentenceSplitter.split(script.content);
        followState = new FollowReadState(sentenceItems);
        speechMatcher = new SpeechTextMatcher();
        sentenceContainer = new LinearLayout(this);
        sentenceContainer.setOrientation(LinearLayout.VERTICAL);
        sentenceContainer.setPadding(TextUtil.dp(this, 32), TextUtil.dp(this, 220), TextUtil.dp(this, 32), TextUtil.dp(this, 320));
        if (settings.mirrorPrompt()) sentenceContainer.setScaleX(-1f);

        sentenceViews.clear();
        for (SentenceItem item : sentenceItems) {
            TextView tv = new TextView(this);
            tv.setText(item.raw.trim());
            tv.setTextSize(settings.textSizeSp());
            tv.setLineSpacing(TextUtil.dp(this, 8), 1.0f);
            tv.setIncludeFontPadding(true);
            tv.setGravity(Gravity.START);
            tv.setPadding(TextUtil.dp(this, 14), TextUtil.dp(this, 10), TextUtil.dp(this, 14), TextUtil.dp(this, 10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, 0, 0, TextUtil.dp(this, 14));
            sentenceContainer.addView(tv, lp);
            sentenceViews.add(tv);
        }
        sentenceContainer.setOnLongClickListener(v -> {
            toggleSpeedPanel();
            return true;
        });
        applySentenceStyles();
        scrollView.addView(sentenceContainer, new ScrollView.LayoutParams(-1, -2));
        root.addView(scrollView, new FrameLayout.LayoutParams(-1, -1));
        sentenceContainer.postDelayed(() -> smoothCenterCurrentSentence(0f), 250);
    }

    private void buildSpeedPanel(FrameLayout root) {
        speedPanel = new LinearLayout(this);
        speedPanel.setOrientation(LinearLayout.VERTICAL);
        speedPanel.setPadding(TextUtil.dp(this, 16), TextUtil.dp(this, 12), TextUtil.dp(this, 16), TextUtil.dp(this, 14));
        speedPanel.setBackgroundColor(Color.argb(230, 20, 22, 26));
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
        speedTipView = new TextView(this);
        speedTipView.setTextColor(Color.LTGRAY);
        speedTipView.setTextSize(12);
        speedTipView.setText("语音模式：已读句变暗，当前句居中，不停顿也会按句推进；支持回读上一段。遥控协议：HTTP/UDP 端口 " + settings.remotePort() + "。 ");
        speedPanel.addView(speedTipView);
        speedPanel.setVisibility(View.GONE);
        root.addView(speedPanel, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
    }

    private void toggleSpeedPanel() {
        speedPanel.setVisibility(speedPanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        if (speedPanel.getVisibility() == View.VISIBLE && speedTipView != null && !lastStatusText.isEmpty()) {
            speedTipView.setText(lastStatusText);
        }
        enterImmersiveMode();
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
            updateHiddenStatus("遥控模式 · 等待蓝牙遥控器或局域网控制端");
        }
    }

    private void startAsr() {
        asrEngine = AsrEngineFactory.create(this);
        updateHiddenStatus("语音识别模式 · 正在启动 " + asrEngine.name());
        asrEngine.start(new AsrEngine.Listener() {
            @Override public void onPartialText(String text) { onRecognized(text, false); }
            @Override public void onFinalText(String text) { onRecognized(text, true); }
            @Override public void onError(String message) { updateHiddenStatus("语音识别：" + message); }
            @Override public void onReady(String engineName) { updateHiddenStatus("语音识别模式 · " + engineName + " · 按句跟读/回读"); }
        });
    }

    private void onRecognized(String text, boolean fin) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post(() -> onRecognized(text, fin));
            return;
        }
        if (paused || text == null || text.trim().isEmpty()) return;
        if (settings.mode() != AppSettings.MODE_VOICE || speechMatcher == null || followState == null) return;

        SpeechTextMatcher.FollowMatch match = speechMatcher.findBestMatch(
                text,
                sentenceItems,
                followState.currentIndex(),
                5,
                12,
                settings.voiceThreshold()
        );

        if (match.accepted) {
            followState.moveTo(match.index, match.progress);
            applySentenceStyles();
            smoothCenterCurrentSentence(match.progress);
        } else {
            // 即使暂时低于阈值，也保持当前句居中，避免画面漂移。
            smoothCenterCurrentSentence(followState.currentProgress());
        }

        int index = followState.currentIndex() + 1;
        int total = sentenceItems.size();
        updateHiddenStatus((fin ? "最终" : "实时")
                + "识别 · 当前 " + index + "/" + total
                + " · 匹配 " + (int) (match.score * 100) + "%"
                + (match.accepted ? "" : " · 低于识别率阈值")
                + (match.backtracked ? " · 已自动回读上一段" : "")
                + "\n" + text);
    }

    private void updateHiddenStatus(String text) {
        lastStatusText = text == null ? "" : text;
        if (statusView != null) statusView.setText(lastStatusText);
        if (speedPanel != null && speedPanel.getVisibility() == View.VISIBLE && speedTipView != null) {
            speedTipView.setText(lastStatusText);
        }
    }

    private void applySentenceStyles() {
        if (sentenceItems == null || sentenceViews == null) return;
        int base = settings.textColor();
        int highlightBg = colorWithAlpha(base, 0.16f);
        for (int i = 0; i < sentenceItems.size() && i < sentenceViews.size(); i++) {
            SentenceItem item = sentenceItems.get(i);
            TextView tv = sentenceViews.get(i);
            if (item.state == SentenceItem.ReadState.READ) {
                tv.setTextColor(colorWithAlpha(base, 0.28f));
                tv.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
                tv.setBackgroundColor(Color.TRANSPARENT);
            } else if (item.state == SentenceItem.ReadState.CURRENT) {
                tv.setTextColor(colorWithAlpha(base, 1.0f));
                tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                tv.setBackgroundColor(highlightBg);
            } else {
                tv.setTextColor(colorWithAlpha(base, 0.68f));
                tv.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
                tv.setBackgroundColor(Color.TRANSPARENT);
            }
        }
    }

    private int colorWithAlpha(int color, float alpha) {
        int original = Color.alpha(color);
        int a = Math.max(0, Math.min(255, (int) (original * alpha)));
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }

    private void smoothCenterCurrentSentence(float progress) {
        if (sentenceViews == null || sentenceViews.isEmpty() || followState == null) return;
        int idx = Math.max(0, Math.min(followState.currentIndex(), sentenceViews.size() - 1));
        TextView currentView = sentenceViews.get(idx);
        currentView.post(() -> {
            if (scrollView == null || currentView.getHeight() <= 0) return;
            int childHeight = scrollView.getChildAt(0) == null ? 0 : scrollView.getChildAt(0).getHeight();
            int maxScroll = Math.max(0, childHeight - scrollView.getHeight());
            int center = currentView.getTop() + currentView.getHeight() / 2;
            int withinSentenceOffset = (int) (currentView.getHeight() * Math.max(0f, Math.min(1f, progress)) * 0.45f);
            targetCenterScrollY = Math.max(0, Math.min(maxScroll, center - scrollView.getHeight() / 2 + withinSentenceOffset));
            handler.removeCallbacks(centerScrollRunnable);
            handler.post(centerScrollRunnable);
        });
    }

    private void startRemoteIfNeeded() {
        if (!settings.remoteEnabled()) {
            updateHiddenStatus(modeName() + " · 局域网遥控服务已关闭");
            return;
        }
        RemoteServerHub.ensureStarted(this);
        RemoteServerHub.setActiveListener(this);
    }

    @Override public void onRemoteScroll(float dy) { scrollView.scrollBy(0, (int) dy); }
    @Override public void onRemotePause(boolean p) {
        paused = p;
        updateHiddenStatus((paused ? "已暂停" : "已继续") + " · " + modeName());
    }
    @Override public void onRemoteTop() {
        if (settings.mode() == AppSettings.MODE_VOICE && followState != null && speechMatcher != null) {
            followState.resetTo(0);
            speechMatcher.reset();
            applySentenceStyles();
            smoothCenterCurrentSentence(0f);
        } else {
            scrollView.smoothScrollTo(0, 0);
        }
    }

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
            updateHiddenStatus((paused ? "已暂停" : "已继续") + " · " + modeName());
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
        RemoteServerHub.clearActiveListener(this);
        if (asrEngine != null) asrEngine.stop();
    }
}
