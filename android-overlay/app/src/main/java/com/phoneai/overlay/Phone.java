package com.phoneai.overlay;

import android.accessibilityservice.AccessibilityService;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.media.AudioManager;
import android.net.Uri;
import android.provider.Settings;
import android.view.Display;
import android.view.WindowManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 不用 root 的做法：點擊、滑動、按鍵、截圖走無障礙服務，開 App 走一般的 startActivity，
 * 系統設定能直接改的（亮度、旋轉、勿擾、音量）用系統 API，不能直接改的（Wi-Fi、藍牙、深色模式、定位…）打開對應的設定頁。
 * 有 root 時 AgentLoop 仍然用原本的 root 指令（更快、更完整）；Root.available() 為 false 才會用這裡。
 */
final class Phone {
    private Phone() { }

    // ---------- 手勢（無障礙，座標是像素） ----------

    private static boolean g(int x1, int y1, int x2, int y2, int ms, int hold) {
        try {
            JSONObject f = new JSONObject().put("x1", x1).put("y1", y1).put("x2", x2).put("y2", y2).put("ms", ms);
            JSONObject j = new JSONObject().put("hold", hold).put("fingers", new JSONArray().put(f));
            return "ok".equals(RippleAccessibilityService.gesture(j));
        } catch (Exception e) {
            return false;
        }
    }

    static boolean tap(int x, int y) { return g(x, y, x, y, 60, 0); }

    static boolean longpress(int x, int y, int ms) { return g(x, y, x, y, ms, 0); }

    static boolean swipe(int x1, int y1, int x2, int y2, int ms) { return g(x1, y1, x2, y2, ms, 0); }

    /** 按住 hold 毫秒再拖到終點。 */
    static boolean drag(int x1, int y1, int x2, int y2, int hold, int ms) { return g(x1, y1, x2, y2, ms, hold); }

    // ---------- 按鍵（無障礙全域動作） ----------

    static boolean key(String k) {
        switch (k) {
            case "back": return RippleAccessibilityService.globalAction(AccessibilityService.GLOBAL_ACTION_BACK);
            case "home": return RippleAccessibilityService.globalAction(AccessibilityService.GLOBAL_ACTION_HOME);
            case "recents": return RippleAccessibilityService.globalAction(AccessibilityService.GLOBAL_ACTION_RECENTS);
            case "notifications": return RippleAccessibilityService.globalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS);
            case "quick_settings": return RippleAccessibilityService.globalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS);
            case "lock": return RippleAccessibilityService.globalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);
            case "screenshot": return RippleAccessibilityService.globalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT);
            default: return false;
        }
    }

    // ---------- 截圖、螢幕大小 ----------

    /** 用無障礙截圖（Android 11 以上，設定檔要有 canTakeScreenshot）；系統限制大約一秒一張，太快會失敗，所以會重試。 */
    static Bitmap screenshot() {
        final RippleAccessibilityService s = RippleAccessibilityService.instance;
        if (s == null) return null;
        for (int attempt = 0; attempt < 3; attempt++) {
            final Bitmap[] out = {null};
            final CountDownLatch latch = new CountDownLatch(1);
            try {
                s.takeScreenshot(Display.DEFAULT_DISPLAY, s.getMainExecutor(), new AccessibilityService.TakeScreenshotCallback() {
                    @Override public void onSuccess(AccessibilityService.ScreenshotResult r) {
                        try {
                            Bitmap hw = Bitmap.wrapHardwareBuffer(r.getHardwareBuffer(), r.getColorSpace());
                            if (hw != null) out[0] = hw.copy(Bitmap.Config.ARGB_8888, false);
                            r.getHardwareBuffer().close();
                        } catch (Exception ignored) { }
                        latch.countDown();
                    }

                    @Override public void onFailure(int errorCode) { latch.countDown(); }
                });
                latch.await(3, TimeUnit.SECONDS);
            } catch (Exception e) {
                return null;
            }
            if (out[0] != null) return out[0];
            try { Thread.sleep(1200); } catch (InterruptedException ignored) { }
        }
        return null;
    }

    static int[] screenSize(Context c) {
        try {
            Rect b = c.getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds();
            return new int[]{b.width(), b.height()};
        } catch (Exception e) {
            return new int[]{1080, 2340};
        }
    }

    // ---------- 開 App、網址、讀系統資訊 ----------

    static boolean launch(Context c, String pkg) {
        try {
            Intent it = c.getPackageManager().getLaunchIntentForPackage(pkg);
            if (it == null) return false;
            c.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static boolean openUrl(Context c, String url) {
        try {
            c.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 不用 root 跑唯讀指令（getprop 這類一般權限就讀得到的）；有些 dumpsys 沒權限會是空的。 */
    static String shellRead(String cmd) {
        try {
            Process p = new ProcessBuilder("sh", "-c", cmd).redirectErrorStream(true).start();
            p.getOutputStream().close();
            InputStream in = p.getInputStream();
            byte[] buf = in.readAllBytes();
            if (!p.waitFor(5, TimeUnit.SECONDS)) p.destroy();
            String out = new String(buf, java.nio.charset.StandardCharsets.UTF_8);
            return out.trim().isEmpty() ? "（沒有輸出；不用 root 時有些系統資訊讀不到）" : out;
        } catch (Exception e) {
            return "執行失敗：" + e.getMessage();
        }
    }

    // ---------- 固定指令：shell 指令 → 不用 root 的做法 ----------

    /** AI 的 cmd 動作名稱 → 對應的 shell 指令（沒有 root 時再轉成不用 root 的做法）；不認識回 null。 */
    static String shellForName(String name) {
        switch (name) {
            case "wifi_on": return "svc wifi enable";
            case "wifi_off": return "svc wifi disable";
            case "bluetooth_on": return "svc bluetooth enable";
            case "bluetooth_off": return "svc bluetooth disable";
            case "volume_up": return "input keyevent 24";
            case "volume_down": return "input keyevent 25";
            case "dark_mode_on": return "cmd uimode night yes";
            case "dark_mode_off": return "cmd uimode night no";
            case "dnd_on": return "cmd notification set_dnd priority";
            case "dnd_off": return "cmd notification set_dnd off";
            case "auto_rotate_on": return "settings put system accelerometer_rotation 1";
            case "auto_rotate_off": return "settings put system accelerometer_rotation 0";
            case "location_on": return "cmd location set-location-enabled true";
            case "location_off": return "cmd location set-location-enabled false";
            case "notifications_open": return "cmd statusbar expand-notifications";
            case "quick_settings_open": return "cmd statusbar expand-settings";
            case "panels_close": return "cmd statusbar collapse";
            default: return null;
        }
    }

    /** 固定指令與 cmd 動作用的 shell 指令，對應到不用 root 的做法代號；沒有對應回 null（單元測試會確認每個固定指令都有）。 */
    static String noRootAction(String shell) {
        if (shell == null) return null;
        switch (shell) {
            case "svc wifi enable": case "svc wifi disable": return "wifi.panel";
            case "svc bluetooth enable": case "svc bluetooth disable": return "bt.settings";
            case "input keyevent 3": return "home";
            case "input keyevent 4": return "back";
            case "input keyevent 24": return "vol.up";
            case "input keyevent 25": return "vol.down";
            case "input keyevent 120": return "screenshot";
            case "input keyevent 223": return "lock";
            case "input keyevent 187": return "recents";
            case "cmd notification set_dnd priority": return "dnd.on";
            case "cmd notification set_dnd off": return "dnd.off";
            case "settings put system accelerometer_rotation 1": return "rotate.on";
            case "settings put system accelerometer_rotation 0": return "rotate.off";
            case "cmd uimode night yes": case "cmd uimode night no": return "dark.settings";
            case "settings put global low_power 1": case "settings put global low_power 0": return "saver.settings";
            case "cmd connectivity airplane-mode enable": case "cmd connectivity airplane-mode disable": return "airplane.settings";
            case "cmd location set-location-enabled true": case "cmd location set-location-enabled false": return "location.settings";
            case "svc nfc enable": case "svc nfc disable": return "nfc.settings";
            case "svc data enable": case "svc data disable": return "data.settings";
            case "settings put system screen_brightness_mode 1": return "autobright.on";
            case "settings put system screen_brightness_mode 0": return "autobright.off";
            case "settings put system screen_brightness_mode 0; settings put system screen_brightness 1": return "bright.min";
            case "settings put system screen_brightness_mode 0; settings put system screen_brightness 255": return "bright.max";
            case "cmd statusbar expand-notifications": return "shade";
            case "cmd statusbar expand-settings": return "quick";
            case "cmd statusbar collapse": return "collapse";
            default: return null;
        }
    }

    /** 依代號執行；回傳要告訴使用者的話。只能打開設定頁、要使用者自己切換的，回覆不會以「已」開頭（讓 AI 知道還沒做完）。 */
    static String runNoRoot(Context c, String id) {
        switch (id) {
            case "home": return glob("home", "已回到主畫面");
            case "back": return glob("back", "已按返回");
            case "recents": return glob("recents", "已打開最近使用的 App");
            case "screenshot": return glob("screenshot", "已送出截圖");
            case "lock": return glob("lock", "已鎖定螢幕");
            case "shade": return glob("notifications", "已打開通知欄");
            case "quick": return glob("quick_settings", "已打開快速設定");
            case "collapse": return glob("back", "已收起通知欄");
            case "vol.up": return volume(c, true);
            case "vol.down": return volume(c, false);
            case "dnd.on": return dnd(c, true);
            case "dnd.off": return dnd(c, false);
            case "rotate.on": return systemInt(c, Settings.System.ACCELEROMETER_ROTATION, 1, "已開啟自動旋轉");
            case "rotate.off": return systemInt(c, Settings.System.ACCELEROMETER_ROTATION, 0, "已關閉自動旋轉");
            case "autobright.on": return systemInt(c, Settings.System.SCREEN_BRIGHTNESS_MODE, 1, "已開啟自動亮度");
            case "autobright.off": return systemInt(c, Settings.System.SCREEN_BRIGHTNESS_MODE, 0, "已關閉自動亮度");
            case "bright.min": return setBrightness(c, 0);
            case "bright.max": return setBrightness(c, 100);
            case "wifi.panel": return page(c, Settings.Panel.ACTION_WIFI, "Wi-Fi");
            case "bt.settings": return page(c, Settings.ACTION_BLUETOOTH_SETTINGS, "藍牙");
            case "dark.settings": return page(c, Settings.ACTION_DISPLAY_SETTINGS, "深色模式");
            case "saver.settings": return page(c, Settings.ACTION_BATTERY_SAVER_SETTINGS, "省電模式");
            case "airplane.settings": return page(c, Settings.ACTION_AIRPLANE_MODE_SETTINGS, "飛航模式");
            case "location.settings": return page(c, Settings.ACTION_LOCATION_SOURCE_SETTINGS, "定位");
            case "nfc.settings": return page(c, Settings.ACTION_NFC_SETTINGS, "NFC");
            case "data.settings": return page(c, Settings.ACTION_NETWORK_OPERATOR_SETTINGS, "行動數據");
            default: return "不用 root 時沒辦法執行這個指令";
        }
    }

    /** 固定指令（Brain.runFixed）用：shell 指令轉成不用 root 的做法。 */
    static String runFixedNoRoot(Context c, String shell) {
        String id = noRootAction(shell);
        return id == null ? "不用 root 時沒辦法執行這個指令" : runNoRoot(c, id);
    }

    private static String glob(String key, String ok) { return key(key) ? ok : "做不到：請先到系統設定開啟 PhoneAI 的無障礙服務"; }

    private static String page(Context c, String action, String what) {
        try {
            c.startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return "不用 root 沒辦法直接切換" + what + "，已打開設定頁，請自己切換（或接著告訴我要點哪裡）";
        } catch (Exception e) {
            return "不用 root 沒辦法直接切換" + what + "，而且打不開設定頁";
        }
    }

    /** 目前媒體音量（不用 root 也讀得到），接在音量指令的結果後面，AI 就知道要不要再按。 */
    static String volumeNow(Context c) {
        try {
            AudioManager am = c.getSystemService(AudioManager.class);
            int v = am.getStreamVolume(AudioManager.STREAM_MUSIC), max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            return "（現在媒體音量 " + v + "/" + max + (v == 0 ? "，已是最小" : v == max ? "，已是最大" : "") + "）";
        } catch (Exception e) {
            return "";
        }
    }

    private static String volume(Context c, boolean up) {
        try {
            c.getSystemService(AudioManager.class).adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    up ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
            return (up ? "已調大音量" : "已調小音量") + volumeNow(c);
        } catch (Exception e) {
            return "調整音量失敗：" + e.getMessage();
        }
    }

    private static String dnd(Context c, boolean on) {
        try {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            if (nm.isNotificationPolicyAccessGranted()) {
                nm.setInterruptionFilter(on ? NotificationManager.INTERRUPTION_FILTER_PRIORITY : NotificationManager.INTERRUPTION_FILTER_ALL);
                return on ? "已開啟勿擾模式" : "已關閉勿擾模式";
            }
            c.startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return "需要權限：已打開設定頁，請允許 PhoneAI 控制勿擾，之後再說一次";
        } catch (Exception e) {
            return "勿擾模式失敗：" + e.getMessage();
        }
    }

    /** 寫系統設定需要「修改系統設定」權限（一般 App 要使用者在設定裡允許一次）。 */
    private static String systemInt(Context c, String key, int value, String ok) {
        try {
            if (!Settings.System.canWrite(c)) return askWriteSettings(c);
            Settings.System.putInt(c.getContentResolver(), key, value);
            return ok;
        } catch (Exception e) {
            return "設定失敗：" + e.getMessage();
        }
    }

    /** 這個固定指令（shell 字串）在沒 root 時，是否會因為缺「修改系統設定」權限而跳出設定頁。 */
    static boolean willAskWriteSettings(Context c, String shell) {
        String id = noRootAction(shell);
        return id != null && (id.startsWith("rotate.") || id.startsWith("autobright.") || id.startsWith("bright.")) && !Settings.System.canWrite(c);
    }

    private static String askWriteSettings(Context c) {
        try {
            c.startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + c.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) { }
        return "需要權限：已打開設定頁，請允許 PhoneAI「修改系統設定」，之後再說一次";
    }

    /** 亮度 0～100（0 會用最低的 1）；會關掉自動亮度。 */
    static String setBrightness(Context c, int pct) {
        try {
            if (!Settings.System.canWrite(c)) return askWriteSettings(c);
            int p = Math.max(0, Math.min(100, pct));
            Settings.System.putInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, 0);
            Settings.System.putInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, Math.max(1, p * 255 / 100));
            return "已把亮度調到 " + p + "%（自動亮度已關閉）";
        } catch (Exception e) {
            return "調整亮度失敗：" + e.getMessage();
        }
    }
}
