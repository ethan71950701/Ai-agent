package com.phoneai.overlay;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 進階與診斷：版本、權限、無障礙、最近任務、語音除錯等原始資訊，和幾個實驗／測試入口。 */
public class DiagActivity extends Activity {
    private TextView info;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout[] c = new LinearLayout[1];
        setContentView(Ui.screen(this, L.t("進階與診斷"), true, c));
        Ui.enterSpring(this);
        LinearLayout box = c[0];

        Ui.section(this, box, L.t("測試與工具"));
        Ui.row(this, box, 0, 4, "shield", L.t("修復權限"), L.t("用 root 補回懸浮層、通知、無障礙權限"), Ui.chevron(this), v -> repairPerms());
        Ui.row(this, box, 1, 4, "sparkle", L.t("測試漣漪與通知"), L.t("約 8 秒"), Ui.chevron(this), v -> {
            startForegroundService(new Intent(this, OverlayService.class).setAction(OverlayService.ACTION_TEST));
            info.postDelayed(this::refresh, 1500);
        });
        Ui.row(this, box, 2, 4, "list", L.t("App 清單"), L.t("顯示讀到幾個 App"), Ui.chevron(this), v -> {
            String t = AppListExporter.build(this);
            info.append(L.t("\n讀到 ") + t.split("\n").length + L.t(" 個 App\n"));
        });
        Ui.row(this, box, 3, 4, "refresh", L.t("重新整理狀態"), null, null, v -> refresh());

        Ui.section(this, box, L.t("狀態"));
        info = Ui.body(this, "");
        info.setTextSize(14);
        LinearLayout inner = Ui.card(this, box);
        inner.addView(info);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void repairPerms() {
        android.widget.Toast.makeText(this, L.t("修復中…"), android.widget.Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            final String r = Maintenance.repairPermissions();
            runOnUiThread(() -> { android.widget.Toast.makeText(this, r, android.widget.Toast.LENGTH_LONG).show(); refresh(); });
        }).start();
    }

    /** 系統設定 power_button_long_press：0 = 什麼都不做（電源鍵助理偵測程式才不會被電源選單搶走）、1 = 跳電源選單、其他 = 別的動作（例如叫語音助理）。 */
    private String powerLongPressText() {
        String v = Settings.Global.getString(getContentResolver(), "power_button_long_press");
        if (v == null) return L.t("（沒有設定，用系統預設）");
        switch (v) {
            case "0": return L.t("0（不做事，長按只叫助理）");
            case "1": return L.t("1（會跳電源選單：長按電源鍵時選單可能會先蓋住助理）");
            case "103": return L.t("103（三星原生語音助理：長按電源鍵叫助理，請不要改它）");
            default: return v + L.t("（其他，系統自己的動作）");
        }
    }

    private void refresh() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        StringBuilder sb = new StringBuilder();
        sb.append(L.t("App 版本：1.0.")).append(BuildConfig.VERSION_CODE).append("\n");
        sb.append(L.t("編譯：")).append(getString(R.string.build_id)).append(L.t("（commit · 編譯時間）\n"));
        sb.append(L.t("Android API：")).append(Build.VERSION.SDK_INT).append('\n');
        sb.append(L.t("懸浮層權限：")).append(Settings.canDrawOverlays(this) ? "✓" : "✕").append('\n');
        sb.append(L.t("通知權限：")).append(checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED ? "✓" : "✕").append('\n');
        if (Build.VERSION.SDK_INT >= 36) {
            sb.append(L.t("可發即時通知：")).append(nm.canPostPromotedNotifications() ? "✓" : "✕").append('\n');
        }
        android.content.SharedPreferences cfgp = getSharedPreferences("cfg", MODE_PRIVATE);
        sb.append(L.t("無障礙服務：")).append(A11yGuard.listed(this) ? L.t("✓ 開著") : L.t("✕ 沒開")).append(L.t("；自動修復 "))
                .append(A11yGuard.enabled(this) ? L.t("開") : L.t("關")).append(L.t("；已修復 ")).append(cfgp.getInt("a11y_fixes", 0)).append(L.t(" 次"))
                .append(cfgp.getString("a11y_last", null) != null ? L.t("（最近 ") + cfgp.getString("a11y_last", "") + L.t("）") : "").append('\n');
        sb.append(L.t("系統長按電源鍵動作：")).append(powerLongPressText()).append('\n');
        sb.append(L.t("底欄玻璃效果：")).append(GlassBar.status).append('\n');
        // 小米的小愛同學長按電源鍵可能綁在別的設定上：用 root 列出所有跟電源／長按／助理有關的設定名稱（只讀，不改）
        sb.append(L.t("\n【電源鍵相關設定（只讀）】\n")).append(Maintenance.suRun(
                "for t in global system secure; do echo \"-- $t\"; settings list $t | grep -i 'power\\|press\\|assist\\|xiao\\|voice'; done")).append('\n');
        sb.append(L.t("按鈕透鏡：")).append(GlassBar.lensInfo).append('\n');
        sb.append(L.t("底欄觸控：")).append(GlassBar.touchInfo).append('\n');
        sb.append(L.t("\n最近的任務（含用量估價）：\n"));
        try {
            org.json.JSONArray h = TaskLog.list(this);
            if (h.length() == 0) sb.append(L.t("（還沒有紀錄）\n"));
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.TAIWAN);
            for (int k = 0; k < Math.min(5, h.length()); k++) {
                org.json.JSONObject o = h.getJSONObject(k);
                sb.append(f.format(new java.util.Date(o.getLong("id")))).append("  ").append(o.getString("text")).append("\n");
            }
        } catch (Exception e) { sb.append(L.t("（讀取紀錄失敗）\n")); }
        sb.append(L.t("\n語音辨識除錯（最近）：\n"));
        try {
            org.json.JSONArray vt = new org.json.JSONArray(getSharedPreferences("cfg", MODE_PRIVATE).getString("voice_trace", "[]"));
            if (vt.length() == 0) sb.append(L.t("（還沒有紀錄）\n"));
            for (int k = 0; k < vt.length(); k++) sb.append(vt.getString(k)).append("\n");
        } catch (Exception e) { sb.append(L.t("（讀取失敗）\n")); }
        sb.append(L.t("\n最近一次通知診斷：\n")).append(OverlayService.lastDiag).append('\n');
        info.setText(sb);
    }
}
