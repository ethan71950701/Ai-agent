package com.phoneai.overlay;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import java.nio.charset.StandardCharsets;

/**
 * 維護工作（App 內按鈕）：檢查並更新、修復權限、自我檢查。
 * 需要 root 的部分用 su 從標準輸入餵腳本（內容不出現在指令列）。
 */
final class Maintenance {
    private Maintenance() { }

    private static final String[][] SU = {{Root.su(), "-mm"}, {Root.su(), "-M"}, {Root.su()}};

    /** 用 root 跑腳本（標準輸入餵進去）；回傳輸出。依序試幾種 su 寫法，第一個「結束碼 0」的就用。 */
    static String suRun(String script) {
        if (App.ctx != null && ExecMode.SHIZUKU.equals(ExecMode.get(App.ctx))) return ShizukuBackend.run(script);
        String last = "";
        for (String[] cmd : SU) {
            try {
                Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
                p.getOutputStream().write((script + "\nexit\n").getBytes(StandardCharsets.UTF_8));
                p.getOutputStream().close();
                java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
                byte[] b = new byte[4096];
                int n;
                while ((n = p.getInputStream().read(b)) > 0) o.write(b, 0, n);
                String out = o.toString("UTF-8");
                if (p.waitFor() == 0) return out;
                last = out;
            } catch (Exception e) {
                last = String.valueOf(e);
            }
        }
        Root.recheck();   // su 都失敗：可能是 root 被撤銷了，下次用 Root.available() 時重新判斷
        return "ERR:" + last;
    }

        /** 用 root 把 App 需要的權限補回來（等同 overlay_install.sh 裡跟權限有關的部分）；回傳給人看的結果。 */
    static String repairPermissions() {
        final String pkg = "com.phoneai.overlay";
        String svc = pkg + "/" + pkg + ".RippleAccessibilityService";
        String script = "PKG=" + pkg + "\nSVC=" + svc + "\n"
                + "appops set $PKG SYSTEM_ALERT_WINDOW allow\n"
                + "pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null\n"
                + "pm grant $PKG android.permission.RECORD_AUDIO 2>/dev/null\n"
                + "CUR=$(settings get secure enabled_accessibility_services | tr -d '\\r')\n"
                + "case \"$CUR\" in *\"$SVC\"*) ;; null|\"\") settings put secure enabled_accessibility_services $SVC ;; *) settings put secure enabled_accessibility_services \"$CUR:$SVC\" ;; esac\n"
                + "settings put secure accessibility_enabled 1\n"
                + "settings put secure enable_notification_nowbar_test 1\n"
                + "echo done";
        String r = suRun(script);
        if (r.startsWith("ERR:") || !r.contains("done")) return L.t("修復失敗：") + r.replace("ERR:", "").trim();
        return L.t("已補回權限");
    }

    /** 只用第一個找到的 su 跑一次（不重試，避免同一個指令被執行好幾次）；不管結束碼，回傳輸出。 */
    static String suOnce(String script) {
        if (App.ctx != null && ExecMode.SHIZUKU.equals(ExecMode.get(App.ctx))) return ShizukuBackend.run(script);
        try {
            Process p = new ProcessBuilder(Root.su()).redirectErrorStream(true).start();
            p.getOutputStream().write((script + "\nexit\n").getBytes(StandardCharsets.UTF_8));
            p.getOutputStream().close();
            java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = p.getInputStream().read(buf)) > 0) o.write(buf, 0, n);
            p.waitFor();
            return o.toString("UTF-8");
        } catch (Exception e) {
            return "ERR:" + e;
        }
    }


    // ---------- API Key ----------
    /** 存 API Key 到 App 私有資料夾的 claude_key（權限 600）。不印出、不寫 log。 */
    static String saveKey(String key) { return saveKey(key, "claude_key"); }

    /** 存到 App 私有資料夾的指定檔案（claude_key／openai_key），權限 600。 */
    static String saveKey(String key, String fileName) {
        key = key == null ? "" : key.trim();
        if (key.length() < 20 || key.contains("\n") || key.contains("'") || key.contains(" ")) return L.t("金鑰格式不對（太短、有空白、換行或引號）");
        if (fileName.equals("claude_key") && key.startsWith("sk-ant-usr-")) return L.t("這是「個人 Key」（sk-ant-usr-），這個專案用它會 401；請在 Console 重建 Key：Linked account 選「未連結」、工作區選「預設工作區」");
        try {
            java.io.File f = new java.io.File(App.ctx.getFilesDir(), fileName), tmp = new java.io.File(f.getPath() + ".tmp");
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(tmp)) { out.write(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
            tmp.setReadable(false, false);
            tmp.setReadable(true, true);
            tmp.setWritable(false, false);
            tmp.setWritable(true, true);
            if (!tmp.renameTo(f)) return L.t("存檔失敗：無法覆蓋舊的 Key");
            return null;
        } catch (Exception e) {
            return L.t("存檔失敗：") + e.getMessage();
        }
    }

    // ---------- 串流執行（首次設定用） ----------

    

    /** 輸入 Claude API Key 的對話框（主畫面與設定導覽共用）。儲存成功後呼叫 after（在主執行緒）。 */
    static void askKey(final android.app.Activity a, final Runnable after) {
        final com.google.android.material.textfield.TextInputLayout til = new com.google.android.material.textfield.TextInputLayout(a);
        til.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
        til.setHint(L.t("貼上 API Key"));
        til.setEndIconMode(com.google.android.material.textfield.TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        final com.google.android.material.textfield.TextInputEditText et = new com.google.android.material.textfield.TextInputEditText(til.getContext());
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        til.addView(et);
        android.widget.LinearLayout form = new android.widget.LinearLayout(a);
        form.setOrientation(android.widget.LinearLayout.VERTICAL);
        int fp = Ui.dp(a, 20);
        form.setPadding(fp, fp / 2, fp, 0);
        form.addView(Ui.body(a, L.t("Key 只存在這支手機的 App 私有資料夾（權限 600），不會顯示、不進 repo，也不要貼給任何人。在 Console 建立時：Linked account 選「未連結」、工作區選「預設工作區」。")));
        form.addView(til);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(a).setTitle(LlmConfig.openai(a) ? L.t("OpenAI 相容 API Key") : "Claude API Key").setView(form)
                .setPositiveButton(L.t("儲存"), (d, w) -> {
                    final String k = et.getText().toString();
                    new Thread(() -> {
                        final String r = saveKey(k, LlmConfig.keyFile(a));
                        a.runOnUiThread(() -> {
                            android.widget.Toast.makeText(a, r == null ? L.t("已儲存") : r, android.widget.Toast.LENGTH_LONG).show();
                            if (r == null && after != null) after.run();
                        });
                    }).start();
                }).setNegativeButton(L.t("取消"), null).show();
    }
}
