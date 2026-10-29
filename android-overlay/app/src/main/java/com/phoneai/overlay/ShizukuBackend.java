package com.phoneai.overlay;

import android.content.pm.PackageManager;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import rikka.shizuku.Shizuku;

/**
 * Shizuku 模式的指令執行：Shizuku 服務以 adb（shell）身分執行，不需要 root。
 * Shizuku 13 把 newProcess 改成 private，這裡用反射呼叫（很多 App 都這樣做）。
 */
final class ShizukuBackend {
    private ShizukuBackend() { }

    static final int REQ = 4242;
    private static Method newProcess;

    static boolean installed(android.content.Context c) {
        try {
            c.getPackageManager().getPackageInfo("moe.shizuku.privileged.api", 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 0＝可用；1＝服務沒在跑（沒啟動）；2＝還沒授權給 PhoneAI；3＝版本太舊。 */
    static int state() {
        try {
            if (!Shizuku.pingBinder()) return 1;
            if (Shizuku.isPreV11()) return 3;
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED ? 0 : 2;
        } catch (Throwable t) {
            return 1;
        }
    }

    static boolean ready() { return state() == 0; }

    static void requestPermission() {
        try {
            if (Shizuku.pingBinder() && !Shizuku.shouldShowRequestPermissionRationale()) Shizuku.requestPermission(REQ);
        } catch (Throwable ignored) { }
    }

    private static Process start(String[] cmd) throws Exception {
        if (newProcess == null) {
            newProcess = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
            newProcess.setAccessible(true);
        }
        return (Process) newProcess.invoke(null, cmd, null, null);
    }

    /** 跑腳本（標準輸入餵給 sh），回傳輸出；失敗回 "ERR:..."。 */
    static String run(String script) {
        try {
            Process p = start(new String[]{"sh"});
            p.getOutputStream().write((script + "\nexit\n").getBytes(StandardCharsets.UTF_8));
            p.getOutputStream().close();
            String out = new String(readAll(p.getInputStream()), StandardCharsets.UTF_8);
            return p.waitFor() == 0 ? out : "ERR:" + out;
        } catch (Throwable e) {
            return "ERR:" + e;
        }
    }

    /** 跑指令並回傳原始位元組（截圖用）；失敗回 null。 */
    static byte[] runBytes(String cmd) {
        try {
            Process p = start(new String[]{"sh", "-c", cmd});
            p.getOutputStream().close();
            byte[] out = readAll(p.getInputStream());
            p.waitFor();
            return out;
        } catch (Throwable e) {
            return null;
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) o.write(b, 0, n);
        return o.toByteArray();
    }
}
