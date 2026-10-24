package com.phoneai.overlay;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.DisplayMetrics;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

/**
 * 外網用：一鍵打開 Tailscale，並點一下它左上角的「連線」位置，然後回到 PhoneAI。
 * 需要 root（用 su 執行命令）。點擊位置可以在設定裡調整。
 */
final class TailscaleLauncher {
    private static final String PKG = "com.tailscale.ipn";

    private TailscaleLauncher() { }

    private static SharedPreferences cfg(Context c) { return c.getSharedPreferences("cfg", Context.MODE_PRIVATE); }

    /** 點擊位置：有設定就用設定的；沒設定就用螢幕左上角附近（寬 12%、高 18%）。 */
    static int tapX(Context c) {
        int v = cfg(c).getInt("ts_tap_x", -1);
        return v >= 0 ? v : (int) (screen(c).widthPixels * 0.12);
    }

    static int tapY(Context c) {
        int v = cfg(c).getInt("ts_tap_y", -1);
        return v >= 0 ? v : (int) (screen(c).heightPixels * 0.18);
    }

    private static DisplayMetrics screen(Context c) {
        return c.getResources().getDisplayMetrics();
    }

    static void setTap(Context c, int x, int y) {
        cfg(c).edit().putInt("ts_tap_x", x).putInt("ts_tap_y", y).apply();
    }

    static boolean installed(Context c) {
        try {
            c.getPackageManager().getPackageInfo(PKG, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 在背景執行：打開 Tailscale → 點一下設定的位置 → 回到 PhoneAI。 */
    static void connect(Context c, Runnable onFail) {
        new Thread(() -> {
            if (!installed(c)) {
                if (onFail != null) onFail.run();
                return;
            }
            Maintenance.suRun("monkey -p " + PKG + " -c android.intent.category.LAUNCHER 1");
            sleep(2500);
            Maintenance.suRun("input tap " + tapX(c) + " " + tapY(c));
            sleep(1500);
            Maintenance.suRun("monkey -p " + c.getPackageName() + " -c android.intent.category.LAUNCHER 1");
        }).start();
    }

    /** 設定點擊的位置：填 X、Y（像素），可以先按「測試」看點不點得到。 */
    static void showSettings(Context c) {
        LinearLayout form = new LinearLayout(c);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (c.getResources().getDisplayMetrics().density * 16);
        form.setPadding(pad, pad / 2, pad, 0);
        EditText x = new EditText(c);
        x.setHint("X");
        x.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        x.setText(String.valueOf(tapX(c)));
        EditText y = new EditText(c);
        y.setHint("Y");
        y.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        y.setText(String.valueOf(tapY(c)));
        form.addView(x);
        form.addView(y);
        new AlertDialog.Builder(c)
                .setTitle(L.t("Tailscale 連線按鈕的位置"))
                .setMessage(L.t("在 Tailscale 上連線按鈕的 X、Y（像素）。不確定就用「開發者選項 → 顯示觸碰位置」量。"))
                .setView(form)
                .setPositiveButton(L.t("儲存"), (d, w) -> {
                    try {
                        setTap(c, Integer.parseInt(x.getText().toString().trim()), Integer.parseInt(y.getText().toString().trim()));
                        Toast.makeText(c, L.t("已儲存"), Toast.LENGTH_SHORT).show();
                    } catch (NumberFormatException e) {
                        Toast.makeText(c, L.t("請輸入數字"), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton(L.t("測試"), (d, w) -> {
                    try {
                        setTap(c, Integer.parseInt(x.getText().toString().trim()), Integer.parseInt(y.getText().toString().trim()));
                        connect(c, null);
                    } catch (NumberFormatException e) {
                        Toast.makeText(c, L.t("請輸入數字"), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(L.t("取消"), null)
                .show();
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }
}
