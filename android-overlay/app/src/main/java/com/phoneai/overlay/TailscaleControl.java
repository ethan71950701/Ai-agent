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
        if (isOn()) { if (log != null) log.append("Tailscale 本來就開著\n"); return true; }
        if (!TailscaleLauncher.installed(c)) { if (log != null) log.append("❌ 這支手機沒有安裝 Tailscale\n"); return false; }
        if (!Root.available()) { if (log != null) log.append("❌ 沒有 root，沒辦法發廣播\n"); return false; }
        List<String> cmds = connectCommands();
        for (int idx : order(cfg(c).getInt("ts_cmd_idx", -1), cmds.size())) {
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
    static void turnOff(Context c) {
        if (!TailscaleLauncher.installed(c) || !Root.available()) return;
        for (String cmd : disconnectCommands()) Maintenance.suRun(cmd);
    }
}
