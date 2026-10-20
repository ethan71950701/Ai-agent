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
        setContentView(Ui.screen(this, "進階與診斷", true, c));
        Ui.enterSpring(this);
        LinearLayout box = c[0];

        Ui.section(this, box, "測試與工具");
        Ui.row(this, box, 0, 5, "shield", "修復權限", "用 root 補回懸浮層、通知、無障礙、Termux 權限", Ui.chevron(this), v -> repairPerms());
        Ui.row(this, box, 1, 5, "check_circle", "自我檢查", "檢查 root、Termux、金鑰檔等，結果在新頁面", Ui.chevron(this), v -> selfTest());
        Ui.row(this, box, 2, 5, "sparkle", "測試漣漪與通知", "約 8 秒", Ui.chevron(this), v -> {
            startForegroundService(new Intent(this, OverlayService.class).setAction(OverlayService.ACTION_TEST));
            info.postDelayed(this::refresh, 1500);
        });
        Ui.row(this, box, 3, 5, "list", "App 清單", "顯示讀到幾個 App", Ui.chevron(this), v -> {
            String t = AppListExporter.build(this);
            info.append("\n讀到 " + t.split("\n").length + " 個 App（Termux 用 python ~/applist.py 取得）\n");
        });
        Ui.row(this, box, 4, 5, "refresh", "重新整理狀態", null, null, v -> refresh());

        Ui.section(this, box, "狀態");
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
        android.widget.Toast.makeText(this, "修復中…", android.widget.Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            final String r = Maintenance.repairPermissions();
            runOnUiThread(() -> { android.widget.Toast.makeText(this, r, android.widget.Toast.LENGTH_LONG).show(); refresh(); });
        }).start();
    }

    private void selfTest() {
        String err = Maintenance.runInTermux(this, "python \"$HOME/selftest.py\"", "selftest.log");
        if (err != null) { android.widget.Toast.makeText(this, err, android.widget.Toast.LENGTH_LONG).show(); return; }
        startActivity(new Intent(this, LogActivity.class).putExtra("mode", 4));
    }

    /** 系統設定 power_button_long_press：0 = 什麼都不做（電源鍵助理偵測程式才不會被電源選單搶走）、1 = 跳電源選單、其他 = 別的動作（例如叫語音助理）。 */
    private String powerLongPressText() {
        String v = Settings.Global.getString(getContentResolver(), "power_button_long_press");
        if (v == null) return "（沒有設定，用系統預設）";
        switch (v) {
            case "0": return "0（不做事，長按只叫助理）";
            case "1": return "1（會跳電源選單：長按電源鍵時選單可能會先蓋住助理）";
            case "103": return "103（三星原生語音助理：長按電源鍵叫助理，請不要改它）";
            default: return v + "（其他，系統自己的動作）";
        }
    }

    private void refresh() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        StringBuilder sb = new StringBuilder();
        sb.append("App 版本：1.0.").append(BuildConfig.VERSION_CODE).append("\n");
        sb.append("編譯：").append(getString(R.string.build_id)).append("（commit · 編譯時間）\n");
        sb.append("Android API：").append(Build.VERSION.SDK_INT).append('\n');
        sb.append("懸浮層權限：").append(Settings.canDrawOverlays(this) ? "✓" : "✕").append('\n');
        sb.append("通知權限：").append(checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED ? "✓" : "✕").append('\n');
        if (Build.VERSION.SDK_INT >= 36) {
            sb.append("可發即時通知：").append(nm.canPostPromotedNotifications() ? "✓" : "✕").append('\n');
        }
        android.content.SharedPreferences cfgp = getSharedPreferences("cfg", MODE_PRIVATE);
        sb.append("無障礙服務：").append(A11yGuard.listed(this) ? "✓ 開著" : "✕ 沒開").append("；自動修復 ")
                .append(A11yGuard.enabled(this) ? "開" : "關").append("；已修復 ").append(cfgp.getInt("a11y_fixes", 0)).append(" 次")
                .append(cfgp.getString("a11y_last", null) != null ? "（最近 " + cfgp.getString("a11y_last", "") + "）" : "").append('\n');
        sb.append("系統長按電源鍵動作：").append(powerLongPressText()).append('\n');
        sb.append("底欄玻璃效果：").append(GlassBar.status).append('\n');
        sb.append("按鈕透鏡：").append(GlassBar.lensInfo).append('\n');
        sb.append("Termux 指令權限：").append(checkSelfPermission(CommandSender.PERM)
                == PackageManager.PERMISSION_GRANTED ? "✓" : "✕（到「修復權限」一鍵補回）").append('\n');
        sb.append("\n最近的任務（含用量估價）：\n");
        try {
            org.json.JSONArray h = new org.json.JSONArray(getSharedPreferences("cfg", MODE_PRIVATE).getString("history", "[]"));
            if (h.length() == 0) sb.append("（還沒有紀錄）\n");
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.TAIWAN);
            for (int k = h.length() - 1; k >= Math.max(0, h.length() - 5); k--) {
                org.json.JSONObject o = h.getJSONObject(k);
                sb.append(f.format(new java.util.Date(o.getLong("t")))).append("  ").append(o.getString("text")).append("\n");
            }
        } catch (Exception e) { sb.append("（讀取紀錄失敗）\n"); }
        sb.append("\n語音辨識除錯（最近）：\n");
        try {
            org.json.JSONArray vt = new org.json.JSONArray(getSharedPreferences("cfg", MODE_PRIVATE).getString("voice_trace", "[]"));
            if (vt.length() == 0) sb.append("（還沒有紀錄）\n");
            for (int k = 0; k < vt.length(); k++) sb.append(vt.getString(k)).append("\n");
        } catch (Exception e) { sb.append("（讀取失敗）\n"); }
        sb.append("\n最近一次通知診斷：\n").append(OverlayService.lastDiag).append('\n');
        info.setText(sb);
    }
}
