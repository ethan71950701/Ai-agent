package com.phoneai.overlay;

import android.content.Context;

import java.util.HashMap;
import java.util.Locale;

/**
 * 介面語言：程式裡的字串原文是繁體中文，L.t("中文") 在英文模式下回傳對應的英文（字典在 LEn）；沒有對應就原樣回傳。
 * 語言選項（設定 → 外觀 → 語言）：auto＝跟隨系統（系統語言是中文就用中文，其他語言用英文）、zh＝繁體中文、en＝English。
 */
final class L {
    static final String PREF = "lang";
    private static volatile boolean en;
    private static final HashMap<String, String> EN = new HashMap<>(1024);

    static {
        LEn.fill(EN);
    }

    private L() { }

    /** App 啟動時、改語言後呼叫。 */
    static void init(Context c) {
        String v = pref(c);
        if ("zh".equals(v)) en = false;
        else if ("en".equals(v)) en = true;
        else en = !"zh".equals(Locale.getDefault().getLanguage());
    }

    static String pref(Context c) {
        return c.getSharedPreferences("cfg", Context.MODE_PRIVATE).getString(PREF, "auto");
    }

    static void setPref(Context c, String v) {
        c.getSharedPreferences("cfg", Context.MODE_PRIVATE).edit().putString(PREF, v).apply();
        init(c);
    }

    static boolean en() {
        return en;
    }

    /** 中文原文 → 目前語言的文字。 */
    static String t(String zh) {
        if (!en || zh == null) return zh;
        String v = EN.get(zh);
        return v != null ? v : zh;
    }
}
