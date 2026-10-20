package com.phoneai.overlay;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import java.nio.charset.StandardCharsets;

/**
 * 原本要在 Termux 打指令的維護工作，改成 App 內按鈕：更新、存 GitHub 權杖、修復權限、自我檢查。
 * 需要 root 的部分用 su 從標準輸入餵腳本（內容不出現在指令列）；Termux 的檔案要用「跳到全域掛載空間」的 su 寫法才看得到（跟記憶頁一樣）。
 */
final class Maintenance {
    private Maintenance() { }

    static final String HOME = CommandSender.TERMUX_HOME;
    static final String TOKEN = HOME + "/.gh_token";
    private static final String[][] SU = {{Root.su(), "-mm"}, {Root.su(), "-M"}, {Root.su()}};

    /** 用 root 跑腳本（標準輸入餵進去）；回傳輸出。依序試幾種 su 寫法，第一個「結束碼 0」的就用。 */
    static String suRun(String script) {
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
        return "ERR:" + last;
    }

    /** 診斷用：列出每個 su 路徑實際跑 id 的結果（給使用者回報用）。 */
    static String rootDiag() {
        StringBuilder sb = new StringBuilder("診斷（每個 su 路徑跑 id 的結果）：");
        String[] paths = {Root.su(), "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "/data/adb/ksu/bin/su", "/debug_ramdisk/su", "/vendor/bin/su", "su"};
        for (String p : paths) {
            try {
                Process pr = new ProcessBuilder(p, "-c", "id -u").redirectErrorStream(true).start();
                pr.getOutputStream().close();
                boolean done = pr.waitFor(8, java.util.concurrent.TimeUnit.SECONDS);
                String out = done ? new String(pr.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim() : "（逾時，可能正在等授權視窗）";
                if (!done) pr.destroy();
                sb.append("\n").append(p).append(" → ").append(done ? "結束碼 " + pr.exitValue() + "：" + out : out);
            } catch (Exception e) {
                sb.append("\n").append(p).append(" → 無法執行（").append(e.getMessage()).append("）");
            }
        }
        return sb.toString();
    }

    static boolean tokenSet() {
        String r = suRun("[ -s " + TOKEN + " ] && echo yes");
        return r.contains("yes");
    }

    /** 把權杖存到 Termux 的 ~/.gh_token（擁有者設成 Termux、權限 600）。權杖只經過標準輸入，不印出、不存進 App。 */
    static String saveToken(String token) {
        token = token == null ? "" : token.trim();
        if (token.isEmpty() || token.contains("\n") || token.contains("'")) return "權杖格式不對（空的、有換行或引號）";
        String r = suRun("U=$(stat -c %u /data/data/com.termux)\n"
                + "printf %s '" + token + "' > " + TOKEN + "\n"
                + "chown $U:$U " + TOKEN + "; chmod 600 " + TOKEN + "\n[ -s " + TOKEN + " ] && echo saved");
        return r.contains("saved") ? null : "存檔失敗：" + (r.startsWith("ERR:") ? r.substring(4).trim() : r.trim());
    }

    /** 用 root 把 App 需要的權限補回來（等同 overlay_install.sh 裡跟權限有關的部分）；回傳給人看的結果。 */
    static String repairPermissions() {
        final String pkg = "com.phoneai.overlay";
        String svc = pkg + "/" + pkg + ".RippleAccessibilityService";
        String script = "PKG=" + pkg + "\nSVC=" + svc + "\n"
                + "appops set $PKG SYSTEM_ALERT_WINDOW allow\n"
                + "pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null\n"
                + "pm grant $PKG android.permission.RECORD_AUDIO 2>/dev/null\n"
                + "pm grant $PKG com.termux.permission.RUN_COMMAND 2>/dev/null\n"
                + "CUR=$(settings get secure enabled_accessibility_services | tr -d '\\r')\n"
                + "case \"$CUR\" in *\"$SVC\"*) ;; null|\"\") settings put secure enabled_accessibility_services $SVC ;; *) settings put secure enabled_accessibility_services \"$CUR:$SVC\" ;; esac\n"
                + "settings put secure accessibility_enabled 1\n"
                + "settings put secure enable_notification_nowbar_test 1\n"
                + "U=$(stat -c %u /data/data/com.termux)\n"
                + "P=" + HOME + "/.termux/termux.properties\n"
                + "mkdir -p " + HOME + "/.termux\n"
                + "grep -q '^allow-external-apps *= *true' $P 2>/dev/null || { sed -i '/^#\\? *allow-external-apps/d' $P 2>/dev/null; echo 'allow-external-apps=true' >> $P; chown $U:$U $P; echo TERMUX_PROP_CHANGED; }\n"
                + "echo done";
        String r = suRun(script);
        if (r.startsWith("ERR:") || !r.contains("done")) return "修復失敗：" + r.replace("ERR:", "").trim();
        return "已補回權限" + (r.contains("TERMUX_PROP_CHANGED") ? "（Termux 設定有更新，請把 Termux 完全關掉再開一次才會生效）" : "");
    }

    /** 只用第一個找到的 su 跑一次（不重試，避免同一個指令被執行好幾次）；不管結束碼，回傳輸出。 */
    static String suOnce(String script) {
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

    /** 在 Termux 背景跑 bash 指令，輸出寫進 ~/<logName>。成功回傳 null。 */
    static String runInTermux(Context c, String bashCmd, String logName) {
        String full = "export PYTHONUNBUFFERED=1 UPDATE_FROM_APP=1; " + bashCmd + " > \"$HOME/" + logName + "\" 2>&1; echo '=== 結束 ===' >> \"$HOME/" + logName + "\"";
        if (c.checkSelfPermission(CommandSender.PERM) == PackageManager.PERMISSION_GRANTED) {
            try {
                Intent i = new Intent("com.termux.RUN_COMMAND");
                i.setClassName("com.termux", "com.termux.app.RunCommandService");
                i.putExtra("com.termux.RUN_COMMAND_PATH", CommandSender.TERMUX_BIN + "/bash");
                i.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-c", full});
                i.putExtra("com.termux.RUN_COMMAND_WORKDIR", HOME);
                i.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
                c.startForegroundService(i);
                return null;
            } catch (Exception ignored) { }
        }
        // 備援：root 切到 Termux 的 uid 執行
        String inner = "export HOME=" + HOME + " PREFIX=/data/data/com.termux/files/usr PATH=" + CommandSender.TERMUX_BIN
                + ":/system/bin LD_LIBRARY_PATH=/data/data/com.termux/files/usr/lib TMPDIR=/data/data/com.termux/files/usr/tmp; cd \"$HOME\"; " + full;
        final String script = "U=$(stat -c %u /data/data/com.termux)\nsu \"$U\" -c '" + inner.replace("'", "'\\''") + "' &\n";
        new Thread(() -> suRun(script), "termux-root").start();
        return null;
    }

    // ---------- API Key ----------
    static final String KEY = HOME + "/.claude_key";

    static boolean keySet() {
        return suRun("[ -s " + KEY + " ] && echo yes").contains("yes");
    }

    /** 存 API Key 到 ~/.claude_key（擁有者 Termux、權限 600）。只經標準輸入；不印出、不存進 App、不寫 log。 */
    static String saveKey(String key) {
        key = key == null ? "" : key.trim();
        if (key.length() < 20 || key.contains("\n") || key.contains("'") || key.contains(" ")) return "金鑰格式不對（太短、有空白、換行或引號）";
        if (key.startsWith("sk-ant-usr-")) return "這是「個人 Key」（sk-ant-usr-），這個專案用它會 401；請在 Console 重建 Key：Linked account 選「未連結」、工作區選「預設工作區」";
        String r = suRun("U=$(stat -c %u /data/data/com.termux)\n"
                + "printf %s '" + key + "' > " + KEY + "\n"
                + "chown $U:$U " + KEY + "; chmod 600 " + KEY + "\n[ -s " + KEY + " ] && echo saved");
        return r.contains("saved") ? null : "存檔失敗：" + (r.startsWith("ERR:") ? r.substring(4).trim() : r.trim());
    }

    // ---------- 串流執行（首次設定用） ----------

    /** 跑 root 腳本並逐行回報輸出；回傳結束碼（-1 = 起不來）。 */
    static int suStream(String script, java.util.function.Consumer<String> line) {
        for (String[] cmd : SU) {
            try {
                Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
                p.getOutputStream().write((script + "\nexit\n").getBytes(StandardCharsets.UTF_8));
                p.getOutputStream().close();
                java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
                String l;
                while ((l = r.readLine()) != null) line.accept(l);
                int code = p.waitFor();
                if (code == 0 || cmd.length == 1) return code;
            } catch (Exception e) {
                line.accept("（執行失敗：" + e + "）");
            }
        }
        return -1;
    }

    /** 以 Termux 自己的身分（uid）執行 bash 指令，環境變數與 Termux 一致。 */
    /** 同 termuxScript，但額外加上網路群組 3003（inet）。App 的 root 切換身份時不會自動帶上它，所以 curl／pkg 會連不到網路。 */
    static String termuxScriptNet(String bashCmd) {
        String inner = "export HOME=" + HOME + " PREFIX=/data/data/com.termux/files/usr PATH=" + CommandSender.TERMUX_BIN
                + ":/system/bin LD_LIBRARY_PATH=/data/data/com.termux/files/usr/lib TMPDIR=/data/data/com.termux/files/usr/tmp LANG=en_US.UTF-8; cd \"$HOME\"; " + bashCmd;
        return "U=$(stat -c %u /data/data/com.termux)\nsu -g \"$U\" -G 3003 \"$U\" -c '" + inner.replace("'", "'\\''") + "'\n";
    }

    static String termuxScript(String bashCmd) {
        String inner = "export HOME=" + HOME + " PREFIX=/data/data/com.termux/files/usr PATH=" + CommandSender.TERMUX_BIN
                + ":/system/bin LD_LIBRARY_PATH=/data/data/com.termux/files/usr/lib TMPDIR=/data/data/com.termux/files/usr/tmp LANG=en_US.UTF-8; cd \"$HOME\"; " + bashCmd;
        return "U=$(stat -c %u /data/data/com.termux)\nsu \"$U\" -c '" + inner.replace("'", "'\\''") + "'\n";
    }

    /** 輸入 Claude API Key 的對話框（主畫面與設定導覽共用）。儲存成功後呼叫 after（在主執行緒）。 */
    static void askKey(final android.app.Activity a, final Runnable after) {
        final com.google.android.material.textfield.TextInputLayout til = new com.google.android.material.textfield.TextInputLayout(a);
        til.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
        til.setHint("貼上 API Key");
        til.setEndIconMode(com.google.android.material.textfield.TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        final com.google.android.material.textfield.TextInputEditText et = new com.google.android.material.textfield.TextInputEditText(til.getContext());
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        til.addView(et);
        android.widget.LinearLayout form = new android.widget.LinearLayout(a);
        form.setOrientation(android.widget.LinearLayout.VERTICAL);
        int fp = Ui.dp(a, 20);
        form.setPadding(fp, fp / 2, fp, 0);
        form.addView(Ui.body(a, "Key 只存在這支手機 Termux 的 ~/.claude_key（權限 600），不會顯示、不存進 App、不進 repo，也不要貼給任何人。在 Console 建立時：Linked account 選「未連結」、工作區選「預設工作區」。"));
        form.addView(til);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(a).setTitle("Claude API Key").setView(form)
                .setPositiveButton("儲存", (d, w) -> {
                    final String k = et.getText().toString();
                    new Thread(() -> {
                        final String r = saveKey(k);
                        a.runOnUiThread(() -> {
                            android.widget.Toast.makeText(a, r == null ? "已儲存" : r, android.widget.Toast.LENGTH_LONG).show();
                            if (r == null && after != null) after.run();
                        });
                    }).start();
                }).setNegativeButton("取消", null).show();
    }
}
