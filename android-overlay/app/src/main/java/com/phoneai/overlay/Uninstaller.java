package com.phoneai.overlay;

import android.app.Activity;
import android.content.SharedPreferences;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * 解除安裝並復原：還原長按電源鍵、刪掉 Termux 家目錄裡我們複製的腳本與 Key、備份檔，
 * 可選擇連 Termux 與 Termux:API 一起解除安裝，最後解除安裝 PhoneAI 本身。每一個刪除動作都要使用者確認。
 */
final class Uninstaller {
    private Uninstaller() { }

    static void start(Activity a) {
        new Thread(() -> {
            boolean termux = Maintenance.suRun("pm path com.termux >/dev/null 2>&1 && echo yes").contains("yes");
            a.runOnUiThread(() -> confirm(a, termux));
        }, "uninstall-check").start();
    }

    private static void confirm(Activity a, boolean termux) {
        String msg = "這會：\n"
                + "• 把長按電源鍵設定還原成原來的值\n"
                + "• 刪除 PhoneAI 的腳本、Claude API Key、GitHub 權杖\n"
                + "• 刪除助理的記憶與待辦（~/memory.json）與備份檔\n"
                + "• 最後解除安裝 PhoneAI Overlay 本身\n\n刪除後無法復原。";
        new MaterialAlertDialogBuilder(a).setTitle("解除安裝 PhoneAI").setMessage(msg)
                .setPositiveButton("繼續", (d, w) -> { if (termux) askTermux(a); else run(a, false); })
                .setNegativeButton("取消", null).show();
    }

    /** 手機上原本就有 Termux 時才問：它可能有你自己的東西，所以要另外問、而且刪除前再確認一次。 */
    private static void askTermux(Activity a) {
        new MaterialAlertDialogBuilder(a).setTitle("手機上的 Termux 要一起刪嗎？")
                .setMessage("Termux 與 Termux:API 可能還有你自己的檔案或設定。\n\n「刪除」會把兩個 App 連同所有資料解除安裝；「保留」只刪 PhoneAI 的東西。")
                .setPositiveButton("刪除 Termux", (d, w) -> confirmDeleteTermux(a))
                .setNeutralButton("保留 Termux", (d, w) -> run(a, false))
                .setNegativeButton("取消", null).show();
    }

    private static void confirmDeleteTermux(Activity a) {
        new MaterialAlertDialogBuilder(a).setTitle("真的要刪除 Termux 嗎？")
                .setMessage("Termux 與 Termux:API 會連同所有資料一起刪除，無法復原。")
                .setPositiveButton("確定刪除", (d, w) -> run(a, true))
                .setNegativeButton("取消", null).show();
    }

    private static void run(Activity a, boolean deleteTermux) {
        new Thread(() -> {
            // 1. 還原長按電源鍵（記下的原值；沒有記錄就不動）
            SharedPreferences sp = a.getSharedPreferences("cfg", Activity.MODE_PRIVATE);
            if (sp.contains("plp_orig")) {
                String orig = sp.getString("plp_orig", "");
                Maintenance.suRun(orig.isEmpty() ? "settings delete global power_button_long_press"
                        : "settings put global power_button_long_press " + orig);
            }
            // 2. 刪除我們複製到家目錄的腳本（清單來自 repo 的 scripts/，跟 install.sh 一致）與其他設定檔
            String h = Maintenance.HOME;
            Maintenance.suRun(
                    "if [ -d " + h + "/phone-ai-agent/scripts ]; then for f in " + h + "/phone-ai-agent/scripts/*.py " + h + "/phone-ai-agent/scripts/*.sh; do rm -f " + h + "/$(basename $f); done; fi; "
                    + "rm -rf " + h + "/phone-ai-agent " + h + "/.shortcuts/tasks/assistant.sh " + h + "/.phoneai_version " + h + "/.phoneai_repo "
                    + h + "/.claude_key " + h + "/.gh_token " + h + "/memory.json; "
                    + "rm -f /sdcard/Download/phoneai_memory_backup.json");
            // 3. 選擇刪除時，才解除安裝 Termux 與 Termux:API
            if (deleteTermux) Maintenance.suRun("pm uninstall com.termux.api; pm uninstall com.termux");
            // 4. 最後才解除安裝本 App（執行後本 App 會直接結束）
            Maintenance.suRun("pm uninstall com.phoneai.overlay");
        }, "uninstall").start();
    }
}
