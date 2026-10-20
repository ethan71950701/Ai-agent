package com.phoneai.overlay;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

/**
 * 把指令交給 Termux 裡的 assistant.py（RUN_COMMAND：以 Termux 自己的身分在背景執行，不開 Termux 畫面）。
 * 前提：Termux 的 ~/.termux/termux.properties 有 allow-external-apps=true，且本 App 取得 com.termux.permission.RUN_COMMAND
 * （overlay_install.sh 會處理）。文字用位置參數 $1 傳給 bash，不經過 shell 解析，避免注入。
 */
public final class CommandSender {
    static final String TERMUX_HOME = "/data/data/com.termux/files/home";
    static final String TERMUX_BIN = "/data/data/com.termux/files/usr/bin";
    static final String PERM = "com.termux.permission.RUN_COMMAND";

    private CommandSender() { }

    /** 面板/主畫面的「背景模式」開關開著，就讓 agent2 在第二螢幕操作（AGENT_BG=1）；背景視窗小，截圖寬度用 540 較看得清。 */
    /** 新任務開始前，把上一次的 assistant.log 存進 assistant.hist（只留最後 30 萬字），App「上一次任務的過程」頁可以看更早的。 */
    private static final String ARCHIVE = "{ [ -s \"$HOME/assistant.log\" ] && { cat \"$HOME/assistant.log\" >> \"$HOME/assistant.hist\"; "
            + "echo '=========== 以上是上一個任務 ===========' >> \"$HOME/assistant.hist\"; "
            + "tail -c 300000 \"$HOME/assistant.hist\" > \"$HOME/assistant.hist.t\" && mv -f \"$HOME/assistant.hist.t\" \"$HOME/assistant.hist\"; }; } 2>/dev/null; ";

    private static String bgEnv(Context c) {
        boolean on = c.getSharedPreferences("cfg", Context.MODE_PRIVATE).getBoolean("bg_on", false);
        // PYTHONUNBUFFERED：輸出寫進 assistant.log 時不緩存，Termux 裡 tail -f 才會即時看到每一步
        boolean tree = c.getSharedPreferences("cfg", Context.MODE_PRIVATE).getBoolean("tree_on", false);   // 實驗：不附截圖，改附畫面元件清單（省 Token）
        return "export PYTHONUNBUFFERED=1; " + (on ? "export AGENT_BG=1 AGENT_WIDTH=540; " : "") + (tree ? "export AGENT_TREE=1; export AGENT_TREE_MODEL=haiku; " : "");
    }

    /** 成功回傳 null，失敗回傳給人看的原因。 */
    public static String send(Context c, String text) {
        text = text == null ? "" : text.trim();
        if (text.isEmpty()) return "沒有內容";
        if (c.checkSelfPermission(PERM) != PackageManager.PERMISSION_GRANTED) {
            return viaRoot(c, text);  // 沒設好 RUN_COMMAND 時，用 root 以 Termux 身分執行（App 已有 root）
        }
        Intent i = new Intent("com.termux.RUN_COMMAND");  // 先別急，下面成功送出後才叫出 Now Bar/光暈
        i.setClassName("com.termux", "com.termux.app.RunCommandService");
        i.putExtra("com.termux.RUN_COMMAND_PATH", TERMUX_BIN + "/bash");
        i.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{
                "-c", ARCHIVE + bgEnv(c) + "python \"$HOME/assistant.py\" --once \"$1\" > \"$HOME/assistant.log\" 2>&1", "_", text});
        i.putExtra("com.termux.RUN_COMMAND_WORKDIR", TERMUX_HOME);
        i.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
        try {
            c.startForegroundService(i);
            begin(c);
            return null;
        } catch (SecurityException e) {
            return viaRoot(c, text);  // Termux 沒開 allow-external-apps
        } catch (Exception e) {
            return viaRoot(c, text);
        }
    }

    /** 送出後馬上叫出 Now Bar（準備中）與光暈。失敗不影響送出。 */
    static void begin(Context c) {
        try {
            c.startForegroundService(new Intent(c, OverlayService.class).setAction(OverlayService.ACTION_BEGIN));
        } catch (Exception ignored) { }
    }

    private static String sh(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    /**
     * 備援：用 root 切到 Termux 的 uid 再跑（Termux:API 的 termux-notification、termux-tts-speak 等要用 Termux 自己的身分才能用，
     * 所以不直接以 root 跑 python）。文字用單引號跳脫後嵌入；指令從 su 的標準輸入餵進去，不經過 App 端的 shell。
     * 此路徑未實測；失敗時結果只會出現在 ~/assistant.log。
     */
    private static String viaRoot(Context c, String text) {
        String inner = "export HOME=" + TERMUX_HOME + " PREFIX=/data/data/com.termux/files/usr"
                + " PATH=" + TERMUX_BIN + ":/system/bin LD_LIBRARY_PATH=/data/data/com.termux/files/usr/lib"
                + " TMPDIR=/data/data/com.termux/files/usr/tmp; cd \"$HOME\"; " + bgEnv(c)
                + ARCHIVE + "python \"$HOME/assistant.py\" --once " + sh(text) + " > \"$HOME/assistant.log\" 2>&1";
        String script = "U=$(stat -c %u /data/data/com.termux)\nsu \"$U\" -c " + sh(inner) + "\n";
        new Thread(() -> {
            try {
                Process p = new ProcessBuilder(Root.su()).redirectErrorStream(true).start();
                p.getOutputStream().write(script.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                p.getOutputStream().close();
                p.waitFor();
            } catch (Exception e) {
                android.util.Log.e(OverlayService.TAG, "viaRoot", e);
            }
        }).start();
        begin(c);
        return null;
    }
}
