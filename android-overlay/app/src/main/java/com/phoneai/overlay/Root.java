package com.phoneai.overlay;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 找到能用的 su。App 直接執行 /system/bin/su 在 KernelSU 上會失敗（KernelSU 是在 execve 時攔截，不是真的有檔案）。
 * 做法：先跑一個 shell 腳本，由 shell 去執行 su。這個腳本放在 App 自己的資料夾裡。
 */
final class Root {
    private Root() { }

    private static String found;
    private static volatile Boolean rootOk;
    private static volatile long rootCheckedAt;
    private static final long RECHECK_MS = 60_000;

    /**
     * 這支手機有沒有可用的 root：第一次會實際跑一次 su，結果記下來（不要在主執行緒呼叫）。
     * 執行模式選「普通」就一律回 false，選「Shizuku」就看 Shizuku 能不能用。
     */
    static boolean available() {
        if (App.ctx == null) return rootAvailable();
        switch (ExecMode.get(App.ctx)) {
            case ExecMode.NORMAL: return false;
            case ExecMode.SHIZUKU: return ShizukuBackend.ready();   // Shizuku 模式：指令走 Shizuku（adb 權限），其餘跟有 root 時一樣
            default: return rootAvailable();
        }
    }

    /** 不會卡住的版本：已經判斷過、而且真的有 root 才回 true（畫面建好時用；還沒判斷過回 false）。 */
    static boolean knownRoot() {
        return rootOk != null && rootOk && (App.ctx == null || ExecMode.ROOT.equals(ExecMode.get(App.ctx)));
    }

    /** 真的有 root（用 su）：安裝 APK 這類 Shizuku 做不到的事要看這個，而不是 available()。 */
    static boolean isRoot() {
        return (App.ctx == null || ExecMode.ROOT.equals(ExecMode.get(App.ctx))) && rootAvailable();
    }

    private static boolean rootAvailable() {
        Boolean r = rootOk;
        if (r != null && (r || System.currentTimeMillis() - rootCheckedAt < RECHECK_MS)) return r;
        synchronized (Root.class) {
            // 判斷成「沒有 root」只記一分鐘：使用者可能之後才在 root 管理器裡允許，不要永遠當成沒有
            if (rootOk == null || (!rootOk && System.currentTimeMillis() - rootCheckedAt >= RECHECK_MS)) {
                found = null;
                su();
                rootOk = found != null;
                rootCheckedAt = System.currentTimeMillis();
            }
            return rootOk;
        }
    }

    /** 距離上次判斷超過 10 秒才重新判斷：每個任務開始時呼叫，使用者在 root 管理器撤銷（或剛允許）root 之後，下一個任務就會自動換做法。 */
    static void recheckIfStale() {
        if (System.currentTimeMillis() - rootCheckedAt > 10_000) recheck();
    }

    /** 使用者剛允許（或撤銷）root 之後，重新判斷。 */
    static void recheck() {
        synchronized (Root.class) {
            found = null;
            rootOk = null;
        }
    }

    /** 放在 App 資料夾裡的 su 包裝腳本。 */
    private static final String WRAPPER =
            "#!/system/bin/sh\n"
            + "unset LD_LIBRARY_PATH LD_PRELOAD\n"
            + "for p in /debug_ramdisk/su /sbin/su /system/sbin/su /system/bin/su /system/xbin/su /su/bin/su /magisk/.core/bin/su\n"
            + "do\n"
            + "  if [ -x $p ]; then\n"
            + "    PATH=/debug_ramdisk:/sbin:/sbin/su:/su/bin:/su/xbin:/system/bin:/system/xbin exec $p \"$@\"\n"
            + "  fi\n"
            + "done\n"
            + "exec su \"$@\"\n";

    /** 找出能真的拿到 root 的 su：實際跑 `id`，回傳 0 才算數。順序：直接用 su（系統 PATH）→ App 的包裝腳本 → 常見路徑。 */
    static String su() {
        if (found != null) return found;
        java.util.List<String> cands = new java.util.ArrayList<>();
        cands.add("su");
        String wrapper = makeWrapper();
        if (wrapper != null) cands.add(wrapper);
        for (String p : new String[]{"/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "/data/adb/ksu/bin/su", "/debug_ramdisk/su"}) {
            cands.add(p);
        }
        for (String c : cands) {
            if (works(c)) return found = c;
        }
        return "su";   // 都不行：維持原樣，錯誤訊息會顯示在畫面上
    }

    private static boolean works(String su) {
        try {
            Process p = new ProcessBuilder(su, "-c", "id -u").redirectErrorStream(true).start();
            p.getOutputStream().close();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            boolean done = p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            if (!done) { p.destroy(); return false; }
            return p.exitValue() == 0 && out.endsWith("0");
        } catch (Exception e) {
            return false;
        }
    }

    /** 把包裝腳本寫進 App 的資料夾並設為可執行；失敗回傳 null。 */
    private static String makeWrapper() {
        try {
            if (App.ctx == null) return null;
            File f = new File(App.ctx.getFilesDir(), "su");
            byte[] want = WRAPPER.getBytes(StandardCharsets.UTF_8);
            boolean same = f.exists() && f.length() == want.length;
            if (!same) {
                try (FileOutputStream o = new FileOutputStream(f)) {
                    o.write(want);
                }
            }
            f.setExecutable(true, false);
            return f.canExecute() ? f.getAbsolutePath() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
