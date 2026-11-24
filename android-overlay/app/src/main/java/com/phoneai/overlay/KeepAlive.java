package com.phoneai.overlay;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 保活檢查：定時自動操作、傳檔、通知整理都靠 App 在背景活著。很多手機（小米、華為、OPPO、vivo、三星…）會把背景 App 殺掉，
 * 這裡列出能查得到的狀態，並一鍵跳到各家系統「自啟動／電池」的設定頁（系統不讓 App 查自啟動有沒有開，只能帶你去看）。
 */
final class KeepAlive {
    private KeepAlive() { }

    static boolean batteryIgnored(Context c) {
        try { return ((PowerManager) c.getSystemService(Context.POWER_SERVICE)).isIgnoringBatteryOptimizations(c.getPackageName()); }
        catch (Throwable t) { return false; }
    }

    static String brand() { return Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase(java.util.Locale.ROOT); }

    /** 各家「自啟動／後台管理」頁面（依廠商）；沒有對應的就回 null。純邏輯，可單元測試。 */
    static String[] autostartComponent(String manufacturer) {
        String m = manufacturer == null ? "" : manufacturer.toLowerCase(java.util.Locale.ROOT);
        if (m.contains("xiaomi") || m.contains("redmi") || m.contains("poco")) return new String[]{"com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"};
        if (m.contains("huawei")) return new String[]{"com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"};
        if (m.contains("honor")) return new String[]{"com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"};
        if (m.contains("oppo") || m.contains("realme") || m.contains("oneplus")) return new String[]{"com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"};
        if (m.contains("vivo") || m.contains("iqoo")) return new String[]{"com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"};
        return null;
    }

    /** 跳到這個廠商的自啟動頁；跳不過去（版本改名了）就改開 App 資訊頁。回傳說明文字。 */
    static String openAutostart(Activity a) {
        String[] comp = autostartComponent(Build.MANUFACTURER);
        if (comp != null) {
            try {
                a.startActivity(new Intent().setComponent(new ComponentName(comp[0], comp[1])).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return L.t("在打開的頁面找到 Vertex，開啟「自啟動」與「後台運行」");
            } catch (Throwable ignored) { }
        }
        try {
            a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.getPackageName())));
            return L.t("這支手機找不到專用的自啟動頁；在 App 資訊裡把「電池」設成「不受限制」，並允許後台運行");
        } catch (Throwable t) { return L.t("打不開設定頁"); }
    }

    static void openBattery(Activity a) {
        try { a.startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); }
        catch (Throwable t) { openAutostart(a); }
    }

    /** 檢查頁：狀態（已知的）＋三顆按鈕。 */
    static void open(final Activity a) {
        LinearLayout form = new LinearLayout(a);
        form.setOrientation(LinearLayout.VERTICAL);
        int fp = Ui.dp(a, 20);
        form.setPadding(fp, fp / 2, fp, 0);
        form.addView(Ui.body(a, L.t("定時自動操作、傳檔、通知整理都要 App 在背景活著。很多手機會把背景 App 殺掉，下面是能查到的狀態，和一鍵跳到系統設定的按鈕。")));
        form.addView(line(a, batteryIgnored(a), L.t("電池：不受限制（系統不會因為省電殺掉它）"), L.t("電池：受限制，容易被系統殺掉 → 按下面的「電池設定」改成不受限制")));
        form.addView(line(a, RippleAccessibilityService.instance != null, L.t("無障礙服務：已開啟"), L.t("無障礙服務：沒有開 → 自動操作手機要用它，到系統設定開啟 Vertex")));
        boolean notif = Build.VERSION.SDK_INT < 33 || a.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        form.addView(line(a, notif, L.t("通知：已允許"), L.t("通知：沒有允許 → 定時結果、更新提醒都收不到")));
        TextView auto = Ui.body(a, String.format(L.t("自啟動／後台運行：系統不讓 App 查詢。這支手機是 %s，請按下面的「自啟動設定」自己確認已開啟。"), Build.MANUFACTURER));
        auto.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 8));
        form.addView(auto);
        form.addView(btn(a, L.t("電池設定"), v -> openBattery(a)));
        form.addView(btn(a, L.t("自啟動設定"), v -> android.widget.Toast.makeText(a, openAutostart(a), android.widget.Toast.LENGTH_LONG).show()));
        form.addView(btn(a, L.t("App 資訊"), v -> {
            try { a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.getPackageName()))); } catch (Throwable ignored) { }
        }));
        new PageBuilder(a).setTitle(L.t("保活檢查")).setView(form).show();
    }

    private static TextView line(Activity a, boolean ok, String good, String bad) {
        TextView t = Ui.body(a, (ok ? "✓ " : "✗ ") + (ok ? good : bad));
        t.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 6));
        return t;
    }

    private static android.view.View btn(Activity a, String text, android.view.View.OnClickListener l) {
        com.google.android.material.button.MaterialButton b = Ui.pill(a, text, false, l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Ui.dp(a, 52));
        lp.topMargin = Ui.dp(a, 8);
        b.setLayoutParams(lp);
        return b;
    }
}
