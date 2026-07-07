package com.jlxc.teleprompter;

import android.app.Activity;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.jlxc.teleprompter.remote.RemoteServerHub;
import com.jlxc.teleprompter.settings.AppSettings;
import com.jlxc.teleprompter.ui.UI;
import com.jlxc.teleprompter.util.NetworkUtil;

public class RemoteSettingsActivity extends Activity {
    private AppSettings s;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        s = new AppSettings(this);
        LinearLayout root = UI.vertical(this);
        UI.backBar(this, root, "遥控设置");

        TextView ip = UI.card(this, "当前手机 IP", NetworkUtil.localIpsText(this) + "\n控制端输入同一 Wi-Fi/热点网段的 IP；提词端默认端口 " + s.remotePort());
        root.addView(ip);

        TextView serviceStatus = UI.card(this, "服务状态", RemoteServerHub.statusText());
        root.addView(serviceStatus);

        Switch remote = new Switch(this);
        remote.setText("进入提词页面后启用局域网遥控服务");
        remote.setTextColor(android.graphics.Color.WHITE);
        remote.setTextSize(16);
        remote.setChecked(s.remoteEnabled());
        remote.setOnCheckedChangeListener((buttonView, isChecked) -> {
            s.setRemoteEnabled(isChecked);
            RemoteServerHub.restart(this);
            serviceStatus.setText("服务状态\n" + RemoteServerHub.statusText());
        });
        root.addView(remote);

        root.addView(UI.label(this, "端口号"));
        EditText port = new EditText(this);
        port.setSingleLine(true);
        port.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        port.setText(String.valueOf(s.remotePort()));
        port.setTextColor(android.graphics.Color.WHITE);
        root.addView(port);
        root.addView(UI.button(this, "保存端口"));
        root.getChildAt(root.getChildCount() - 1).setOnClickListener(v -> {
            try {
                int p = Integer.parseInt(port.getText().toString().trim());
                if (p < 1024 || p > 65535) throw new IllegalArgumentException();
                s.setRemotePort(p);
                RemoteServerHub.restart(this);
                Toast.makeText(this, "端口已保存，遥控服务已重启", Toast.LENGTH_SHORT).show();
                serviceStatus.setText("服务状态\n" + RemoteServerHub.statusText());
                ip.setText("当前手机 IP\n" + NetworkUtil.localIpsText(this) + "\n控制端输入同一 Wi-Fi/热点网段的 IP；提词端默认端口 " + s.remotePort());
            } catch (Exception e) { Toast.makeText(this, "端口范围 1024～65535", Toast.LENGTH_SHORT).show(); }
        });

        TextView protocol = UI.card(this, "控制协议", "HTTP: /api/ping, /api/remote/scroll?dy=80\nUDP: SCROLL 80 / SCROLL -80\ndy>0 向上滚动继续读，dy<0 回退。蓝牙鼠标滚轮/方向键在提词页面内也可直接控制。 ");
        root.addView(protocol);
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        RemoteServerHub.ensureStarted(this);
    }
}
