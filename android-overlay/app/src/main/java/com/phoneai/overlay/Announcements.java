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
    private static final String PREF_FLAGS = "remote_disabled", PREF_FLAGS_AT = "remote_disabled_at", PREF_MUTED = "ann_muted", PREF_HIST = "ann_hist", PREF_CACHE = "ann_cache", PREF_LAST = "ann_last";

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

    /** 打開 App 時呼叫（主畫面 onResume）。 */
    static void checkOnOpen(final Activity a) { RemoteTuning.refreshIfDue(a); check(a, false); }

    /** 這次執行中已經彈過的公告 id：回到首頁（onResume）不會一直重複彈，要「下次打開 App」才會再彈。 */
    private static final Set<String> SHOWN_THIS_RUN = new HashSet<>();

    /**
     * 規則：公告只要還在檔案裡、版本符合、你沒勾「不要再提醒」，**每次打開 App 都會彈出**（不是只彈一次；手滑關掉也沒關係）。
     * 抓檔案最多每 10 分鐘一次，其他時候用上次存下來的內容。manual＝在設定按「立即看公告」：馬上抓、忽略「不要再提醒」，沒有或抓不到會告訴你原因。
     */
    static void check(final Activity a, final boolean manual) {
        final SharedPreferences sp = a.getSharedPreferences("cfg", Context.MODE_PRIVATE);
        if (!manual && !sp.getBoolean("ann_on", true)) return;
        long now = System.currentTimeMillis();
        final boolean fresh = manual || now - sp.getLong(PREF_LAST, 0) >= MIN_INTERVAL_MS;
        if (fresh) sp.edit().putLong(PREF_LAST, now).apply();
        new Thread(() -> {
            String json;
            try {
                json = fresh ? fetch() : sp.getString(PREF_CACHE, "");
                if (json == null) {
                    LifeLog.add(a, "公告：抓不到（網路或檔案不存在）");
                    if (manual) toast(a, L.t("抓不到公告（網路不通，或檔案不存在）"), true);
                    json = sp.getString(PREF_CACHE, "");   // 抓不到就用上次存的（有的話）
                } else if (fresh) {
                    sp.edit().putString(PREF_CACHE, json).apply();
                }
            } catch (Exception e) {
                LifeLog.add(a, "公告：出錯 " + e);
                if (manual) toast(a, L.t("抓不到公告（網路不通，或檔案不存在）"), true);
                json = sp.getString(PREF_CACHE, "");
            }
            if (json == null || json.isEmpty()) return;
            final Result r;
            try {
                long vc = a.getPackageManager().getPackageInfo(a.getPackageName(), 0).getLongVersionCode();
                Set<String> muted = manual ? new HashSet<String>() : new HashSet<>(sp.getStringSet(PREF_MUTED, new HashSet<>()));
                r = parse(json, vc, System.currentTimeMillis(), !L.en(), muted);
            } catch (Exception e) { return; }
            if (r == null) {
                LifeLog.add(a, "公告：檔案格式看不懂");
                if (manual) toast(a, L.t("公告檔案格式看不懂"), true);
                return;
            }
            if (fresh) {
                LifeLog.add(a, "公告：抓到（id=" + r.id + "，" + (r.show ? "要顯示" : "不顯示") + "，停用 " + r.disable + "）");
                sp.edit().putString(PREF_FLAGS, String.join(",", r.disable)).putLong(PREF_FLAGS_AT, System.currentTimeMillis()).apply();   // 成功抓到：開關以這次為準（檔案沒寫就是全部恢復）
            }
            if (!r.id.isEmpty() && (!r.title.isEmpty() || !r.body.isEmpty())) saveHistory(a, r);   // 收到的公告存起來，「公告」頁看得到
            if (!r.show) {
                if (manual) toast(a, L.t("目前沒有公告"), false);
                return;
            }
            synchronized (SHOWN_THIS_RUN) {
                if (!manual && !SHOWN_THIS_RUN.add(r.id)) return;
            }
            a.runOnUiThread(() -> show(a, r));
        }, "announcements").start();
    }

    private static void toast(final Activity a, final String s, final boolean longer) {
        a.runOnUiThread(() -> android.widget.Toast.makeText(a, s, longer ? android.widget.Toast.LENGTH_LONG : android.widget.Toast.LENGTH_SHORT).show());
    }

    private static String fetch() throws Exception { return fetch("announcement.json"); }

    /** 從 repo main 抓一個小檔案（固定的 https 網址、最多 20 KB）。 */
    static String fetch(String file) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL("https://raw.githubusercontent.com/" + BuildConfig.REPO + "/main/" + file).openConnection();
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
                .setMessage(r.body);
        final android.widget.CheckBox mute = new android.widget.CheckBox(a);
        if (!r.repeat) {   // 重大公告而且你的版本還太舊：不能選「不要再提醒」
            mute.setText(L.t("不要再提醒這則公告"));
            android.widget.FrameLayout box = new android.widget.FrameLayout(a);
            int p = (int) (22 * a.getResources().getDisplayMetrics().density);
            box.setPadding(p, 0, p, 0);
            box.addView(mute);
            b.setView(box);
        }
        b.setPositiveButton(L.t("知道了"), (d, w) -> { if (mute.isChecked()) mute(a, r.id, true); });
        if (r.offerUpdate) b.setNeutralButton(L.t("前往更新"), (d, w) -> Updater.start(a));   // 只用 App 內建的更新流程，不開檔案裡寫的網址
        b.show();
    }

    // ---------- 不要再提醒、歷史紀錄 ----------

    static Set<String> mutedIds(Context c) { return new HashSet<>(c.getSharedPreferences("cfg", Context.MODE_PRIVATE).getStringSet(PREF_MUTED, new HashSet<>())); }

    static void mute(Context c, String id, boolean on) {
        SharedPreferences sp = c.getSharedPreferences("cfg", Context.MODE_PRIVATE);
        Set<String> m = mutedIds(c);
        if (on) m.add(id); else m.remove(id);
        sp.edit().putStringSet(PREF_MUTED, m).apply();
    }

    /** 歷史紀錄的一筆。 */
    static final class Entry {
        String id, severity, title, body;
        long at;
    }

    private static final int HISTORY_CAP = 30;

    /** 純邏輯：把一則公告放到歷史最前面（同 id 的舊紀錄換掉），最多留 cap 筆。可單元測試。 */
    static String mergeHistory(String histJson, String id, String sev, String title, String body, long now, int cap) {
        try {
            JSONArray old;
            try { old = histJson == null || histJson.isEmpty() ? new JSONArray() : new JSONArray(histJson); } catch (Exception bad) { old = new JSONArray(); }   // 舊紀錄壞掉：從頭開始，不要把壞資料留著
            JSONArray out = new JSONArray();
            out.put(new JSONObject().put("id", id).put("sev", sev).put("title", title).put("body", body).put("at", now));
            for (int i = 0; i < old.length() && out.length() < cap; i++) {
                JSONObject o = old.optJSONObject(i);
                if (o != null && !id.equals(o.optString("id"))) out.put(o);
            }
            return out.toString();
        } catch (Exception e) {
            return histJson == null ? "[]" : histJson;
        }
    }

    private static void saveHistory(Context c, Result r) {
        SharedPreferences sp = c.getSharedPreferences("cfg", Context.MODE_PRIVATE);
        JSONArray cur;
        try { cur = new JSONArray(sp.getString(PREF_HIST, "[]")); } catch (Exception e) { cur = new JSONArray(); }
        long at = System.currentTimeMillis();
        for (int i = 0; i < cur.length(); i++) {   // 同一則已經存過就保留第一次收到的時間，不要每次打開都往前跳
            JSONObject o = cur.optJSONObject(i);
            if (o != null && r.id.equals(o.optString("id"))) { at = o.optLong("at", at); break; }
        }
        sp.edit().putString(PREF_HIST, mergeHistory(sp.getString(PREF_HIST, "[]"), r.id, r.severity, r.title, r.body, at, HISTORY_CAP)).apply();
    }

    static java.util.List<Entry> history(Context c) {
        java.util.List<Entry> out = new java.util.ArrayList<>();
        try {
            JSONArray a = new JSONArray(c.getSharedPreferences("cfg", Context.MODE_PRIVATE).getString(PREF_HIST, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                Entry e = new Entry();
                e.id = o.optString("id");
                e.severity = o.optString("sev", "info");
                e.title = o.optString("title");
                e.body = o.optString("body");
                e.at = o.optLong("at");
                out.add(e);
            }
        } catch (Exception ignored) { }
        return out;
    }

    static void deleteHistory(Context c, String id) {
        SharedPreferences sp = c.getSharedPreferences("cfg", Context.MODE_PRIVATE);
        try {
            JSONArray a = new JSONArray(sp.getString(PREF_HIST, "[]")), out = new JSONArray();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o != null && !id.equals(o.optString("id"))) out.put(o);
            }
            sp.edit().putString(PREF_HIST, out.toString()).apply();
        } catch (Exception ignored) { }
    }
}
