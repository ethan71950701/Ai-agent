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

    /** 讀取目前畫面上記下的位置附近的顏色：有顏色（綠色等）＝開，灰色＝關；讀不到回 null。需要在背景執行。 */
    static Boolean readOn(Context c) {
        try {
            Process p = new ProcessBuilder(Root.su(), "-c", "screencap -p").redirectErrorStream(false).start();
            p.getOutputStream().close();
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = p.getInputStream().read(buf)) > 0) bo.write(buf, 0, n);
            p.waitFor();
            android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeByteArray(bo.toByteArray(), 0, bo.size());
            if (bmp == null) return null;
            int cx = tapX(c), cy = tapY(c);
            int colored = 0;
            // 開關的軌道是彩色的、拇指是白色的，所以在記下的位置附近找「有顏色」的像素
            for (int dy = -30; dy <= 30; dy += 3) {
                for (int dx = -40; dx <= 40; dx += 3) {
                    int x = cx + dx, y = cy + dy;
                    if (x < 0 || y < 0 || x >= bmp.getWidth() || y >= bmp.getHeight()) continue;
                    int argb = bmp.getPixel(x, y);
                    int r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
                    if (Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) > 60) colored++;
                }
            }
            return colored >= 8;
        } catch (Exception e) {
            return null;
        }
    }

    /** 檢查目前狀態：打開 Tailscale、讀顏色、回到 PhoneAI。結果記在 cfg（ts_on：1 開、0 關、-1 不知道）。 */
    static void checkState(Context c, java.util.function.Consumer<Boolean> done) {
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            Maintenance.suRun("monkey -p " + PKG + " -c android.intent.category.LAUNCHER 1");
            sleep(2000);
            Boolean on = readOn(app);
            Maintenance.suRun("monkey -p " + app.getPackageName() + " -c android.intent.category.LAUNCHER 1");
            cfg(app).edit().putInt("ts_on", on == null ? -1 : (on ? 1 : 0)).apply();
            if (done != null) new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> done.accept(on));
        }).start();
    }

    /** 切換：先讀目前狀態，然後點一下連線按鈕（開的會關，關的會開），再讀一次狀態回報。 */
    static void toggle(Context c, java.util.function.Consumer<Boolean> done) {
        final Context app = c.getApplicationContext();
        checkState(app, now -> {
            if (now == null) {
                if (done != null) done.accept(null);
                return;
            }
            new Thread(() -> {
                Maintenance.suRun("monkey -p " + PKG + " -c android.intent.category.LAUNCHER 1");
                sleep(2000);
                Maintenance.suRun("input tap " + tapX(app) + " " + tapY(app));
                sleep(3000);
                checkState(app, done);
            }).start();
        });
    }

    /**
     * 設定點擊位置（不用輸入數字）：打開 Tailscale，在畫面上蓋一層透明的提示，
     * 使用者直接點一下連線按鈕，就記下那個位置，然後把提示拿掉。需要「顯示在其他 App 上層」的權限。
     */
    static void captureTap(Context c) {
        if (!android.provider.Settings.canDrawOverlays(c)) {
            android.widget.Toast.makeText(c, L.t("請先允許「顯示在其他 App 上層」，再試一次"), android.widget.Toast.LENGTH_LONG).show();
            c.startActivity(new android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:" + c.getPackageName())).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        }
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            Maintenance.suRun("monkey -p " + PKG + " -c android.intent.category.LAUNCHER 1");
            sleep(2000);
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> showCaptureLayer(app));
        }).start();
    }

    private static void showCaptureLayer(Context app) {
        final android.view.WindowManager wm = (android.view.WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
        final android.widget.FrameLayout layer = new android.widget.FrameLayout(app);
        layer.setBackgroundColor(0x33000000);   // 半透明，看得到底下的 Tailscale
        android.widget.TextView hint = new android.widget.TextView(app);
        hint.setText(L.t("請點一下 Tailscale 的連線按鈕（只記錄這一次）"));
        hint.setTextColor(0xFFFFFFFF);
        hint.setTextSize(18);
        hint.setBackgroundColor(0xCC000000);
        hint.setPadding(40, 40, 40, 40);
        android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 120;
        layer.addView(hint, lp);
        layer.setOnTouchListener((v, ev) -> {
            if (ev.getAction() == android.view.MotionEvent.ACTION_UP) {
                setTap(app, (int) ev.getRawX(), (int) ev.getRawY());
                try { wm.removeView(layer); } catch (Exception ignored) { }
                android.widget.Toast.makeText(app, L.t("已記下位置"), android.widget.Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        android.view.WindowManager.LayoutParams p = new android.view.WindowManager.LayoutParams(
                android.view.WindowManager.LayoutParams.MATCH_PARENT, android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.TRANSLUCENT);
        p.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
        try {
            wm.addView(layer, p);
        } catch (Exception e) {
            android.widget.Toast.makeText(app, L.t("無法顯示提示：") + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }
}
