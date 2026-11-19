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

    static volatile int W = 720, H = 1200;   // 預設直式；要開的 App 在清單裡宣告橫式（遊戲常見）時，建立背景螢幕就用橫式 1200x720（AI 看的截圖本來就縮小，預覽窗也不大）
    static final int DPI = 280;

    /** 這個 App 的啟動畫面在 AndroidManifest 裡宣告橫式（橫式、感應橫式、反向橫式、使用者橫式）。沒宣告或程式裡才轉方向的看不出來，回 false。 */
    static boolean isLandscapeApp(Context c, String pkg) {
        try {
            if (pkg == null || pkg.isEmpty()) return false;
            android.content.pm.PackageManager pm = c.getPackageManager();
            android.content.Intent li = pm.getLaunchIntentForPackage(pkg);
            android.content.pm.ActivityInfo ai = li == null ? null : li.resolveActivityInfo(pm, 0);
            if (ai == null) return false;
            int o = ai.screenOrientation;   // 0 LANDSCAPE、6 SENSOR_LANDSCAPE、8 REVERSE_LANDSCAPE、11 USER_LANDSCAPE
            return o == 0 || o == 6 || o == 8 || o == 11;
        } catch (Throwable t) { return false; }
    }

    static volatile boolean active;
    static volatile int id;
    static volatile String sf = "";
    private static volatile Set<String> baseline = new HashSet<>();   // 建立背景螢幕前就有的螢幕編號

    /**
     * 截圖編號失效（診斷：「Display Id … is not valid」，預覽窗接上去後主機重建了背景螢幕，SurfaceFlinger 編號換了）：
     * 重新查，挑一個建立前沒有的虛擬螢幕編號。有換成新的回 true。
     */
    static synchronized boolean refreshSf() {
        if (!active) return false;
        try {
            Set<String> now = sfIds(Maintenance.suRun("dumpsys SurfaceFlinger --display-id"));
            now.removeAll(baseline);
            if (now.isEmpty() || now.contains(sf) && now.size() == 1) return false;
            String pick = now.contains(sf) ? sf : now.iterator().next();
            if (pick.equals(sf)) return false;
            sf = pick;
            OverlayService.vdSf = pick;
            LifeLog.add(App.ctx, "背景螢幕截圖編號換成 " + pick);
            return true;
        } catch (Exception e) { return false; }
    }

    private static Process host;

    static boolean wanted(Context c) {
        android.content.SharedPreferences p = c.getSharedPreferences("cfg", Context.MODE_PRIVATE);
        return p.getBoolean("bg_feature", true) && p.getBoolean("bg_on", false) && Root.isRoot();   // bg_feature＝設定裡的功能開關（有 root 預設開）；bg_on＝助理面板上的按鈕（每次自己決定，預設關）
    }

    /** 建立背景螢幕；成功回 true。在背景執行緒呼叫（最多等約 15 秒）。 */
    static synchronized boolean start(Context c) { return start(c, null); }

    /** pkg：任務一開始要打開的 App（可為 null）；它是橫式的就建立橫式背景螢幕，橫式遊戲才不會被轉歪。 */
    static synchronized boolean start(Context c, String pkg) {
        stopQuietly();
        boolean landscape = isLandscapeApp(c, pkg);
        W = landscape ? 1200 : 720;
        H = landscape ? 720 : 1200;
        try {
            Set<String> before = sfIds(Maintenance.suRun("dumpsys SurfaceFlinger --display-id"));
            baseline = new HashSet<>(before);
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
                        // 橫式遊戲：叫系統「不理會 App 要求的方向」，讓橫式 App 在直式背景螢幕上維持正的（上下留黑邊），
                        // 不會被轉 90 度、讓預覽整個歪掉（使用者以前要把手機橫過來才看得懂）。指令不支援也沒關係，結果記進診斷頁的最近事件。
                        try {
                            if (W > H || !c.getSharedPreferences("cfg", Context.MODE_PRIVATE).getBoolean("bg_ignore_orient", true)) throw new Exception("off");   // 橫式背景螢幕不需要忽略方向
                            String r = Maintenance.suRun("wm set-ignore-orientation-request -d " + id + " true 2>&1");
                            LifeLog.add(c, "背景螢幕：忽略 App 的方向要求 → " + (r == null || r.trim().isEmpty() ? "已設定" : r.trim().replace('\n', ' ')));
                        } catch (Throwable ignored2) { }
                        return true;
                    }
                }
                Thread.sleep(250);
            }
        } catch (Exception ignored) { }
        stopQuietly();
        return false;
    }

    static synchronized void stop() { lingerUntil = 0; stopQuietly(); }

    // ---------- 任務結束後保留一段時間 ----------
    private static volatile long lingerUntil;   // 0 = 沒有在保留；否則是預定收掉的時間（uptime）
    private static final android.os.Handler LINGER = new android.os.Handler(android.os.Looper.getMainLooper());

    /** 設定的保留秒數（任務結束後背景螢幕與預覽窗再留多久，你碰預覽窗就重新計時）；0 = 馬上收。 */
    static int keepSeconds(Context c) { return c.getSharedPreferences("cfg", Context.MODE_PRIVATE).getInt("bg_keep_s", 15); }

    /** 任務結束：背景螢幕不馬上收，留 keepSeconds 秒讓你看結果、繼續操作；你碰預覽窗會重新計時。 */
    static void stopLater(Context c) {
        int s = keepSeconds(c);
        if (s <= 0 || !active) { stop(); return; }
        lingerUntil = android.os.SystemClock.uptimeMillis() + s * 1000L;
        scheduleLinger();
    }

    private static void scheduleLinger() {
        LINGER.removeCallbacksAndMessages(null);
        long wait = Math.max(200, lingerUntil - android.os.SystemClock.uptimeMillis());
        LINGER.postDelayed(() -> {
            if (lingerUntil == 0) return;
            if (android.os.SystemClock.uptimeMillis() >= lingerUntil) {
                new Thread(BgDisplay::stop, "bg-linger-stop").start();   // 收掉要等程序結束，不要卡主執行緒
            } else scheduleLinger();
        }, wait);
    }

    /** 你碰了預覽窗（或在上面操作）：保留中就重新計時。 */
    static void touched(Context c) {
        if (lingerUntil == 0) return;
        lingerUntil = android.os.SystemClock.uptimeMillis() + Math.max(5, keepSeconds(c)) * 1000L;
    }

    /** 新任務開始前：還在保留的舊背景螢幕要收掉，不然新任務的指令會被改寫到舊的背景螢幕上。 */
    static void stopIfLingering() {
        if (lingerUntil != 0) stop();
    }

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
        RippleAccessibilityService.hidePreview();   // 背景螢幕沒了：預覽窗跟著關（以前按停止後預覽窗還留著）
        if (!OverlayService.vdSf.isEmpty()) {
            OverlayService.vdSf = "";
            OverlayService.vdId = 0;
            OverlayService.refreshNotification();
        }
    }

    /** 自我檢查（設定裡的「測試背景螢幕」）：建立 → 在上面開「設定」→ 截圖 → 判斷是不是全黑 → 收掉。回傳給人看的結果。 */
    static volatile android.graphics.Bitmap lastTestShot;   // 測試時截到的畫面，給設定頁的結果視窗顯示

    static String selfTest(Context c) {
        lastTestShot = null;
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
                lastTestShot = bmp;
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
