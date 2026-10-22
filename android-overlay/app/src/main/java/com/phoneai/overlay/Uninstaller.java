package com.phoneai.overlay;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * 解除安裝並復原：還原長按電源鍵、刪掉 Downloads 裡的記憶備份，最後解除安裝 PhoneAI 本身。
 * App 自己的資料（Key、記憶、紀錄）隨解除安裝一起刪除。每一個刪除動作都要使用者確認。
 */
final class Uninstaller {
    private Uninstaller() { }

    static void start(Activity a) {
        String msg = L.t("這會：\n")
                + L.t("• 把長按電源鍵設定還原成原來的值\n")
                + L.t("• 刪除 PhoneAI 的 Claude API Key、記憶與紀錄（隨 App 一起刪除）\n")
                + L.t("• 刪除 Downloads 裡的記憶備份檔\n")
                + L.t("• 最後解除安裝 PhoneAI Overlay 本身\n\n刪除後無法復原。");
        new MaterialAlertDialogBuilder(a).setTitle(L.t("解除安裝 PhoneAI")).setMessage(msg)
                .setPositiveButton(L.t("繼續"), (d, w) -> run(a))
                .setNegativeButton(L.t("取消"), null).show();
    }

    private static void run(Activity a) {
        new Thread(() -> {
            // 1. 還原長按電源鍵（記下的原值；沒有記錄就不動）
            SharedPreferences sp = a.getSharedPreferences("cfg", Activity.MODE_PRIVATE);
            if (sp.contains("plp_orig")) {
                String orig = sp.getString("plp_orig", "");
                Maintenance.suRun(orig.isEmpty() ? "settings delete global power_button_long_press"
                        : "settings put global power_button_long_press " + orig);
            }
            // 2. 刪除記憶備份檔
            Maintenance.suRun("rm -f /sdcard/Download/phoneai_memory_backup.json");
            // 3. 最後才解除安裝本 App：用系統的解除安裝畫面，由你按確定
            a.runOnUiThread(() -> a.startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.getPackageName()))));
        }, "uninstall").start();
    }
}
