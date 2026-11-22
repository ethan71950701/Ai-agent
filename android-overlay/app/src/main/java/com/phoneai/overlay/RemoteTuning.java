package com.phoneai.overlay;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 遠端調參數：只改數值（例如回答卡片玻璃的模糊、暗度）時，不用發新版 App、不用下載安裝。
 * 打開 App 時（最多每 10 分鐘）去 repo 抓一個小檔案 tuning.json，裡面只放「白名單裡的數值」；
 * 沒有網路、檔案壞掉、數值超出範圍一律用程式內建的預設值。不傳送任何使用者資料，只認數字，不執行任何東西。
 * 格式：{"g3_blur": 8, "g3_dark": 1.2}。設定 → 外觀可以關掉。
 */
final class RemoteTuning {
    private RemoteTuning() { }

    /** 白名單：名稱 → {最小, 最大}。超出範圍的值會被夾回範圍內；沒列在這裡的名稱一律忽略。 */
    static final Map<String, float[]> ALLOWED = new HashMap<>();
    static {
        // 回答卡片「液態玻璃」那一檔
        ALLOWED.put("g3_blur", new float[]{0f, 40f});       // 後面畫面的模糊（dp）
        ALLOWED.put("g3_dark", new float[]{0f, 2f});        // 暗度倍率（1 = 跟底欄一樣）
        ALLOWED.put("g3_rim", new float[]{0f, 1f});         // 邊緣細亮線強度
        ALLOWED.put("g3_elev", new float[]{0f, 30f});       // 外陰影高度（dp）
        ALLOWED.put("g3_ambient", new float[]{0f, 1f});     // 環境陰影濃度
    }

    private static final long MIN_INTERVAL_MS = 10 * 60_000L;
    private static final String PREF_JSON = "tune_json", PREF_LAST = "tune_last", PREF_ON = "tune_on";
    private static volatile Map<String, Float> cache;   // 解析過的值（改檔案後換新）

    /** 純邏輯，可單元測試：只留白名單裡的數字，夾進範圍；壞掉的 JSON 回空表。 */
    static Map<String, Float> parse(String json) {
        Map<String, Float> out = new HashMap<>();
        if (json == null || json.length() > 4096) return out;
        try {
            JSONObject o = new JSONObject(json);
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                float[] r = ALLOWED.get(k);
                Object v = o.opt(k);
                if (r == null || !(v instanceof Number)) continue;
                float f = ((Number) v).floatValue();
                if (Float.isNaN(f) || Float.isInfinite(f)) continue;
                out.put(k, Math.max(r[0], Math.min(r[1], f)));
            }
        } catch (Exception ignored) { }
        return out;
    }

    static boolean enabled(Context c) { return c.getSharedPreferences("cfg", Context.MODE_PRIVATE).getBoolean(PREF_ON, true); }

    /** 這個參數現在的值：遠端有給（而且開關開著）就用遠端的，否則用 def。 */
    static float f(Context c, String key, float def) {
        try {
            if (!enabled(c)) return def;
            Map<String, Float> m = cache;
            if (m == null) {
                m = parse(c.getSharedPreferences("cfg", Context.MODE_PRIVATE).getString(PREF_JSON, ""));
                cache = m;
            }
            Float v = m.get(key);
            return v == null ? def : v;
        } catch (Throwable t) { return def; }
    }

    /** 打開 App 時呼叫：最多每 10 分鐘在背景抓一次。 */
    static void refreshIfDue(final Context c) {
        SharedPreferences sp = c.getSharedPreferences("cfg", Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (!enabled(c) || now - sp.getLong(PREF_LAST, 0) < MIN_INTERVAL_MS) return;
        sp.edit().putLong(PREF_LAST, now).apply();
        new Thread(() -> refreshNow(c), "remote-tuning").start();
    }

    /** 馬上抓（在背景執行緒呼叫）。成功回 true；抓不到或格式壞掉不動原本的值。 */
    static boolean refreshNow(Context c) {
        try {
            if (!enabled(c)) return false;
            String json = Announcements.fetch("tuning.json");
            if (json == null) return false;
            new JSONObject(json);   // 格式不對就丟例外，不存
            c.getSharedPreferences("cfg", Context.MODE_PRIVATE).edit().putString(PREF_JSON, json).apply();
            cache = parse(json);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
