package com.phoneai.overlay;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 底欄玻璃的參數與開關（設定 → 底欄玻璃（進階））。數值存在 cfg 裡，App 啟動時載入到靜態欄位，
 * 玻璃每一格直接讀這些欄位，所以拖動滑桿會即時生效。
 */
final class GlassPrefs {
    private GlassPrefs() { }

    static float refr = 0.6f, chroma = 0.5f, zoom = 0.4f, skew = 1f, blur = 0f;   // 預設：折射較輕；色散只給上層透鏡（底欄不會有彩色光邊）
    static boolean magnify = true, iconRefr = true, lensBg = true, skewOn = true;   // 圖示折射預設開啟

    static final String K_REFR = "g_refr", K_CHROMA = "g_chroma", K_ZOOM = "g_zoom", K_SKEW = "g_skew", K_BLUR = "g_blur";
    static final String K_MAGNIFY = "g_magnify", K_ICON_REFR = "g_iconRefr", K_LENS_BG = "g_lensBg", K_SKEW_ON = "g_skewOn";

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("cfg", Context.MODE_PRIVATE);
    }

    /** 從 cfg 載入到靜態欄位（App 啟動時、以及改設定後呼叫）。 */
    static void load(Context c) {
        SharedPreferences p = sp(c);
        refr = p.getFloat(K_REFR, 0.6f);
        chroma = p.getFloat(K_CHROMA, 0.5f);
        zoom = p.getFloat(K_ZOOM, 0.4f);
        skew = p.getFloat(K_SKEW, 1f);
        blur = p.getFloat(K_BLUR, 0f);
        magnify = p.getBoolean(K_MAGNIFY, true);
        iconRefr = p.getBoolean(K_ICON_REFR, true);
        lensBg = p.getBoolean(K_LENS_BG, true);
        skewOn = p.getBoolean(K_SKEW_ON, true);
    }

    static void putFloat(Context c, String key, float v) {
        sp(c).edit().putFloat(key, v).apply();
        load(c);
    }

    static void putBool(Context c, String key, boolean v) {
        sp(c).edit().putBoolean(key, v).apply();
        load(c);
    }

    /** 全部恢復預設。 */
    static void reset(Context c) {
        sp(c).edit().remove(K_REFR).remove(K_CHROMA).remove(K_ZOOM).remove(K_SKEW).remove(K_BLUR)
                .remove(K_MAGNIFY).remove(K_ICON_REFR).remove(K_LENS_BG).remove(K_SKEW_ON).apply();
        load(c);
    }
}
