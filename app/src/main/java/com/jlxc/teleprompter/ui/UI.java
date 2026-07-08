package com.jlxc.teleprompter.ui;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.os.Build;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.ViewGroup;

import com.jlxc.teleprompter.util.TextUtil;

public final class UI {
    public static final int ACCENT = Color.rgb(57, 197, 187);
    public static final int BG = Color.rgb(16, 18, 22);
    public static final int CARD = Color.rgb(28, 31, 38);

    private UI() {}

    public static LinearLayout vertical(Activity a) {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(TextUtil.dp(a, 18), TextUtil.dp(a, 18), TextUtil.dp(a, 18), TextUtil.dp(a, 18));
        l.setBackgroundColor(BG);
        return l;
    }

    public static TextView title(Activity a, String text) {
        TextView v = new TextView(a);
        v.setText(text);
        v.setTextColor(Color.WHITE);
        v.setTextSize(26);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(0, 0, 0, TextUtil.dp(a, 18));
        return v;
    }

    public static TextView label(Activity a, String text) {
        TextView v = new TextView(a);
        v.setText(text);
        v.setTextColor(Color.WHITE);
        v.setTextSize(15);
        v.setPadding(0, TextUtil.dp(a, 10), 0, TextUtil.dp(a, 6));
        return v;
    }

    public static Button button(Activity a, String text) {
        Button b = new Button(a);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(16);
        b.setBackgroundColor(ACCENT);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, TextUtil.dp(a, 54));
        lp.setMargins(0, TextUtil.dp(a, 8), 0, TextUtil.dp(a, 8));
        b.setLayoutParams(lp);
        return b;
    }

    public static TextView card(Activity a, String title, String sub) {
        TextView v = new TextView(a);
        v.setText(title + (sub == null || sub.isEmpty() ? "" : "\n" + sub));
        v.setTextColor(Color.WHITE);
        v.setTextSize(16);
        v.setBackgroundColor(CARD);
        v.setPadding(TextUtil.dp(a, 16), TextUtil.dp(a, 14), TextUtil.dp(a, 16), TextUtil.dp(a, 14));
        v.setMinHeight(TextUtil.dp(a, 70));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, TextUtil.dp(a, 10));
        v.setLayoutParams(lp);
        return v;
    }

    public static ScrollView scrollWrap(Activity a, View content) {
        ScrollView sv = new ScrollView(a);
        sv.setFillViewport(false);
        sv.setBackgroundColor(BG);
        sv.addView(content, new ScrollView.LayoutParams(-1, -2));
        return sv;
    }

    public static ArrayAdapter<String> darkSpinnerAdapter(Activity a, String[] items) {
        return new ArrayAdapter<String>(a, android.R.layout.simple_spinner_item, items) {
            private TextView style(View v, boolean dropDown) {
                TextView t = (TextView) v;
                t.setTextColor(dropDown ? Color.BLACK : Color.WHITE);
                t.setTextSize(16);
                t.setBackgroundColor(dropDown ? Color.WHITE : CARD);
                t.setPadding(TextUtil.dp(a, 12), TextUtil.dp(a, 10), TextUtil.dp(a, 12), TextUtil.dp(a, 10));
                return t;
            }

            @Override public View getView(int position, View convertView, ViewGroup parent) {
                return style(super.getView(position, convertView, parent), false);
            }

            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                return style(super.getDropDownView(position, convertView, parent), true);
            }
        };
    }


    public static void hideStatusBar(Activity a) {
        Window window = a.getWindow();
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        View decor = window.getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = decor.getWindowInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars());
            }
        }
    }

    public static void backBar(Activity a, LinearLayout root, String title) {
        TextView v = title(a, "‹  " + title);
        v.setOnClickListener(view -> a.finish());
        root.addView(v);
    }
}

