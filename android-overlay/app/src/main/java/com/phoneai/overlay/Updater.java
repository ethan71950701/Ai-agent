package com.phoneai.overlay;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.widget.TextView;
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
    static final int NOTE = 9001;
    private static volatile boolean busy;

    /** 按一次「檢查並更新」：檢查 → 有新版本才問 → 下載（通知列顯示進度）→ 安裝。 */
    static void start(final Activity a) {
        if (busy) { toast(a, L.t("正在檢查或更新中")); return; }
        busy = true;
        toast(a, L.t("檢查更新中…"));
        new Thread(() -> {
            try {
                String scriptMsg = "";   // 腳本已經不需要：所有功能都在 App 裡
                JSONObject rel = latest();
                if (rel == null) {
                    done(a, L.t("目前沒有發布的版本，已是最新版本"));
                    return;
                }
                String sha = commitOf(rel);
                String installed = installedSha(a);
                long remote = runOf(rel.optString("tag_name"));   // 發布的版本號 = CI 執行序號（build-<序號>-<sha7>）
                String shown = remote > 0 ? "1.0." + remote : sha.substring(0, Math.min(7, sha.length()));
                boolean newer = remote > 0 ? remote > BuildConfig.VERSION_CODE
                        : !(installed != null && !installed.equals("dev") && !sha.isEmpty() && sha.startsWith(installed));
                note(a, "手動檢查：最新 " + shown + "，目前 1.0." + BuildConfig.VERSION_CODE + (newer ? "，有新版本" : "，已是最新"));
                if (!newer) {
                    done(a, L.t("已是最新版本（1.0.") + BuildConfig.VERSION_CODE + L.t("）"));
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
                if (url == null) { done(a, L.t("最新版本沒有 APK，稍後再試")); return; }
                final String v = shown;
                final String apkUrl = url; final long apkSize = size; final String apkName = name;
                a.runOnUiThread(() -> new com.google.android.material.dialog.MaterialAlertDialogBuilder(a)
                        .setTitle(L.t("有新版本 ") + v)
                        .setMessage(L.t("目前是 1.0.") + BuildConfig.VERSION_CODE + L.t("，最新是 ") + v + L.t("。\n\n") + notesOf(rel)
                                + L.t("\n\n要現在更新嗎？進度會顯示在通知列與 Now Bar。"))
                        .setPositiveButton(L.t("更新"), (d, w) -> {
                            showProg(a, v);   // App 裡也顯示下載進度（通知列與 Now Bar 照舊）
                            new Thread(() -> download(a, apkUrl, apkSize, apkName, v)).start();
                        })
                        .setNegativeButton(L.t("稍後"), (d, w) -> busy = false)
                        .setOnCancelListener(d -> busy = false)
                        .show());
            } catch (Exception e) {
                note(a, "手動檢查失敗：" + e);
                done(a, L.t("檢查失敗：") + e.getMessage());
            }
        }).start();
    }

    /** 診斷用：最近一次檢查更新的結果（診斷頁顯示）。 */
    static void note(Context c, String s) {
        c.getSharedPreferences("cfg", Context.MODE_PRIVATE).edit().putString("upd_info",
                new java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.US).format(new java.util.Date()) + " " + s).apply();
    }

    static String info(Context c) { return c.getSharedPreferences("cfg", Context.MODE_PRIVATE).getString("upd_info", ""); }

    /**
     * 自動檢查（打開 App 時呼叫）：開關開著、距離上次檢查超過 1 小時，就在背景問一次；有新版本在通知欄提醒一則（點了就進 App 並開始更新流程）。
     * 只提醒，不會自動安裝。
     */
    static void autoCheck(final Activity a) {
        final android.content.SharedPreferences sp = a.getSharedPreferences("cfg", Context.MODE_PRIVATE);
        if (!sp.getBoolean("upd_auto", true)) return;
        long now = System.currentTimeMillis();
        if (now - sp.getLong("upd_last", 0) < 3600_000L) return;   // 最多每小時一次（以前 20 小時：開發期間一天出好幾版，使用者看不到提醒；GitHub 未登入每小時 60 次，夠用）
        sp.edit().putLong("upd_last", now).apply();
        new Thread(() -> {
            try {
                JSONObject rel = latest();
                long remote = rel == null ? 0 : runOf(rel.optString("tag_name"));
                boolean newer = remote > BuildConfig.VERSION_CODE;
                note(a, "自動檢查：最新 1.0." + remote + "，目前 1.0." + BuildConfig.VERSION_CODE + (newer ? "，有新版本" : "，已是最新"));
                if (!newer || a.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
                NotificationManager nm = a.getSystemService(NotificationManager.class);
                nm.createNotificationChannel(new NotificationChannel("update_notice", L.t("有新版本"), NotificationManager.IMPORTANCE_DEFAULT));
                android.content.Intent it = new android.content.Intent(a, MainActivity.class).putExtra("do_update", true)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);
                nm.notify(9002, new Notification.Builder(a, "update_notice")
                        .setSmallIcon(R.drawable.ic_stat)
                        .setContentTitle(L.t("Vertex 有新版本"))
                        .setContentText(L.t("最新是 1.0.") + remote + L.t("，目前是 1.0.") + BuildConfig.VERSION_CODE + L.t("。點一下更新"))
                        .setContentIntent(android.app.PendingIntent.getActivity(a, 1, it, android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT))
                        .setAutoCancel(true)
                        .build());
            } catch (Exception e) {
                note(a, "自動檢查失敗：" + e);
            }
        }, "update-auto").start();
    }

    private static void download(Activity a, String url, long size, String name, String v) {
        NotificationManager nm = a.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CH, L.t("App 更新"), NotificationManager.IMPORTANCE_LOW));
        File out = new File(a.getCacheDir(), "update.apk");
        try {
            boolean cached = !Root.isRoot() && size > 0 && out.length() == size;   // 沒 root 時上次下載好、卻因為缺「安裝未知應用程式」權限沒裝：直接用，不重新下載
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(20000);
            c.setReadTimeout(60000);
            c.setRequestProperty("User-Agent", "PhoneAI-Update");
            long total = size > 0 ? size : c.getContentLengthLong();
            long got = 0;
            int lastPct = -1;
            if (!cached) try (InputStream in = c.getInputStream(); OutputStream o = new FileOutputStream(out)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) {
                    o.write(buf, 0, n);
                    got += n;
                    int pct = total > 0 ? (int) (got * 100 / total) : 0;
                    if (pct != lastPct) {
                        lastPct = pct;
                        nm.notify(NOTE, progress(a, L.t("下載 ") + v + "：" + pct + "%", pct, false));
                        setProg(a, L.t("下載 ") + v + "：" + pct + "%" + (total > 0 ? String.format(java.util.Locale.US, "（%.1f / %.1f MB）", got / 1048576f, total / 1048576f) : ""), pct, false);
                    }
                }
            }
            if (!Root.isRoot()) {   // 沒有 root：交給系統安裝程式，使用者按一下「安裝」
                nm.cancel(NOTE);
                closeProg(a);
                installWithoutRoot(a, out, v);
                return;
            }
            nm.notify(NOTE, progress(a, L.t("安裝 ") + v + L.t(" 中…"), 100, true));
            setProg(a, L.t("安裝 ") + v + L.t(" 中…"), 100, true);
            String r = Maintenance.suOnce("cp '" + out.getAbsolutePath() + "' /data/local/tmp/phoneai_update.apk && chmod 644 /data/local/tmp/phoneai_update.apk\n"
                    + "pm install -r --user 0 -i com.google.android.packageinstaller /data/local/tmp/phoneai_update.apk 2>&1\n"
                    + "rm -f /data/local/tmp/phoneai_update.apk");
            out.delete();
            nm.cancel(NOTE);
            if (r.contains("Success")) {
                done(a, L.t("已更新到 ") + v + L.t("，App 會重新開啟"));
            } else if (r.contains("UPDATE_INCOMPATIBLE") || r.contains("SIGNATURE")) {
                done(a, L.t("簽章不同，無法直接覆蓋（請看「設定導覽」的說明）"));
            } else {
                done(a, L.t("安裝失敗：") + r.trim());
            }
        } catch (Exception e) {
            nm.cancel(NOTE);
            done(a, L.t("下載失敗：") + e.getMessage());
        }
    }

    /** 安裝回呼（InstallResultReceiver）收到結果後：清掉下載好的 APK、解除忙碌。 */
    static void finishInstall(Context c) {
        new File(c.getCacheDir(), "update.apk").delete();
        busy = false;
    }

    /** （更新測試 4）不用 root 安裝：把 APK 交給系統安裝程式（跟瀏覽器下載的 APK 同一個畫面，有「安裝中／完成」），需要允許「安裝未知應用程式」。 */
    private static void installWithoutRoot(Activity a, File apk, String v) {
        try {
            if (!a.getPackageManager().canRequestPackageInstalls()) {
                a.runOnUiThread(() -> {
                    toast(a, L.t("請先允許 Vertex「安裝未知應用程式」，再按一次更新"));
                    a.startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:" + a.getPackageName())));
                });
                busy = false;   // APK 留著：授權後再按更新，直接安裝不用重下載
                return;
            }
            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(a, a.getPackageName() + ".update", apk);
            Intent it = new Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra(Intent.EXTRA_RETURN_RESULT, false);
            a.runOnUiThread(() -> a.startActivity(it));
            busy = false;
        } catch (Exception e) {
            done(a, L.t("安裝失敗：") + e.getMessage());
        }
    }

    /** 下載／安裝進度：同時升級成 Now Bar（Live Update）。狀態列上的小字顯示百分比，Now Bar 上是進度條。 */
    private static Notification progress(Context c, String text, int pct, boolean indeterminate) {
        Notification.Builder b = new Notification.Builder(c, CH)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(L.t("Vertex 更新"))
                .setContentText(text)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setContentIntent(android.app.PendingIntent.getActivity(c, 0,   // 沒有 contentIntent 系統會把 Now Bar 膠囊藏起來（OverlayService 的註解有說明）
                        new android.content.Intent(c, MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP),
                        android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT))
                .setColor(OverlayService.CHIP_COLOR)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setProgress(100, pct, indeterminate);
        android.os.Bundle ex = new android.os.Bundle();
        ex.putBoolean("android.requestPromotedOngoing", true);   // 同 OverlayService：要求 Now Bar／Live Update
        b.addExtras(ex);
        if (android.os.Build.VERSION.SDK_INT >= 36) {
            b.setShortCriticalText(indeterminate ? L.t("安裝") : pct + "%");
        }
        return b.build();
    }

    private static void done(Activity a, String msg) {
        busy = false;
        closeProg(a);
        a.runOnUiThread(() -> toast(a, msg));
    }

    // ---------- App 裡的下載進度視窗（通知列的進度照舊） ----------
    private static android.app.Dialog progDlg;
    private static TextView progTv;
    private static com.google.android.material.progressindicator.LinearProgressIndicator progBar;

    private static void showProg(final Activity a, final String v) {
        a.runOnUiThread(() -> {
            try {
                float d = a.getResources().getDisplayMetrics().density;
                android.widget.LinearLayout box = new android.widget.LinearLayout(a);
                box.setOrientation(android.widget.LinearLayout.VERTICAL);
                box.setPadding((int) (24 * d), (int) (12 * d), (int) (24 * d), (int) (4 * d));
                progTv = new TextView(a);
                progTv.setText(L.t("準備下載 ") + v + "…");
                progTv.setTextSize(15);
                progTv.setTextColor(Ui.attr(a, com.google.android.material.R.attr.colorOnSurface));
                box.addView(progTv);
                progBar = new com.google.android.material.progressindicator.LinearProgressIndicator(a);
                progBar.setMax(100);
                progBar.setIndeterminate(true);   // 還沒收到第一筆資料前先轉圈
                android.widget.LinearLayout.LayoutParams bp = new android.widget.LinearLayout.LayoutParams(-1, -2);
                bp.topMargin = (int) (14 * d);
                box.addView(progBar, bp);
                progDlg = new com.google.android.material.dialog.MaterialAlertDialogBuilder(a).setTitle(L.t("更新 Vertex")).setView(box)
                        .setCancelable(false).setNegativeButton(L.t("背景進行"), null).show();   // 關掉視窗不會中斷，進度還在通知列
            } catch (Throwable t) {
                progDlg = null;
            }
        });
    }

    private static void setProg(final Activity a, final String text, final int pct, final boolean indeterminate) {
        a.runOnUiThread(() -> {
            if (progTv == null || progBar == null) return;
            progTv.setText(text);
            if (indeterminate) { progBar.setIndeterminate(true); return; }
            if (progBar.isIndeterminate()) { progBar.setVisibility(android.view.View.INVISIBLE); progBar.setIndeterminate(false); progBar.setVisibility(android.view.View.VISIBLE); }
            progBar.setProgressCompat(pct, true);
        });
    }

    private static void closeProg(final Activity a) {
        a.runOnUiThread(() -> {
            try { if (progDlg != null) progDlg.dismiss(); } catch (Throwable ignored) { }
            progDlg = null;
            progTv = null;
            progBar = null;
        });
    }

    private static void toast(Activity a, String msg) {
        Toast.makeText(a, msg, Toast.LENGTH_LONG).show();
    }

    /** 最新的正式 Release；沒有就回傳 null。 */
    private static JSONObject latest() throws Exception {
        // 不用 /releases/latest：它是依「標籤那個提交的日期」排序，不一定是編號最大的；改成列出最近幾個，自己挑 build 編號最大的
        HttpURLConnection c = (HttpURLConnection) new URL("https://api.github.com/repos/" + BuildConfig.REPO + "/releases?per_page=15").openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(20000);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("User-Agent", "PhoneAI-Update");
        if (c.getResponseCode() == 404)   // repo 是私人的時候，沒有權杖就看不到 Release，會回 404
            throw new java.io.IOException(L.t("找不到發布版本（404）：repo 是私人的，App 沒有權杖就查不到"));
        if (c.getResponseCode() == 403 || c.getResponseCode() == 429) {   // GitHub API 沒登入的查詢次數有限（每小時 60 次，同一個網路的人共用）：改走網頁，不吃這個額度
            JSONObject web = latestViaWeb();
            if (web != null) return web;
            throw new java.io.IOException(L.t("GitHub 回應 ") + c.getResponseCode() + L.t("（查詢次數用完，稍後再試）"));
        }
        if (c.getResponseCode() != 200) throw new java.io.IOException(L.t("GitHub 回應 ") + c.getResponseCode());
        try (InputStream in = c.getInputStream()) {
            org.json.JSONArray all = new org.json.JSONArray(new String(in.readAllBytes(), "UTF-8"));
            JSONObject best = null;
            long bestRun = -1;
            for (int i = 0; i < all.length(); i++) {
                JSONObject r = all.getJSONObject(i);
                if (r.optBoolean("draft") || r.optBoolean("prerelease")) continue;
                long run = runOf(r.optString("tag_name"));
                if (run > bestRun) { bestRun = run; best = r; }
            }
            return best;
        }
    }

    /**
     * API 被限流時的備用：github.com/<repo>/releases/latest 會轉址到 /releases/tag/<標籤>，從轉址拿到標籤，
     * 再組出 APK 的下載網址（檔名固定 PhoneAI.apk）。這條路不需要 API、不吃查詢額度；代價是沒有更新說明。查不到回 null。
     */
    private static JSONObject latestViaWeb() {
        try {
            String url = "https://github.com/" + BuildConfig.REPO + "/releases/latest";
            for (int hop = 0; hop < 4; hop++) {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setInstanceFollowRedirects(false);
                c.setConnectTimeout(15000);
                c.setReadTimeout(15000);
                c.setRequestProperty("User-Agent", "PhoneAI-Update");
                int code = c.getResponseCode();
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (code < 300 || code >= 400 || loc == null) return null;
                if (loc.startsWith("/")) loc = "https://github.com" + loc;
                Matcher m = Pattern.compile("/releases/tag/([^/?#]+)").matcher(loc);
                if (m.find()) {
                    String tag = java.net.URLDecoder.decode(m.group(1), "UTF-8");
                    String repo = loc.replaceFirst("^https://github.com/", "").replaceFirst("/releases/tag/.*$", "");
                    return new JSONObject().put("tag_name", tag).put("body", "")
                            .put("assets", new org.json.JSONArray().put(new JSONObject().put("name", "PhoneAI.apk").put("size", 0)
                                    .put("browser_download_url", "https://github.com/" + repo + "/releases/download/" + tag + "/PhoneAI.apk")));
                }
                url = loc;   // 先轉到新的 repo 位置（repo 改過名）再繼續
            }
        } catch (Exception e) {
            CrashLog.w("更新（網頁備用）", e);
        }
        return null;
    }

    /** 發布說明（去掉 commit 那一行），太長就截斷。 */
    static String notesOf(JSONObject rel) {
        String body = rel.optString("body", "");
        StringBuilder sb = new StringBuilder();
        for (String line : body.split("\n")) {
            if (line.startsWith("commit:")) continue;
            // 雙語條目「中文｜English」：依介面語言只留一半；沒有分隔符的舊說明原樣顯示
            int bar = line.indexOf('｜');
            String l = line;
            if (bar >= 0) l = L.en() ? line.substring(bar + 1).trim() : line.substring(0, bar).trim();
            // Markdown 符號在一般文字裡會露出來：標題去掉 ##，條列改成「•」
            if (l.startsWith("## ")) { if (sb.length() > 0) sb.append('\n'); l = l.substring(3).trim(); }
            else if (l.startsWith("- ")) l = "•  " + l.substring(2).trim();
            sb.append(l).append('\n');
        }
        String t = sb.toString().trim();
        if (t.isEmpty()) return L.t("（這次沒有更新說明）");
        // 對話框的說明區本身可以捲動，所以只防止極端長的內容；一般的更新說明完整顯示
        return t.length() > 4000 ? t.substring(0, 4000) + "…" : t;
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
