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

    @Test
    public void connectErrorsAreClassified() {
        assertEquals("refused", FcmLab.classifyConnectError(new java.net.ConnectException("failed to connect to /100.1.2.3 (port 47321) after 5000ms: isConnectionFailed: ECONNREFUSED (Connection refused)")));
        assertEquals("timeout", FcmLab.classifyConnectError(new java.net.SocketTimeoutException("failed to connect to /100.1.2.3 (port 47321) after 5000ms")));
        assertEquals("timeout", FcmLab.classifyConnectError(new java.io.IOException("wrapped", new java.net.SocketTimeoutException("connect timed out"))));
        assertEquals("noroute", FcmLab.classifyConnectError(new java.net.NoRouteToHostException("No route to host")));
        assertEquals("noroute", FcmLab.classifyConnectError(new java.net.ConnectException("connect failed: ENETUNREACH (Network is unreachable)")));
        assertEquals("other", FcmLab.classifyConnectError(new java.io.IOException("something else")));
        assertEquals("other", FcmLab.classifyConnectError(null));
    }

    @Test
    public void onlyOurOwnIdleTailscaleIsTurnedOff() {
        assertTrue(TailscaleControl.shouldTurnOff(true, false, 100_000, 30_000, 60_000));    // 我們開的、沒在傳、閒置 70 秒
        assertEquals(false, TailscaleControl.shouldTurnOff(true, false, 80_000, 30_000, 60_000));    // 才閒置 50 秒
        assertEquals(false, TailscaleControl.shouldTurnOff(true, true, 500_000, 30_000, 60_000));    // 還在傳
        assertEquals(false, TailscaleControl.shouldTurnOff(false, false, 500_000, 30_000, 60_000));  // 使用者自己開的：不動
        assertTrue(TailscaleControl.shouldTurnOff(true, false, 90_000, 30_000, 60_000));     // 剛好 60 秒
    }

    @Test
    public void busyCountsTransfersConnectionsAndTheWaitingHold() {
        assertTrue(TailscaleControl.busy(true, 0, 1000, 0));
        assertTrue(TailscaleControl.busy(false, 2, 1000, 0));
        assertTrue("等對方連進來的保留期內不算閒置", TailscaleControl.busy(false, 0, 1000, 5000));
        assertEquals(false, TailscaleControl.busy(false, 0, 5000, 5000));
    }

    @Test
    public void ownershipIsOnlyClearedWhenTailscaleReallyWentOff() {
        assertEquals("done", TailscaleControl.afterTurnOff(true, 1));
        assertEquals("關不掉要再試，不能清掉記號", "retry", TailscaleControl.afterTurnOff(false, 1));
        assertEquals("retry", TailscaleControl.afterTurnOff(false, TailscaleControl.MAX_OFF_TRIES - 1));
        assertEquals("試滿了才放棄", "giveup", TailscaleControl.afterTurnOff(false, TailscaleControl.MAX_OFF_TRIES));
    }

    @Test
    public void staleOwnershipMarkIsNotTrusted() {
        long now = 10_000_000L;
        assertTrue(TailscaleControl.oursStillValid(true, now - 60_000, now));
        assertEquals("過期的記號不能接手（那可能是你自己開的）", false, TailscaleControl.oursStillValid(true, now - TailscaleControl.OURS_VALID_MS, now));
        assertEquals(false, TailscaleControl.oursStillValid(false, now - 1000, now));
        assertEquals("沒有時間（舊版留下的）不信", false, TailscaleControl.oursStillValid(true, 0, now));
        assertEquals("時鐘倒退不信", false, TailscaleControl.oursStillValid(true, now + 5000, now));
    }
}
