package com.phoneai.overlay;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;

/**
 * 管理 InputServer（root 常駐的即時觸控注入程式）。預覽窗打開時啟動、關閉時結束。
 * ready() 為 false（還在啟動、啟動失敗、程序死掉）時，呼叫端要退回舊的 RootInput（放開後才送整個手勢）。
 */
final class LiveInput {
    private static final String TAG = OverlayService.TAG;
    private static Process proc;
    private static Writer out;
    private static volatile boolean ready;
    private static int display = -1;
    private static int dropped;

    private LiveInput() { }

    static synchronized void start(Context c, int displayId) {
        Log.i(TAG, "live input start requested, display " + displayId);
        if (displayId < 0) return;   // 0 = 主螢幕（多指觸控用），>0 = 背景虛擬螢幕
        if (proc != null && display == displayId && proc.isAlive()) return;
        stop(false);  // 重啟時不送 pkill（會跟新程序賽跑）；舊程序的 stdin 一關就自己結束
        display = displayId;
        final String apk = c.getPackageCodePath();
        new Thread(() -> launch(apk, displayId), "liveinput").start();
    }

    private static void launch(String apk, int displayId) {
        try {
            // apk 路徑與螢幕編號都是程式自己算出來的（沒有使用者輸入），可以安全放進指令
            Log.i(TAG, "live input launching: " + apk);
            Process p = new ProcessBuilder(Root.su(), "-c",
                    "CLASSPATH=" + apk + " app_process /system/bin com.phoneai.overlay.InputServer " + displayId)
                    .redirectErrorStream(true).start();
            synchronized (LiveInput.class) {
                if (display != displayId) { p.destroy(); return; }  // 啟動期間已經被停掉或換螢幕
                proc = p;
                out = new OutputStreamWriter(p.getOutputStream());
            }
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if ("ready".equals(line)) { ready = true; Log.i(TAG, "live input ready, display " + displayId); }
                    else Log.w(TAG, "live input: " + line);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "live input launch: " + e);
        } finally {
            ready = false;
            Log.i(TAG, "live input ended, display " + displayId);
        }
    }

    static boolean ready() { return ready; }

    /** 送一行指令（D/M/U x y、C）。失敗就標成不可用，之後走備援。 */
    static void send(String line) {
        Writer w;
        synchronized (LiveInput.class) { w = out; }
        if (w == null || !ready) {
            if (++dropped % 50 == 1) Log.w(TAG, "live input not ready, dropped " + dropped);
            return;
        }
        try {
            w.write(line);
            w.write('\n');
            w.flush();
        } catch (Exception e) {
            ready = false;
        }
    }

    static synchronized void stop() { stop(true); }

    private static synchronized void stop(boolean kill) {
        ready = false;
        display = -1;
        Process p = proc;
        proc = null;
        out = null;
        if (p == null) return;  // 沒有舊的就不要送 pkill：它跟剛啟動的新程序賽跑，會把新的也殺掉
        p.destroy();
        if (kill) try { new ProcessBuilder(Root.su(), "-c", "pkill -f 'com.phoneai.overlay.InputServer'").start(); } catch (Exception ignored) { }
    }
}
