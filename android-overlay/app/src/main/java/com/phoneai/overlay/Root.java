package com.phoneai.overlay;

import java.io.File;

/**
 * 找到 su 的實際路徑。App 的 PATH 不一定有 su（有些手機的 su 在 /system/xbin、/sbin、/data/adb 底下），
 * 直接寫 "su" 會出現「Cannot run program "su": error=2」，所有需要 root 的功能都會失敗。
 */
final class Root {
    private Root() { }

    private static String found;

    static String su() {
        if (found != null) return found;
        String[] fixed = {"/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "/data/adb/ksu/bin/su", "/debug_ramdisk/su", "/vendor/bin/su", "/system/bin/.ext/su"};
        for (String p : fixed) {
            if (new File(p).canExecute()) return found = p;
        }
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(":")) {
                if (new File(dir, "su").canExecute()) return found = new File(dir, "su").getAbsolutePath();
            }
        }
        return "su";   // 真的找不到：維持原樣，錯誤訊息會顯示在畫面上
    }
}
