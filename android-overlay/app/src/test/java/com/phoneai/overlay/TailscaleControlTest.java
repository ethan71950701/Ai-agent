package com.phoneai.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class TailscaleControlTest {
    @Test
    public void commandsTargetOnlyTheTailscaleReceiverWithFixedStrings() {
        List<String> on = TailscaleControl.connectCommands();
        assertEquals(2, on.size());
        assertEquals("am broadcast -a com.tailscale.ipn.CONNECT_VPN -n com.tailscale.ipn/.IPNReceiver", on.get(0));
        assertEquals("am broadcast -a com.tailscale.ipn.VPN_CONNECT -n com.tailscale.ipn/.IPNReceiver", on.get(1));
        List<String> off = TailscaleControl.disconnectCommands();
        assertEquals("am broadcast -a com.tailscale.ipn.DISCONNECT_VPN -n com.tailscale.ipn/.IPNReceiver", off.get(0));
        for (String c : on) assertTrue(c, c.matches("[A-Za-z0-9 ._/\\-]+"));   // 只有固定的字元，沒有任何外來內容
    }

    @Test
    public void rememberedCommandIsTriedFirst() {
        assertEquals(java.util.Arrays.asList(0, 1), TailscaleControl.order(-1, 2));
        assertEquals(java.util.Arrays.asList(1, 0), TailscaleControl.order(1, 2));
        assertEquals(java.util.Arrays.asList(0, 1), TailscaleControl.order(0, 2));
        assertEquals(java.util.Arrays.asList(0, 1), TailscaleControl.order(9, 2));   // 記的編號不合理：照原順序
    }
}
