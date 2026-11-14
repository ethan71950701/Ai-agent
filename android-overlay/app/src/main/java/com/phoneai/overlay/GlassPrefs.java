package com.phoneai.overlay;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 底欄玻璃的參數與開關（設定 → 底欄玻璃（進階））。數值存在 cfg 裡，App 啟動時載入到靜態欄位，
 * 玻璃每一格直接讀這些欄位，所以拖動滑桿會即時生效。
 */
final class GlassPrefs {
    private GlassPrefs() { }

    static float pressX = 1.2f, pressY = 1.4f, barPress = 1.06f;   // 按住時透鏡的橫向／直向放大倍數、整條底欄的放大倍數
    static float stretch = 2f;   // 拖動形變強度（乘在加速度形變上）
    static float refr = 0.56f, chroma = 0.30f, zoom = 0.08f, skew = 0f, blur = 0f;   // 預設：折射較輕；色散只給上層透鏡（底欄不會有彩色光邊）
    static boolean magnify = true, iconRefr = true, skewOn = true, gpu = true;   // 圖示折射預設開啟
    static boolean vivid = true;   // 玻璃顏色增強：拉高飽和度、暗背景提亮、黑底變淡（蓋住的地方顏色跟沒蓋住的不一樣）
    static float barDark = 0.66f;   // 底欄的暗度（0 = 幾乎透明，1 = 很暗）；預設跟之前的亮度一樣

    static final String K_PRESS_X = "g_pressX", K_PRESS_Y = "g_pressY", K_BAR_PRESS = "g_barPress";
    static final String K_REFR = "g_refr", K_CHROMA = "g_chroma", K_ZOOM = "g_zoom", K_SKEW = "g_skew", K_BLUR = "g_blur";
    static final String K_MAGNIFY = "g_magnify", K_ICON_REFR = "g_iconRefr", K_SKEW_ON = "g_skewOn", K_GPU = "g_gpu";
    static final String K_BAR_DARK = "g_barDark", K_STRETCH = "g_stretch", K_VIVID = "g_vivid";

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("cfg", Context.MODE_PRIVATE);
    }

    /** 從 cfg 載入到靜態欄位（App 啟動時、以及改設定後呼叫）。 */
    static void load(Context c) {
        SharedPreferences p = sp(c);
        refr = p.getFloat(K_REFR, 0.56f);
        chroma = p.getFloat(K_CHROMA, 0.30f);
        zoom = p.getFloat(K_ZOOM, 0.08f);
        pressX = p.getFloat(K_PRESS_X, 1.2f);
        pressY = p.getFloat(K_PRESS_Y, 1.4f);
        barPress = p.getFloat(K_BAR_PRESS, 1.06f);
        skew = p.getFloat(K_SKEW, 0f);
        blur = p.getFloat(K_BLUR, 0f);
        magnify = p.getBoolean(K_MAGNIFY, true);
        iconRefr = p.getBoolean(K_ICON_REFR, true);
        skewOn = p.getBoolean(K_SKEW_ON, true);
        gpu = p.getBoolean(K_GPU, true);
        vivid = p.getBoolean(K_VIVID, true);
        barDark = p.getFloat(K_BAR_DARK, 0.66f);
        stretch = p.getFloat(K_STRETCH, 2f);
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
        sp(c).edit().remove(K_REFR).remove(K_CHROMA).remove(K_ZOOM).remove(K_PRESS_X).remove(K_PRESS_Y).remove(K_BAR_PRESS).remove(K_SKEW).remove(K_BLUR)
                .remove(K_MAGNIFY).remove(K_ICON_REFR).remove(K_SKEW_ON).remove(K_GPU).remove(K_VIVID).remove(K_BAR_DARK).remove(K_STRETCH).apply();
        load(c);
    }
}
