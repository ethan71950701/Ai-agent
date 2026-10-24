package com.phoneai.overlay;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * 遠端指令（Telegram 群組）：家裡這台機器人收到「<裝置名稱> 開／關／狀態」，就切換自己的 Tailscale 並回覆結果。
 * 只接受設定的使用者帳號在設定的群組發的指令。機器人的 token 只存在這支手機的 App 資料夾。
 */
final class TelegramRemote {
    private static volatile boolean running;
    private static volatile Thread loop;

    private TelegramRemote() { }

    private static SharedPreferences cfg(Context c) { return c.getSharedPreferences("cfg", Context.MODE_PRIVATE); }

    static boolean enabled(Context c) { return cfg(c).getBoolean("tg_enabled", false); }

    static void setEnabled(Context c, boolean on) {
        cfg(c).edit().putBoolean("tg_enabled", on).apply();
    }

    static String token(Context c) {
        try {
            return new String(Files.readAllBytes(new File(c.getFilesDir(), "telegram_token").toPath()), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return "";
        }
    }

    static void saveToken(Context c, String t) throws Exception {
        File f = new File(c.getFilesDir(), "telegram_token");
        Files.write(f.toPath(), t.trim().getBytes(StandardCharsets.UTF_8));
        f.setReadable(false, false);
        f.setReadable(true, true);
    }

    static long allowedUser(Context c) { return cfg(c).getLong("tg_user", 0); }

    static long group(Context c) { return cfg(c).getLong("tg_chat", 0); }

    static String label(Context c) { return cfg(c).getString("tg_label", "家裡手機"); }

    static void saveSettings(Context c, long user, long chat, String label) {
        cfg(c).edit().putLong("tg_user", user).putLong("tg_chat", chat).putString("tg_label", label).apply();
    }

    /** 最近收到的訊息來源（給設定頁面挑選用）。 */
    static String lastSeen(Context c) { return cfg(c).getString("tg_seen", ""); }

    static boolean isRunning() { return running; }

    /** 開始輪詢（在 RemoteService 裡呼叫）。已經在跑就不重複開。 */
    static synchronized void start(Context c) {
        if (running) return;
        final Context app = c.getApplicationContext();
        running = true;
        loop = new Thread(() -> {
            long offset = 0;
            while (running && enabled(app)) {
                try {
                    String tk = token(app);
                    if (tk.isEmpty()) { sleep(5000); continue; }
                    JSONObject r = api(tk, "getUpdates", "timeout=25&offset=" + offset);
                    JSONArray res = r.optJSONArray("result");
                    if (res == null) { sleep(5000); continue; }
                    for (int i = 0; i < res.length(); i++) {
                        JSONObject u = res.getJSONObject(i);
                        offset = Math.max(offset, u.getLong("update_id") + 1);
                        JSONObject m = u.optJSONObject("message");
                        if (m != null) handle(app, tk, m);
                    }
                } catch (Exception e) {
                    sleep(5000);   // 網路斷了就等一下再試
                }
            }
            running = false;
        }, "telegram-remote");
        loop.setDaemon(true);
        loop.start();
    }

    static synchronized void stop() {
        running = false;
    }

    private static void handle(Context c, String tk, JSONObject m) throws Exception {
        JSONObject from = m.optJSONObject("from");
        JSONObject chat = m.optJSONObject("chat");
        if (from == null || chat == null) return;
        long fromId = from.getLong("id"), chatId = chat.getLong("id");
        remember(c, fromId, chatId, chat.optString("title", chat.optString("first_name", "")));
        // 只接受設定的帳號、設定的群組（群組還沒設定時不處理任何指令）
        if (fromId != allowedUser(c) || chatId != group(c)) return;
        String text = m.optString("text", "").trim();
        // 去掉開頭的 @機器人名稱
        while (text.startsWith("@")) {
            int sp = text.indexOf(' ');
            text = sp < 0 ? "" : text.substring(sp + 1).trim();
        }
        String[] parts = text.split("\\s+");
        if (parts.length < 2 || !parts[0].equals(label(c))) return;
        String cmd = parts[1];
        long chatTo = chatId;
        if (cmd.equals("狀態") || cmd.equalsIgnoreCase("status")) {
            TailscaleLauncher.checkState(c, now -> reply(c, tk, chatTo, now == null ? "讀不到狀態（請先在 App 設定點擊位置）"
                    : "Tailscale 目前：" + (now ? "開" : "關")));
        } else if (cmd.equals("開") || cmd.equals("關") || cmd.equalsIgnoreCase("on") || cmd.equalsIgnoreCase("off")) {
            final boolean want = cmd.equals("開") || cmd.equalsIgnoreCase("on");
            TailscaleLauncher.checkState(c, now -> {
                if (now == null) {
                    reply(c, tk, chatTo, "讀不到狀態（請先在 App 設定點擊位置）");
                } else if (now == want) {
                    reply(c, tk, chatTo, "Tailscale 已經是" + (want ? "開" : "關") + "的");
                } else {
                    TailscaleLauncher.toggle(c, after -> reply(c, tk, chatTo, after == null ? "切換失敗，請看看手機"
                            : "Tailscale 已" + (after ? "開" : "關")));
                }
            });
        }
    }

    /** 記下最近的來源（ID 與名稱），設定頁面可以直接挑，不用查 ID。 */
    private static synchronized void remember(Context c, long user, long chat, String title) {
        String cur = lastSeen(c);
        String line = "使用者 " + user + "　聊天 " + chat + (title.isEmpty() ? "" : "（" + title + "）");
        if (cur.contains(line)) return;
        String[] lines = cur.isEmpty() ? new String[0] : cur.split("\n");
        StringBuilder sb = new StringBuilder(line);
        for (int i = 0; i < lines.length && i < 4; i++) sb.append('\n').append(lines[i]);
        cfg(c).edit().putString("tg_seen", sb.toString()).apply();
    }

    private static void reply(Context c, String tk, long chat, String text) {
        new Thread(() -> {
            try {
                api(tk, "sendMessage", "chat_id=" + chat + "&text=" + URLEncoder.encode(text, "UTF-8"));
            } catch (Exception ignored) { }
        }).start();
    }

    /** 呼叫 Telegram Bot API。params 是 URL 查詢字串（已編碼）。 */
    static JSONObject api(String tk, String method, String params) throws Exception {
        URL url = new URL("https://api.telegram.org/bot" + tk + "/" + method + "?" + params);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setConnectTimeout(15000);
        con.setReadTimeout(40000);
        InputStream in = con.getResponseCode() < 400 ? con.getInputStream() : con.getErrorStream();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        con.disconnect();
        return new JSONObject(bo.toString("UTF-8"));
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }
}
