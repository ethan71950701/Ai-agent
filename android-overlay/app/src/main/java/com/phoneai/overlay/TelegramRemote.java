package com.phoneai.overlay;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 遠端指令（Telegram 群組，靠通知監聽）：群組裡有人傳「@機器人 <裝置名稱> 開／關／狀態」，
 * Telegram 會在手機上跳通知，PhoneAI 讀到通知後比對：群組名稱、發訊息的人、裝置名稱都要對得上才執行。
 * 同一個機器人和群組可以給好幾台裝置用，每台只回應自己的名字。
 */
final class TelegramRemote {
    private static final Pattern CMD = Pattern.compile("(?:@\\S+\\s+)*(\\S+)\\s+(開|關|狀態|on|off|status)", Pattern.CASE_INSENSITIVE);

    private TelegramRemote() { }

    private static SharedPreferences cfg(Context c) { return c.getSharedPreferences("cfg", Context.MODE_PRIVATE); }

    static boolean enabled(Context c) { return cfg(c).getBoolean("tg_enabled", false); }

    static void setEnabled(Context c, boolean on) { cfg(c).edit().putBoolean("tg_enabled", on).apply(); }

    static String label(Context c) { return cfg(c).getString("tg_label", "家裡手機"); }

    static String group(Context c) { return cfg(c).getString("tg_group", ""); }

    static String me(Context c) { return cfg(c).getString("tg_me", ""); }

    static void saveSettings(Context c, String groupName, String myName, String label) {
        cfg(c).edit().putString("tg_group", groupName.trim()).putString("tg_me", myName.trim())
                .putString("tg_label", label.trim().isEmpty() ? "家裡手機" : label.trim()).apply();
    }

    /** 通知監聽收到 Telegram 的通知時呼叫：title 是群組名稱，text 通常是「發訊息的人: 內容」。 */
    static void onNotification(Context c, String title, String text) {
        if (!enabled(c)) return;
        if (group(c).isEmpty() || !title.trim().equals(group(c))) return;   // 不是設定的群組
        int colon = text.indexOf(':');
        if (colon < 0) return;
        String sender = text.substring(0, colon).trim();
        String body = text.substring(colon + 1).trim();
        if (me(c).isEmpty() || !sender.equals(me(c))) return;                 // 不是設定的人
        Matcher m = CMD.matcher(body);
        if (!m.find() || !m.group(1).equals(label(c))) return;               // 不是這台的名字
        String cmd = m.group(2);
        if (cmd.equals("狀態") || cmd.equalsIgnoreCase("status")) {
            TailscaleLauncher.checkState(c, now -> result(c, now == null ? "讀不到狀態（請先設定點擊位置）"
                    : "Tailscale 目前：" + (now ? "開" : "關")));
        } else {
            final boolean want = cmd.equals("開") || cmd.equalsIgnoreCase("on");
            TailscaleLauncher.checkState(c, now -> {
                if (now == null) {
                    result(c, "讀不到狀態（請先設定點擊位置）");
                } else if (now == want) {
                    result(c, "Tailscale 已經是" + (want ? "開" : "關") + "的");
                } else {
                    TailscaleLauncher.toggle(c, after -> result(c, after == null ? "切換失敗，請看看手機"
                            : "Tailscale 已" + (after ? "開" : "關")));
                }
            });
        }
    }

    /** 結果顯示在這支手機的通知上（不另外回到 Telegram）。 */
    private static void result(Context c, String text) {
        Notify.notifyText(c, L.t("遠端指令"), text, 7402);
    }
}
