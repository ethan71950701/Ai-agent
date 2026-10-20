package com.phoneai.overlay;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 長按電源鍵叫出助理面板（給長按電源鍵被系統綁死的手機，例如小米）。
 * 用 root 跑 `getevent -lt <電源鍵裝置>`，按住超過 HOLD_MS 就開 AskActivity（不自動開始聽）。
 * 為什麼放在 App 的無障礙服務裡、不用 Termux 的 powerkey.py：實測 Termux 在背景會被 HyperOS 凍結好幾秒，凍結期間按的電源鍵沒有即時反應；
 * 無障礙服務是系統認定的重要服務，不會被凍結，開機後也會自己啟動。
 * 裝置選擇規則跟 scripts/powerkey.py 一樣：名稱含 pwrkey 的優先；排除 bark（小米 13 的 pmic_pwrkey_bark）與 goodix（指紋，也登記了 KEY_POWER）。
 * 要先讓系統自己的長按什麼都不做（settings put global power_button_long_press 0），否則系統的電源選單／小愛會一起跳；主畫面的開關會幫你設。
 */
final class PowerKeyWatcher {
    private static final String TAG = OverlayService.TAG;
    private static final long HOLD_MS = 400, COOLDOWN_MS = 1500;
    private static final Pattern POWER = Pattern.compile("EV_KEY\\s+KEY_POWER\\s+(DOWN|UP)");
    private static volatile boolean running;
    private static volatile Process proc;
    private static Thread thread;

    private PowerKeyWatcher() { }

    static synchronized void start(Context ctx) {
        if (running) return;
        running = true;
        final Context app = ctx.getApplicationContext();
        thread = new Thread(() -> loop(app), "powerkey");
        thread.setDaemon(true);
        thread.start();
    }

    static synchronized void stop() {
        running = false;
        Process p = proc;
        if (p != null) p.destroy();
        killGetevent();
    }

    private static void killGetevent() {
        try { new ProcessBuilder(Root.su(), "-c", "pkill -f '[g]etevent -lt'").start().waitFor(); } catch (Exception ignored) { }
    }

    private static void loop(Context app) {
        final Handler main = new Handler(Looper.getMainLooper());
        final long[] lastFire = {0};
        final Runnable fire = () -> {
            long now = System.currentTimeMillis();
            if (now - lastFire[0] < COOLDOWN_MS || AssistantSheet.current != null) return;  // 面板已經開著就不重複開
            lastFire[0] = now;
            try {
                app.startActivity(new Intent(app, AskActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e) {
                Log.e(TAG, "powerkey open panel", e);
            }
        };
        while (running) {
            List<String> devs = findDevices();
            if (devs.isEmpty()) {
                Log.w(TAG, "powerkey: no KEY_POWER device");
                sleep(15000);
                continue;
            }
            // getevent 一次只能收一個裝置：只用第一個（規則已排除 bark／指紋）
            try {
                proc = new ProcessBuilder(Root.su(), "-c", "getevent -lt " + devs.get(0)).redirectErrorStream(true).start();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                    String line;
                    while (running && (line = r.readLine()) != null) {
                        Matcher m = POWER.matcher(line);
                        if (!m.find()) continue;
                        if ("DOWN".equals(m.group(1))) {
                            main.removeCallbacks(fire);
                            main.postDelayed(fire, HOLD_MS);
                        } else {
                            main.removeCallbacks(fire);
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "powerkey getevent: " + e);
            } finally {
                main.removeCallbacks(fire);
                killGetevent();
            }
            if (running) sleep(3000);  // getevent 意外結束：稍等再重開
        }
    }

    /** 從 `getevent -pl` 找有 KEY_POWER 的輸入裝置路徑（排序後最適合的在前面）。 */
    static List<String> findDevices() {
        List<String> real = new ArrayList<>(), other = new ArrayList<>(), all = new ArrayList<>();
        try {
            Process p = new ProcessBuilder(Root.su(), "-c", "getevent -pl").redirectErrorStream(true).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String path = null, name = "", line;
                Pattern dev = Pattern.compile("add device \\d+: (\\S+)"), nm = Pattern.compile("\\s*name:\\s+\"(.*)\"");
                while ((line = r.readLine()) != null) {
                    Matcher m = dev.matcher(line);
                    if (m.find()) { path = m.group(1); name = ""; continue; }
                    Matcher n = nm.matcher(line);
                    if (n.find()) { name = n.group(1).toLowerCase(); continue; }
                    if (line.contains("KEY_POWER") && path != null && !all.contains(path)) {
                        all.add(path);
                        if (name.contains("pwrkey") && !name.contains("bark")) real.add(path);
                        else if (!name.contains("bark") && !name.contains("goodix")) other.add(path);
                    }
                }
            }
            p.waitFor();
        } catch (Exception e) {
            Log.w(TAG, "powerkey find: " + e);
        }
        if (!real.isEmpty()) return real;
        return other.isEmpty() ? all : other;
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }
}
