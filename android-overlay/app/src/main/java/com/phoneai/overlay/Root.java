package com.phoneai.overlay;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 找到能用的 su。App 直接執行 /system/bin/su 在 KernelSU 上會失敗（KernelSU 是在 execve 時攔截，不是真的有檔案）。
 * 解法跟 Termux 的 su 一樣：先跑一個 shell 腳本，由 shell 去執行 su。這個腳本放在 App 自己的資料夾裡。
 */
final class Root {
    private Root() { }

    private static String found;

    /** 放在 App 資料夾裡的 su 包裝腳本（內容與 Termux 的 su 相同的邏輯）。 */
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

    static String su() {
        if (found != null) return found;
        String wrapper = makeWrapper();
        if (wrapper != null) return found = wrapper;
        String[] fixed = {"/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "/data/adb/ksu/bin/su", "/debug_ramdisk/su", "/vendor/bin/su", "/system/bin/.ext/su"};
        for (String p : fixed) {
            if (new File(p).canExecute()) return found = p;
        }
        return "su";   // 真的找不到：維持原樣，錯誤訊息會顯示在畫面上
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
