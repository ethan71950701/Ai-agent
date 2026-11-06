package com.phoneai.overlay;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

/** 診斷用的事件紀錄：最近 30 件事（離開／回來、背景多久、系統通知回收記憶體、螢幕開關、重建玻璃…），存在設定檔，程序被殺掉也留著。 */
final class LifeLog {
    private LifeLog() { }

    static synchronized void add(Context c, String msg) {
        try {
            SharedPreferences sp = c.getSharedPreferences("cfg", Context.MODE_PRIVATE);
            JSONArray old;
            try { old = new JSONArray(sp.getString("life_log", "[]")); } catch (Exception e) { old = new JSONArray(); }
            JSONArray now = new JSONArray();
            for (int k = Math.max(0, old.length() - 29); k < old.length(); k++) now.put(old.opt(k));
            now.put(new java.text.SimpleDateFormat("MM/dd HH:mm:ss", java.util.Locale.US).format(new java.util.Date()) + " " + msg);
            sp.edit().putString("life_log", now.toString()).apply();
        } catch (Exception ignored) { }
    }

    static String read(Context c) {
        try {
            JSONArray a = new JSONArray(c.getSharedPreferences("cfg", Context.MODE_PRIVATE).getString("life_log", "[]"));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < a.length(); i++) sb.append(a.optString(i)).append('\n');
            return sb.length() == 0 ? "（還沒有紀錄）" : sb.toString().trim();
        } catch (Exception e) {
            return "（讀不到紀錄）";
        }
    }
}
