package com.phoneai.overlay;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 公告與功能開關：打開 App 時（不必等更新檢查）去 repo 抓一個小檔案 announcement.json，不用更新 App 就能看到最新公告；
 * 檔案裡也可以暫時停用出問題的功能（kill switch）。格式與用法見 docs/ANNOUNCEMENTS.md。
 *
 * 安全：只讀固定的 https 網址、只顯示文字（長度有上限、去掉控制字元）、「前往更新」按鈕用 App 內建的更新流程，
 * 檔案裡寫的任何網址都不會被開啟或下載；功能開關只認白名單裡的名稱；不傳送任何使用者資料。
 */
final class Announcements {
    private Announcements() { }

    /** 可以被遠端暫時停用的功能名稱（白名單；檔案裡寫別的名稱一律忽略）。 */
    static final List<String> FLAGS = Arrays.asList("background_mode", "file_transfer", "scheduled_lookup", "chat_stream", "run_shell");

    private static final long MIN_INTERVAL_MS = 10 * 60_000L;   // 打開 App 時最多每 10 分鐘抓一次
    private static final long FLAG_TTL_MS = 7L * 24 * 3600_000L;   // 抓不到網路太久，停用就自動失效，不會永遠關著
    private static final int MAX_BYTES = 20 * 1024;
    private static final String PREF_FLAGS = "remote_disabled", PREF_FLAGS_AT = "remote_disabled_at", PREF_SEEN = "ann_seen", PREF_LAST = "ann_last";

    /** 解析結果（純資料）。 */
    static final class Result {
        String id = "", severity = "info", title = "", body = "";
        boolean show;            // 這次要不要跳出公告
        boolean offerUpdate;     // 要不要放「前往更新」按鈕
        boolean repeat;          // 還沒更新就每次打開都提醒（critical 加版本門檻）
        final Set<String> disable = new HashSet<>();
    }

    /**
     * 純邏輯，可單元測試。
     * @param versionCode 目前安裝的版本號；nowMs 現在時間；zh 是否用中文；seen 已經看過的公告 id
     * @return 解析失敗回 null
     */
    static Result parse(String json, long versionCode, long nowMs, boolean zh, Set<String> seen) {
        try {
            JSONObject o = new JSONObject(json);
            Result r = new Result();
            r.id = clean(o.optString("id", ""), 60);
            String sev = o.optString("severity", "info");
            r.severity = sev.equals("warn") || sev.equals("critical") ? sev : "info";
            r.title = clean(pick(o.opt("title"), zh), 80);
            r.body = clean(pick(o.opt("body"), zh), 600);
            JSONArray dis = o.optJSONArray("disable");
            for (int i = 0; dis != null && i < dis.length(); i++) {
                String f = dis.optString(i);
                if (FLAGS.contains(f)) r.disable.add(f);
            }
            long below = o.optLong("min_version_below", 0);
            boolean versionOk = below <= 0 || versionCode < below;
            boolean expired = false;
            String exp = o.optString("expires", "");
            if (!exp.isEmpty()) {
                try {
                    java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
                    f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                    expired = nowMs > f.parse(exp).getTime() + 24L * 3600_000L;
                } catch (Exception ignored) { }
            }
            r.repeat = r.severity.equals("critical") && below > 0 && versionOk;
            r.offerUpdate = below > 0 || !r.severity.equals("info");
            boolean hasText = !r.id.isEmpty() && (!r.title.isEmpty() || !r.body.isEmpty());
            r.show = hasText && versionOk && !expired && (r.repeat || !seen.contains(r.id));
            return r;
        } catch (Exception e) {
            return null;
        }
    }

    /** {"zh":"…","en":"…"} 或單純一個字串；選不到對應語言就用另一個。 */
    private static String pick(Object v, boolean zh) {
        if (v instanceof JSONObject) {
            JSONObject t = (JSONObject) v;
            String a = t.optString(zh ? "zh" : "en", ""), b = t.optString(zh ? "en" : "zh", "");
            return a.isEmpty() ? b : a;
        }
        return v == null || v == JSONObject.NULL ? "" : String.valueOf(v);
    }

    /** 去掉控制字元、修剪、限制長度（只顯示純文字）。 */
    static String clean(String s, int max) {
        if (s == null) return "";
        String t = s.replaceAll("[\\p{Cntrl}&&[^\\n]]", "").trim();
        return t.length() > max ? t.substring(0, max) + "…" : t;
    }

    // ---------- 功能開關 ----------

    /** 這個功能現在被遠端暫時停用了嗎（最近一次成功抓到的公告檔案說了算，超過 7 天沒更新就失效）。 */
    static boolean off(String flag) {
        Context c = App.ctx;
        if (c == null) return false;
        try {
            SharedPreferences sp = c.getSharedPreferences("cfg", Context.MODE_PRIVATE);
            if (System.currentTimeMillis() - sp.getLong(PREF_FLAGS_AT, 0) > FLAG_TTL_MS) return false;
            String s = "," + sp.getString(PREF_FLAGS, "") + ",";
            return s.contains("," + flag + ",");
        } catch (Throwable t) { return false; }
    }

    // ---------- 抓取與顯示 ----------

    static boolean enabled(Context c) { return c.getSharedPreferences("cfg", Context.MODE_PRIVATE).getBoolean("ann_on", true); }

    /** 打開 App 時呼叫（主畫面 onResume）：背景抓一次，有要顯示的就跳對話框。 */
    static void checkOnOpen(final Activity a) { check(a, false); }

    /**
     * manual＝使用者在設定按「立即看公告」：不管 10 分鐘限制、也不管看過沒有，現在有公告就顯示，沒有或抓不到就用提示告訴他原因。
     */
    static void check(final Activity a, final boolean manual) {
        final SharedPreferences sp = a.getSharedPreferences("cfg", Context.MODE_PRIVATE);
        if (!manual && !sp.getBoolean("ann_on", true)) return;
        long now = System.currentTimeMillis();
        if (!manual && now - sp.getLong(PREF_LAST, 0) < MIN_INTERVAL_MS) return;
        sp.edit().putLong(PREF_LAST, now).apply();
        new Thread(() -> {
            final Result r;
            try {
                String json = fetch();
                if (json == null) {
                    LifeLog.add(a, "公告：抓不到（網路或檔案不存在）");
                    if (manual) a.runOnUiThread(() -> android.widget.Toast.makeText(a, L.t("抓不到公告（網路不通，或檔案不存在）"), android.widget.Toast.LENGTH_LONG).show());
                    return;
                }
                long vc = a.getPackageManager().getPackageInfo(a.getPackageName(), 0).getLongVersionCode();
                Set<String> seen = manual ? new HashSet<String>() : new HashSet<>(sp.getStringSet(PREF_SEEN, new HashSet<>()));
                r = parse(json, vc, System.currentTimeMillis(), !L.en(), seen);
                if (r == null) {
                    LifeLog.add(a, "公告：檔案格式看不懂");
                    if (manual) a.runOnUiThread(() -> android.widget.Toast.makeText(a, L.t("公告檔案格式看不懂"), android.widget.Toast.LENGTH_LONG).show());
                    return;
                }
                LifeLog.add(a, "公告：抓到（id=" + r.id + "，" + (r.show ? "要顯示" : "不顯示") + "，停用 " + r.disable + "）");
                sp.edit().putString(PREF_FLAGS, String.join(",", r.disable)).putLong(PREF_FLAGS_AT, System.currentTimeMillis()).apply();   // 成功抓到：開關以這次為準（檔案沒寫就是全部恢復）
            } catch (Exception e) {
                LifeLog.add(a, "公告：出錯 " + e);
                if (manual) a.runOnUiThread(() -> android.widget.Toast.makeText(a, L.t("抓不到公告（網路不通，或檔案不存在）"), android.widget.Toast.LENGTH_LONG).show());
                return;   // 沒網路、檔案不存在：靜靜地略過
            }
            if (!r.show) {
                if (manual) a.runOnUiThread(() -> android.widget.Toast.makeText(a, L.t("目前沒有公告"), android.widget.Toast.LENGTH_SHORT).show());
                return;
            }
            a.runOnUiThread(() -> show(a, r));
        }, "announcements").start();
    }

    private static String fetch() throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL("https://raw.githubusercontent.com/" + BuildConfig.REPO + "/main/announcement.json").openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", "Vertex");
        if (c.getResponseCode() != 200) return null;
        try (java.io.InputStream in = c.getInputStream()) {
            java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[2048];
            int n;
            while ((n = in.read(b)) > 0) {
                if (o.size() + n > MAX_BYTES) return null;   // 太大不是正常公告
                o.write(b, 0, n);
            }
            return o.toString("UTF-8");
        }
    }

    private static void show(final Activity a, final Result r) {
        if (a.isFinishing() || a.isDestroyed()) return;
        com.google.android.material.dialog.MaterialAlertDialogBuilder b = new com.google.android.material.dialog.MaterialAlertDialogBuilder(a)
                .setTitle((r.severity.equals("critical") ? "⚠ " : "") + (r.title.isEmpty() ? L.t("公告") : r.title))
                .setMessage(r.body)
                .setPositiveButton(L.t("知道了"), null);
        if (r.offerUpdate) b.setNeutralButton(L.t("前往更新"), (d, w) -> Updater.start(a));   // 只用 App 內建的更新流程，不開檔案裡寫的網址
        b.setOnDismissListener(d -> markSeen(a, r.id));
        b.show();
    }

    private static void markSeen(Context c, String id) {
        SharedPreferences sp = c.getSharedPreferences("cfg", Context.MODE_PRIVATE);
        Set<String> seen = new HashSet<>(sp.getStringSet(PREF_SEEN, new HashSet<>()));
        seen.add(id);
        sp.edit().putStringSet(PREF_SEEN, seen).apply();
    }
}
