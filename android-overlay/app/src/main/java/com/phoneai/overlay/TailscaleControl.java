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

    /** 把 Tailscale 開起來並等它出現網路介面（最多 waitMs）。本來就開著回 true；沒有 root、沒裝、開不起來回 false。要在背景執行緒呼叫。 */
    static boolean turnOn(Context c, long waitMs) {
        if (isOn()) return true;
        if (!TailscaleLauncher.installed(c) || !Root.available()) return false;
        for (String cmd : connectCommands()) Maintenance.suRun(cmd);
        long until = System.currentTimeMillis() + waitMs;
        while (System.currentTimeMillis() < until) {
            if (isOn()) return true;
            try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        return isOn();
    }

    /** 關掉（我們自己開的才關回去）。盡力而為。 */
    static void turnOff(Context c) {
        if (!TailscaleLauncher.installed(c) || !Root.available()) return;
        for (String cmd : disconnectCommands()) Maintenance.suRun(cmd);
    }
}
