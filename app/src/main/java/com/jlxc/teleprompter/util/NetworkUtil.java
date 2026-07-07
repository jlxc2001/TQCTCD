package com.jlxc.teleprompter.util;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class NetworkUtil {
    private NetworkUtil() {}

    public static String localIp(Context c) {
        List<String> ips = localIps(c);
        if (!ips.isEmpty()) return ips.get(0);
        return "未获取到 IP";
    }

    public static String localIpsText(Context c) {
        List<String> ips = localIps(c);
        if (ips.isEmpty()) return "未获取到 IP";
        StringBuilder sb = new StringBuilder();
        for (String ip : ips) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(ip);
        }
        return sb.toString();
    }

    private static List<String> localIps(Context c) {
        ArrayList<String> result = new ArrayList<>();
        try {
            WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null && wm.getConnectionInfo() != null) {
                String ip = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
                if (isUsefulIp(ip) && !result.contains(ip)) result.add(ip);
            }
        } catch (Exception ignored) {}
        try {
            ArrayList<String> lan = new ArrayList<>();
            ArrayList<String> other = new ArrayList<>();
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                String name = ni.getName() == null ? "" : ni.getName().toLowerCase();
                if (!ni.isUp() || ni.isLoopback()) continue;
                boolean likelyLan = name.contains("wlan") || name.contains("wifi") || name.contains("ap") || name.contains("eth") || name.contains("rndis");
                boolean likelyMobile = name.contains("rmnet") || name.contains("ccmni") || name.contains("cell") || name.contains("wwan");
                for (java.net.InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (addr.isLoopbackAddress() || !(addr instanceof Inet4Address)) continue;
                    String ip = addr.getHostAddress();
                    if (!isUsefulIp(ip)) continue;
                    String text = ip + "  (" + ni.getName() + ")";
                    if (result.contains(text) || lan.contains(text) || other.contains(text)) continue;
                    if (likelyLan && !likelyMobile) lan.add(text); else other.add(text);
                }
            }
            for (String s : lan) if (!result.contains(s)) result.add(s);
            for (String s : other) if (!result.contains(s)) result.add(s);
        } catch (Exception ignored) {}
        return result;
    }

    private static boolean isUsefulIp(String ip) {
        if (ip == null || ip.isEmpty() || "0.0.0.0".equals(ip) || "127.0.0.1".equals(ip)) return false;
        return !ip.startsWith("169.254.");
    }
}
