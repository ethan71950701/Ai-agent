package com.phoneai.overlay;

import android.content.Context;

/**
 * 執行模式（引導頁第一頁選）：
 * ROOT＝用 su 執行指令（最完整）；SHIZUKU＝透過 Shizuku 用 adb 等級權限執行指令（不用 root）；NORMAL＝只用無障礙與系統 API。
 * 沒選過的舊使用者：以前勾了「不使用 root」就是 NORMAL，其他維持 ROOT（沒 root 時會自動退成 NORMAL 的做法）。
 */
final class ExecMode {
    private ExecMode() { }

    static final String ROOT = "root", SHIZUKU = "shizuku", NORMAL = "normal";

    static String get(Context c) {
        android.content.SharedPreferences p = c.getSharedPreferences("cfg", Context.MODE_PRIVATE);
        String m = p.getString("exec_mode", null);
        if (m != null) return m;
        return p.getBoolean("no_root", false) ? NORMAL : ROOT;
    }

    static boolean chosen(Context c) { return c.getSharedPreferences("cfg", Context.MODE_PRIVATE).contains("exec_mode"); }

    static void set(Context c, String mode) {
        c.getSharedPreferences("cfg", Context.MODE_PRIVATE).edit().putString("exec_mode", mode).apply();
        Root.recheck();
    }
}
