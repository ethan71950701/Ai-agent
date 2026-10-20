package com.phoneai.overlay;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * App 自己更新：查最新的 GitHub Release（不需要權杖），有新版本就問一次要不要更新；
 * 要的話在通知列顯示下載進度，下載完用 root 安裝（簽名固定，所以覆蓋安裝）。
 */
final class Updater {
    private Updater() { }

    private static final String CH = "update";
    private static final int NOTE = 9001;
    private static volatile boolean busy;

    /** 按一次「檢查並更新」：檢查 → 有新版本才問 → 下載（通知列顯示進度）→ 安裝。 */
    static void start(final Activity a) {
        if (busy) { toast(a, "正在檢查或更新中"); return; }
        busy = true;
        toast(a, "檢查更新中…");
        new Thread(() -> {
            try {
                String scriptMsg = "";   // 腳本已經不需要：所有功能都在 App 裡
                JSONObject rel = latest();
                if (rel == null) {
                    done(a, "目前沒有發布的版本，已是最新版本");
                    return;
                }
                String sha = commitOf(rel);
                String installed = installedSha(a);
                long remote = runOf(rel.optString("tag_name"));   // 發布的版本號 = CI 執行序號（build-<序號>-<sha7>）
                String shown = remote > 0 ? "1.0." + remote : sha.substring(0, Math.min(7, sha.length()));
                boolean newer = remote > 0 ? remote > BuildConfig.VERSION_CODE
                        : !(installed != null && !installed.equals("dev") && !sha.isEmpty() && sha.startsWith(installed));
                if (!newer) {
                    done(a, "已是最新版本（1.0." + BuildConfig.VERSION_CODE + "）");
                    return;
                }
                String url = null; long size = 0; String name = "";
                org.json.JSONArray assets = rel.getJSONArray("assets");
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject as = assets.getJSONObject(i);
                    if (as.getString("name").endsWith(".apk")) {
                        url = as.getString("browser_download_url");
                        size = as.optLong("size", 0);
                        name = as.getString("name");
                        break;
                    }
                }
                if (url == null) { done(a, "最新版本沒有 APK，稍後再試"); return; }
                final String v = shown;
                a.runOnUiThread(() -> toast(a, "已是最新版本"));
                final String apkUrl = url; final long apkSize = size; final String apkName = name;
                a.runOnUiThread(() -> new com.google.android.material.dialog.MaterialAlertDialogBuilder(a)
                        .setTitle("有新版本 " + v)
                        .setMessage("目前是 1.0." + BuildConfig.VERSION_CODE + "，最新是 " + v + "。\n\n" + notesOf(rel)
                                + "\n\n要現在更新嗎？進度會顯示在通知列與 Now Bar。")
                        .setPositiveButton("更新", (d, w) -> new Thread(() -> download(a, apkUrl, apkSize, apkName, v)).start())
                        .setNegativeButton("稍後", (d, w) -> busy = false)
                        .setOnCancelListener(d -> busy = false)
                        .show());
            } catch (Exception e) {
                done(a, "檢查失敗：" + e.getMessage());
            }
        }).start();
    }

    private static void download(Activity a, String url, long size, String name, String v) {
        NotificationManager nm = a.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CH, "App 更新", NotificationManager.IMPORTANCE_LOW));
        File out = new File(a.getCacheDir(), "update.apk");
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(20000);
            c.setReadTimeout(60000);
            c.setRequestProperty("User-Agent", "PhoneAI-Update");
            long total = size > 0 ? size : c.getContentLengthLong();
            long got = 0;
            int lastPct = -1;
            try (InputStream in = c.getInputStream(); OutputStream o = new FileOutputStream(out)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) {
                    o.write(buf, 0, n);
                    got += n;
                    int pct = total > 0 ? (int) (got * 100 / total) : 0;
                    if (pct != lastPct) {
                        lastPct = pct;
                        nm.notify(NOTE, progress(a, "下載 " + v + "：" + pct + "%", pct, false));
                    }
                }
            }
            nm.notify(NOTE, progress(a, "安裝 " + v + " 中…", 100, true));
            String r = Maintenance.suOnce("cp '" + out.getAbsolutePath() + "' /data/local/tmp/phoneai_update.apk && chmod 644 /data/local/tmp/phoneai_update.apk\n"
                    + "pm install -r --user 0 -i com.google.android.packageinstaller /data/local/tmp/phoneai_update.apk 2>&1\n"
                    + "rm -f /data/local/tmp/phoneai_update.apk");
            out.delete();
            nm.cancel(NOTE);
            if (r.contains("Success")) {
                done(a, "已更新到 " + v + "，App 會重新開啟");
            } else if (r.contains("UPDATE_INCOMPATIBLE") || r.contains("SIGNATURE")) {
                done(a, "簽章不同，無法直接覆蓋（請看「設定導覽」的說明）");
            } else {
                done(a, "安裝失敗：" + r.trim());
            }
        } catch (Exception e) {
            nm.cancel(NOTE);
            done(a, "下載失敗：" + e.getMessage());
        }
    }

    /** 下載／安裝進度：同時升級成 Now Bar（Live Update）。狀態列上的小字顯示百分比，Now Bar 上是進度條。 */
    private static Notification progress(Context c, String text, int pct, boolean indeterminate) {
        Notification.Builder b = new Notification.Builder(c, CH)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("PhoneAI 更新")
                .setContentText(text)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setProgress(100, pct, indeterminate);
        android.os.Bundle ex = new android.os.Bundle();
        ex.putBoolean("android.requestPromotedOngoing", true);   // 同 OverlayService：要求 Now Bar／Live Update
        b.addExtras(ex);
        if (android.os.Build.VERSION.SDK_INT >= 36) {
            b.setShortCriticalText(indeterminate ? "安裝" : pct + "%");
        }
        return b.build();
    }

    private static void done(Activity a, String msg) {
        busy = false;
        a.runOnUiThread(() -> toast(a, msg));
    }

    private static void toast(Activity a, String msg) {
        Toast.makeText(a, msg, Toast.LENGTH_LONG).show();
    }

    /** 最新的正式 Release；沒有就回傳 null。 */
    private static JSONObject latest() throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL("https://api.github.com/repos/" + BuildConfig.REPO + "/releases/latest").openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(20000);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("User-Agent", "PhoneAI-Update");
        if (c.getResponseCode() == 404)   // repo 是私人的時候，沒有權杖就看不到 Release，會回 404
            throw new java.io.IOException("找不到發布版本（404）：repo 是私人的，App 沒有權杖就查不到");
        if (c.getResponseCode() != 200) throw new java.io.IOException("GitHub 回應 " + c.getResponseCode());
        try (InputStream in = c.getInputStream()) {
            return new JSONObject(new String(in.readAllBytes(), "UTF-8"));
        }
    }

    /** 發布說明（去掉 commit 那一行），太長就截斷。 */
    static String notesOf(JSONObject rel) {
        String body = rel.optString("body", "");
        StringBuilder sb = new StringBuilder();
        for (String line : body.split("\n")) {
            if (line.startsWith("commit:")) continue;
            sb.append(line).append('\n');
        }
        String t = sb.toString().trim();
        if (t.isEmpty()) return "（這次沒有更新說明）";
        return t.length() > 600 ? t.substring(0, 600) + "…" : t;
    }

    /** 從標籤 build-<序號>-<sha7> 取出序號；讀不到回傳 0。 */
    static long runOf(String tag) {
        Matcher m = Pattern.compile("^build-(\\d+)-").matcher(tag == null ? "" : tag);
        return m.find() ? Long.parseLong(m.group(1)) : 0;
    }

    private static String commitOf(JSONObject rel) {
        Matcher m = Pattern.compile("commit:\\s*([0-9a-f]{7,40})").matcher(rel.optString("body", ""));
        return m.find() ? m.group(1) : "";
    }

    /** 目前安裝的版本（commit 前 7 碼）；讀不到回傳 null。 */
    private static String installedSha(Context c) {
        String b = c.getString(R.string.build_id);   // 格式：「28c9415 · 10/20 06:56」
        int i = b.indexOf(' ');
        return i > 0 ? b.substring(0, i) : b;
    }
}
