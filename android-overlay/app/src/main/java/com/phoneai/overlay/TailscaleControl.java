package com.phoneai.overlay;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/**
 * 不開畫面、不點螢幕地開關 Tailscale（盡力而為，要 root）：對它的 IPNReceiver 發廣播。
 * 動作名稱在不同版本的 Tailscale 不一樣（CONNECT_VPN 或 VPN_CONNECT），兩種都試，再用「有沒有 100.x 的網路介面」確認真的開了。
 * 沒辦法保證每個版本都能用；失敗就回 false，呼叫端照原本的方式處理。
 */
final class TailscaleControl {
    private TailscaleControl() { }

    static final String PKG = "com.tailscale.ipn";

    static boolean isOn() { return TransferClient.localTailnetIp() != null; }

    static List<String> connectCommands() { return commands("CONNECT_VPN", "VPN_CONNECT"); }

    static List<String> disconnectCommands() { return commands("DISCONNECT_VPN", "VPN_DISCONNECT"); }

    private static List<String> commands(String... actions) {
        List<String> out = new ArrayList<>();
        for (String a : actions) out.add("am broadcast -a " + PKG + "." + a + " -n " + PKG + "/.IPNReceiver");
        return out;
    }

    private static android.content.SharedPreferences cfg(Context c) { return c.getSharedPreferences("cfg", Context.MODE_PRIVATE); }

    /** 試的順序：上次成功的那一種排第一（記在設定裡），其他照原順序。 */
    static List<Integer> order(int remembered, int n) {
        List<Integer> out = new ArrayList<>();
        if (remembered >= 0 && remembered < n) out.add(remembered);
        for (int i = 0; i < n; i++) if (i != remembered) out.add(i);
        return out;
    }

    /** 把 Tailscale 開起來並等它出現網路介面（最多約 waitMs，兩種指令各分一半）。本來就開著回 true；沒有 root、沒裝、開不起來回 false。要在背景執行緒呼叫。 */
    static boolean turnOn(Context c, long waitMs) {
        return turnOn(c, waitMs, null);
    }

    /** log 不是 null 的話，會把每一步發生什麼寫進去（測試用）。 */
    static boolean turnOn(Context c, long waitMs, StringBuilder log) {
        if (isOn()) { if (log != null) log.append("Tailscale 本來就開著\n"); return true; }   // 本來就開著：是使用者自己的，不標成我們開的
        if (!TailscaleLauncher.installed(c)) { if (log != null) log.append("❌ 這支手機沒有安裝 Tailscale\n"); return false; }
        if (!Root.available()) { if (log != null) log.append("❌ 沒有 root，沒辦法發廣播\n"); return false; }
        List<String> cmds = connectCommands();
        for (int idx : order(cfg(c).getInt("ts_cmd_idx", -1), cmds.size())) {
            markOurs(c);   // 廣播一送出就先記下「是我們開的」：就算 12 秒內沒連上、之後才連上，也有人負責關回去；App 被殺掉重開也記得
            String out = Maintenance.suRun(cmds.get(idx));
            if (log != null) log.append("送出：").append(cmds.get(idx)).append("\n回應：").append(out == null ? "" : out.trim().replace('\n', ' ')).append('\n');
            long until = System.currentTimeMillis() + Math.max(2000, waitMs / cmds.size());
            while (System.currentTimeMillis() < until) {
                if (isOn()) {
                    cfg(c).edit().putInt("ts_cmd_idx", idx).apply();   // 記住這一種有用
                    if (log != null) log.append("✅ 開起來了（第 ").append(idx + 1).append(" 種動作有效）\n");
                    return true;
                }
                try { Thread.sleep(400); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return isOn(); }
            }
            if (log != null) log.append("這種動作沒有讓 Tailscale 連上\n");
        }
        return isOn();
    }

    // ---- 我們開的 Tailscale，閒置一段時間後關回去（你自己開的不會動）。發送端與被叫醒的接收端共用這一套；「是我們開的」記在設定裡，App 被殺掉重開也不會漏關
    static final long IDLE_MS = 60_000;
    private static volatile boolean ours;
    private static volatile long lastUse, holdUntil;
    private static Thread watcher;
    private static int offFailures;

    /** 要不要把 Tailscale 關回去：只有「我們開的」、而且現在沒有傳輸在跑、而且閒置夠久。 */
    static boolean shouldTurnOff(boolean ours, boolean busy, long nowMs, long lastUseMs, long idleMs) {
        return ours && !busy && nowMs - lastUseMs >= idleMs;
    }

    /** 現在算不算忙：有傳輸在跑、有連線進來、或還在「等對方連進來」的保留期內。 */
    static boolean busy(boolean transferActive, int connections, long nowMs, long holdUntilMs) {
        return transferActive || connections > 0 || nowMs < holdUntilMs;
    }

    /** 「是我們開的」記號最多留這麼久：程序被殺掉沒來得及清的話，過了這段時間就當作過期（之後你自己開的 Tailscale 才不會被誤關）。 */
    static final long OURS_VALID_MS = 30 * 60_000L;

    /** 啟動時要不要接手上次的記號：有記號、而且還沒過期。 */
    static boolean oursStillValid(boolean flag, long markedAtMs, long nowMs) {
        return flag && markedAtMs > 0 && nowMs - markedAtMs >= 0 && nowMs - markedAtMs < OURS_VALID_MS;
    }

    /** 關完之後下一步：已經關了＝done；沒關掉而且還沒試滿次數＝retry；試滿了＝giveup（照實記下來，請使用者手動關）。 */
    static String afterTurnOff(boolean isOff, int failures) {
        if (isOff) return "done";
        return failures >= MAX_OFF_TRIES ? "giveup" : "retry";
    }

    static final int MAX_OFF_TRIES = 3;
    private static volatile long persistedAt;

    private static void markOurs(Context c) {
        offFailures = 0;
        ours = true;
        lastUse = System.currentTimeMillis();
        persistOurs(c);
        startWatcher(c.getApplicationContext());
    }

    private static void persistOurs(Context c) {
        persistedAt = System.currentTimeMillis();
        cfg(c).edit().putBoolean("ts_ours", true).putLong("ts_ours_at", persistedAt).apply();
    }

    private static void clearOurs(Context c) {
        ours = false;
        cfg(c).edit().putBoolean("ts_ours", false).putLong("ts_ours_at", 0).apply();
    }

    /** App 啟動時呼叫：上次是我們開的、但程序被殺掉沒來得及關，就接手繼續負責（閒置後關回去）；已經不在了就清掉記號。要在背景執行緒。 */
    static void reconcile(Context c) {
        boolean flag = cfg(c).getBoolean("ts_ours", false);
        if (!flag) return;
        if (!oursStillValid(true, cfg(c).getLong("ts_ours_at", 0), System.currentTimeMillis())) { clearOurs(c); return; }   // 記號太舊：不知道現在的 Tailscale 是誰開的，不去動它
        if (!isOn()) { clearOurs(c); return; }
        ours = true;
        lastUse = System.currentTimeMillis();
        startWatcher(c.getApplicationContext());
    }

    /** 連線前用：把 Tailscale 開起來；如果本來是關的、是我們開的，之後閒置 60 秒會自動關回去。 */
    static boolean turnOnForSession(Context c, long waitMs) { return turnOnForSession(c, waitMs, null); }

    static boolean turnOnForSession(Context c, long waitMs, StringBuilder log) {
        boolean ok = turnOn(c, waitMs, log);
        lastUse = System.currentTimeMillis();
        return ok;
    }

    /** 被叫醒的接收端：在這段時間內先不要關（對方還在連進來的路上）。 */
    static void hold(long ms) { holdUntil = System.currentTimeMillis() + ms; }

    /** 又用了一次 Tailscale 連線：重新計閒置時間。 */
    static void noteUse() { lastUse = System.currentTimeMillis(); }

    private static synchronized void startWatcher(final Context app) {
        if (watcher != null && watcher.isAlive()) return;
        watcher = new Thread(() -> {
            while (ours) {
                try { Thread.sleep(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                if (!isOn()) {   // 使用者自己關掉了，或一直沒連上
                    if (System.currentTimeMillis() - lastUse > 90_000) { clearOurs(app); return; }   // 剛送出廣播還在等它連上時先不放棄
                    continue;
                }
                boolean busy = busy(TransferNotify.active(), TransferServer.activeConnections(), System.currentTimeMillis(), holdUntil);
                if (busy) {
                    lastUse = System.currentTimeMillis();
                    if (lastUse - persistedAt > 60_000) persistOurs(app);   // 還在用：記號的時間跟著更新，不會因為傳很久而過期
                    continue;
                }
                if (shouldTurnOff(ours, false, System.currentTimeMillis(), lastUse, IDLE_MS)) {
                    boolean off = turnOff(app);   // 會等它真的斷線；沒關掉就不能清掉記號，不然之後沒人會再關
                    String next = afterTurnOff(off, ++offFailures);
                    if (next.equals("done")) {
                        clearOurs(app);
                        LifeLog.add(app, "外網：我們開的 Tailscale 閒置 " + IDLE_MS / 1000 + " 秒，已關回去");
                        return;
                    }
                    if (next.equals("giveup")) {
                        clearOurs(app);
                        LifeLog.add(app, "外網：我們開的 Tailscale 試了 " + MAX_OFF_TRIES + " 次都關不掉（沒 root、沒安裝，或被系統凍住），請手動關閉");
                        return;
                    }
                    LifeLog.add(app, "外網：Tailscale 還沒關掉，30 秒後再試（第 " + offFailures + " 次）");
                    lastUse = System.currentTimeMillis() - IDLE_MS + 30_000;   // 30 秒後再試
                }
            }
        }, "tailscale-restore");
        watcher.setDaemon(true);
        watcher.start();
    }

    /** 戳一下 Tailscale：它顯示開著、但可能被系統凍住（收不到封包）時，對它發一次連線廣播讓它醒過來。已經連著不會有任何變化。不等結果。 */
    static void nudge(Context c) {
        if (!TailscaleLauncher.installed(c) || !Root.available()) return;
        List<String> cmds = connectCommands();
        Maintenance.suRun(cmds.get(Math.max(0, Math.min(cmds.size() - 1, cfg(c).getInt("ts_cmd_idx", 0)))));
    }

    /** 給使用者按的測試：把 Tailscale 開起來再關回去（本來就開著就只報告，不去動它），回傳一段說明。要在背景執行緒呼叫。 */
    static String selfTest(Context c) {
        StringBuilder log = new StringBuilder();
        boolean wasOn = isOn();
        boolean ok = turnOn(c, 16000, log);
        if (!wasOn && ok) {
            turnOff(c);
            long until = System.currentTimeMillis() + 6000;
            while (System.currentTimeMillis() < until && isOn()) {
                try { Thread.sleep(400); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
            log.append(isOn() ? "⚠️ 關不回去（斷線的廣播沒有生效）\n" : "✅ 也關回去了\n");
        } else if (!ok) {
            log.append("\n可能的原因：\n• Tailscale 的版本不支援這個廣播（不同版本的動作名稱不一樣）\n• 小米／三星要先給 Tailscale「自啟動」和電池「無限制」，系統才讓它收廣播\n• Tailscale 還沒登入、或還沒給過 VPN 連線授權（先手動連一次）\n");
        }
        return log.toString().trim();
    }

    /** 關掉（我們自己開的才關回去）。盡力而為。 */
    static boolean turnOff(Context c) {
        if (!isOn()) return true;
        if (!TailscaleLauncher.installed(c) || !Root.available()) return false;
        for (String cmd : disconnectCommands()) Maintenance.suRun(cmd);
        long until = System.currentTimeMillis() + 6000;   // 等它真的斷線再回報
        while (System.currentTimeMillis() < until) {
            if (!isOn()) return true;
            try { Thread.sleep(300); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        return !isOn();
    }
}
