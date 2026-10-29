package com.phoneai.overlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;

/**
 * 無障礙服務被系統關掉時自動修復。使用者回報：「無障礙權限會自己被關掉」（原因還不確定：可能是 App 更新／重裝、服務當掉、或系統清理；
 * 這裡先做兩件事：①發現沒開就用 root 重新打開，保留使用者其他的無障礙服務；②記下修了幾次、最近一次的時間（主畫面顯示），之後才能判斷多久會掉一次）。
 * 只動我們自己的服務，不動別人的；主畫面有開關（預設開）。沒有 root 或寫入失敗就算了，不影響其他功能。
 * 只在 App 自己有東西在跑的時候檢查（開主畫面、開面板、OverlayService 收到指令），不另外常駐輪詢。
 */
final class A11yGuard {
    static final String COMPONENT = "com.phoneai.overlay/com.phoneai.overlay.RippleAccessibilityService";
    private static volatile long lastRun;

    private A11yGuard() { }

    static boolean enabled(Context c) {
        return c.getSharedPreferences("cfg", Context.MODE_PRIVATE).getBoolean("a11y_guard", true);
    }

    /** 我們的無障礙服務有沒有在系統的啟用清單裡。 */
    static boolean listed(Context c) {
        String cur = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return cur != null && (":" + cur + ":").contains(":" + COMPONENT + ":");
    }

    /** 沒開就用 root 補回去（在背景執行緒做）。10 秒內重複呼叫只做一次。 */
    static void ensure(Context c) {
        if (!enabled(c)) return;
        long now = System.currentTimeMillis();
        if (now - lastRun < 10000) return;
        lastRun = now;
        if (listed(c)) return;
        final Context app = c.getApplicationContext();
        final String cur = Settings.Secure.getString(app.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        new Thread(() -> {
            if (!Root.isRoot()) return;   // 沒有 root 補不回去，使用者要自己到系統設定重開
            String list = (cur == null || cur.isEmpty() || "null".equals(cur)) ? COMPONENT : cur + ":" + COMPONENT;   // 保留別的服務
            try {
                Process p = new ProcessBuilder(Root.su(), "-c",
                        "settings put secure enabled_accessibility_services '" + list + "'; settings put secure accessibility_enabled 1")
                        .redirectErrorStream(true).start();
                p.getInputStream().readAllBytes();
                int rc = p.waitFor();
                boolean ok = rc == 0 && listed(app);
                Log.w(OverlayService.TAG, L.t("a11y 自動修復 rc=") + rc + " ok=" + ok);
                if (ok) {
                    SharedPreferences sp = app.getSharedPreferences("cfg", Context.MODE_PRIVATE);
                    sp.edit().putInt("a11y_fixes", sp.getInt("a11y_fixes", 0) + 1)
                            .putString("a11y_last", new java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.US).format(new java.util.Date())).apply();
                }
            } catch (Exception e) {
                Log.w(OverlayService.TAG, L.t("a11y 自動修復失敗: ") + e);
            }
        }, "a11y-guard").start();
    }
}
