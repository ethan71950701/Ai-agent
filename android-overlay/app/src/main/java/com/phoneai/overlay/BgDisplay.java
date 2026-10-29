package com.phoneai.overlay;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 背景模式：用 root 建立「看不見的虛擬螢幕」（DirectDisplay 主機），任務在那個螢幕上執行，不動使用者目前的畫面。
 * 從舊的 Termux 版（vdisplay.py、agent2.py）移植過來。只有 root 才能用；只用截圖模式（元件清單與巨集讀的是主螢幕，這裡不適用）。
 * 任務期間 active = true，AgentLoop 的 input、截圖、開 App 指令會改指定這個螢幕；任務結束就收掉（螢幕上的 App 一起消失）。
 */
final class BgDisplay {
    private BgDisplay() { }

    static final int W = 720, H = 1200, DPI = 280;   // 舊版預設（AI 看的截圖本來就縮小，預覽窗也不大）

    static volatile boolean active;
    static volatile int id;
    static volatile String sf = "";

    private static Process host;

    static boolean wanted(Context c) {
        android.content.SharedPreferences p = c.getSharedPreferences("cfg", Context.MODE_PRIVATE);
        return p.getBoolean("bg_feature", true) && p.getBoolean("bg_on", false) && Root.isRoot();   // bg_feature＝設定裡的功能開關（有 root 預設開）；bg_on＝助理面板上的按鈕（每次自己決定，預設關）
    }

    /** 建立背景螢幕；成功回 true。在背景執行緒呼叫（最多等約 15 秒）。 */
    static synchronized boolean start(Context c) {
        stopQuietly();
        try {
            Set<String> before = sfIds(Maintenance.suRun("dumpsys SurfaceFlinger --display-id"));
            String cmd = "CLASSPATH=" + c.getApplicationInfo().sourceDir + " exec app_process / com.phoneai.overlay.DirectDisplay --host " + W + " " + H + " " + DPI;
            host = new ProcessBuilder(Root.su(), "-c", cmd).redirectErrorStream(true).start();
            final int[] got = {-1};
            final Process p = host;
            Thread t = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        int n = parseDisplayLine(line);
                        if (n >= 0) got[0] = n;
                    }
                } catch (Exception ignored) { }
            }, "bgdisplay-out");
            t.setDaemon(true);
            t.start();
            for (int i = 0; i < 60 && p.isAlive(); i++) {
                if (got[0] >= 0) {
                    Set<String> now = sfIds(Maintenance.suRun("dumpsys SurfaceFlinger --display-id"));
                    now.removeAll(before);
                    if (!now.isEmpty()) {
                        id = got[0];
                        sf = now.iterator().next();
                        active = true;
                        OverlayService.vdSf = sf;   // Now Bar 的「點一下顯示預覽」與接手要用
                        OverlayService.vdId = id;
                        OverlayService.vdW = W;
                        OverlayService.vdH = H;
                        OverlayService.refreshNotification();
                        return true;
                    }
                }
                Thread.sleep(250);
            }
        } catch (Exception ignored) { }
        stopQuietly();
        return false;
    }

    static synchronized void stop() { stopQuietly(); }

    private static void stopQuietly() {
        active = false;
        id = 0;
        sf = "";
        Process p = host;
        host = null;
        if (p != null) {
            try { p.getOutputStream().close(); } catch (Exception ignored) { }   // 關掉標準輸入，主機就會釋放螢幕並結束
            try { if (!p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) p.destroy(); } catch (Exception ignored) { }
        }
        try { Maintenance.suRun("pkill -f com.phoneai.overlay.DirectDisplay"); } catch (Exception ignored) { }
        if (!OverlayService.vdSf.isEmpty()) {
            OverlayService.vdSf = "";
            OverlayService.vdId = 0;
            OverlayService.refreshNotification();
        }
    }

    /** 自我檢查（設定裡的「測試背景螢幕」）：建立 → 在上面開「設定」→ 截圖 → 判斷是不是全黑 → 收掉。回傳給人看的結果。 */
    static String selfTest(Context c) {
        StringBuilder sb = new StringBuilder();
        if (!Root.isRoot()) return L.t("背景螢幕需要 root。");
        boolean ok = start(c);
        if (!ok) return L.t("建立背景螢幕失敗（這支手機或 root 管理器可能不支援）。");
        try {
            sb.append(L.t("已建立：系統編號 ")).append(id).append(L.t("，截圖編號 ")).append(sf).append('\n');
            Maintenance.suRun(launch("com.android.settings", id));
            Thread.sleep(3000);
            Process p = new ProcessBuilder(Root.su(), "-c", "screencap -d " + sf + " -p").redirectErrorStream(false).start();
            p.getOutputStream().close();
            byte[] png = p.getInputStream().readAllBytes();
            p.waitFor();
            android.graphics.Bitmap bmp = png.length < 100 ? null : android.graphics.BitmapFactory.decodeByteArray(png, 0, png.length);
            if (bmp == null) {
                sb.append(L.t("截圖失敗：讀不到背景螢幕的畫面。"));
            } else {
                Set<Integer> colors = new HashSet<>();
                for (int y = 0; y < bmp.getHeight(); y += 17)
                    for (int x = 0; x < bmp.getWidth(); x += 13) colors.add(bmp.getPixel(x, y));
                sb.append(L.t("截圖大小 ")).append(bmp.getWidth()).append('x').append(bmp.getHeight()).append(L.t("，顏色種類 ")).append(colors.size())
                        .append(colors.size() < 8 ? L.t("：幾乎是一片純色（可能是黑的，背景螢幕沒有畫面）") : L.t("：有畫面，應該可以用"));
            }
        } catch (Exception e) {
            sb.append(L.t("檢查出錯：")).append(e);
        } finally {
            stop();
        }
        return sb.toString();
    }

    // ---------- 純邏輯（單元測試用） ----------

    /** DirectDisplay 就緒時印 "display <編號>"。 */
    static int parseDisplayLine(String line) {
        Matcher m = Pattern.compile("^display (\\d+)").matcher(line == null ? "" : line.trim());
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    /** dumpsys SurfaceFlinger --display-id 裡的虛擬螢幕編號（截圖 screencap -d 要用這個編號）。 */
    static Set<String> sfIds(String dump) {
        Set<String> out = new HashSet<>();
        Matcher m = Pattern.compile("Display (\\d+) \\(Virtual display\\)").matcher(dump == null ? "" : dump);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    /** 把給主螢幕的 shell 指令改成指定背景螢幕：input 加 -d、開 App 改用 am start --display、開網址加 --display。音量鍵不改。 */
    static String rewrite(String cmd, int displayId) {
        String out = cmd.replaceAll("\\binput (tap|swipe|draganddrop|text|keycombination|motionevent)\\b", "input -d " + displayId + " $1");
        out = out.replaceAll("\\binput keyevent (?!24\\b|25\\b)", "input -d " + displayId + " keyevent ");
        Matcher m = Pattern.compile("monkey -p ([A-Za-z0-9._]+) -c android\\.intent\\.category\\.LAUNCHER 1").matcher(out);
        StringBuffer sb = new StringBuffer();
        while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(launch(m.group(1), displayId)));   // launch() 裡有 $，不能直接當取代字串
        m.appendTail(sb);
        out = sb.toString();
        out = out.replace("am start -a android.intent.action.VIEW", "am start --display " + displayId + " -a android.intent.action.VIEW");
        return out;
    }

    /** 在指定螢幕開 App：先查啟動入口再 am start -n（monkey 沒辦法指定螢幕）。 */
    static String launch(String pkg, int displayId) {
        return "COMP=$(cmd package resolve-activity --brief -c android.intent.category.LAUNCHER " + pkg + " | tail -n 1); am start --display " + displayId + " -n \"$COMP\"";
    }
}
