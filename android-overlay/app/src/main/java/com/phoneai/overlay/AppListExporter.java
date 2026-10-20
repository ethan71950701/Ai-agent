package com.phoneai.overlay;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 讀出桌面上有圖示的 App「顯示名稱<TAB>套件名稱」。Termux 讀不到別的 App 的顯示名稱，所以由本 App 用 PackageManager 讀，
 * Termux 端（applist.py）經由 OverlayService 的連線（cmd "apps"）取得，再自己存成 ~/apps.tsv。
 * （早先版本由 App 用 root 寫 Termux 家目錄，實測沒寫出檔案、原因未查明，已移除。）
 */
public final class AppListExporter {
    private AppListExporter() { }

    static String build(Context c) {
        PackageManager pm = c.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(main, 0);
        Set<String> seen = new LinkedHashSet<>();
        StringBuilder sb = new StringBuilder();
        for (ResolveInfo ri : list) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(c.getPackageName()) || !seen.add(pkg)) continue;
            String label = String.valueOf(ri.loadLabel(pm)).replaceAll("[\\t\\r\\n]+", " ").trim();
            if (label.isEmpty()) continue;
            sb.append(label).append('\t').append(pkg).append('\n');
        }
        return sb.toString();
    }
}
