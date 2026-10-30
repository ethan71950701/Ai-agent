package com.phoneai.overlay;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 有桌面圖示的 App 清單（套件名稱＋顯示名稱）。讀名稱（loadLabel）很慢，所以讀過的存起來（記憶體＋app_labels.json）：
 * 下次只讀新裝的 App，已經讀過的直接用；語言改了才全部重讀。
 */
final class AppLabels {
    private AppLabels() { }

    private static volatile Map<String, String> memory;
    private static volatile String memoryLocale = "";

    /** 依顯示名稱排序的 [套件, 名稱]（不含自己）。在背景執行緒呼叫。 */
    static synchronized List<String[]> list(Context c) {
        PackageManager pm = c.getPackageManager();
        String locale = c.getResources().getConfiguration().getLocales().toLanguageTags();
        Map<String, String> known = known(c, locale);
        Map<String, String> now = new HashMap<>();
        boolean added = false;
        for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)) {   // 這個查詢很快，慢的是讀名稱
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(c.getPackageName()) || now.containsKey(pkg)) continue;
            String label = known.get(pkg);
            if (label == null) { label = String.valueOf(ri.loadLabel(pm)); added = true; }
            now.put(pkg, label);
        }
        boolean removed = now.size() != known.size() || !known.keySet().containsAll(now.keySet());
        memory = now;
        memoryLocale = locale;
        if (added || removed) save(c, locale, now);
        TreeMap<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, String> e : now.entrySet()) sorted.put(e.getValue().toLowerCase(java.util.Locale.ROOT) + "\u0000" + e.getKey(), e.getKey());
        List<String[]> out = new ArrayList<>();
        for (String pkg : sorted.values()) out.add(new String[]{pkg, now.get(pkg)});
        return out;
    }

    private static Map<String, String> known(Context c, String locale) {
        Map<String, String> m = memory;
        if (m != null && locale.equals(memoryLocale)) return m;
        try {
            JSONObject d = new JSONObject(new String(Files.readAllBytes(file(c).toPath()), StandardCharsets.UTF_8));
            if (!locale.equals(d.optString("locale"))) return new HashMap<>();   // 語言變了：名稱要重讀
            JSONObject labels = d.getJSONObject("labels");
            Map<String, String> out = new HashMap<>();
            java.util.Iterator<String> it = labels.keys();
            while (it.hasNext()) { String k = it.next(); out.put(k, labels.getString(k)); }
            return out;
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private static File file(Context c) { return new File(c.getCacheDir(), "app_labels.json"); }

    private static void save(Context c, String locale, Map<String, String> labels) {
        try {
            Files.write(file(c).toPath(), new JSONObject().put("locale", locale).put("labels", new JSONObject(labels)).toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) { }   // 存不起來只是下次要重讀
    }
}
