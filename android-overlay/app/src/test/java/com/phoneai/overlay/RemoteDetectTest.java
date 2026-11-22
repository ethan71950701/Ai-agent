package com.phoneai.overlay;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RemoteDetectTest {
    @Test
    public void noWifiIsRemote() {
        assertTrue(FcmLab.isRemote(null, "192.168.1.107"));
        assertTrue(FcmLab.isRemote(null, null));
    }

    @Test
    public void sameNetworkIsNotRemote() {
        assertFalse(FcmLab.isRemote("192.168.1.20/24", "192.168.1.107"));
        assertFalse("網段比 /24 大時也要看實際前綴長度", FcmLab.isRemote("172.23.4.118/20", "172.23.9.5"));
    }

    @Test
    public void aDifferentWifiNetworkIsRemote() {
        // 手機在 192.168.51.x，平板上次是 192.168.1.107：不在同一個區網，要走 Tailscale
        assertTrue(FcmLab.isRemote("192.168.51.217/24", "192.168.1.107"));
        assertTrue(FcmLab.isRemote("10.0.0.5/24", "192.168.1.107"));
    }

    @Test
    public void unknownPeerAddressKeepsTheLanFirstBehaviour() {
        assertFalse(FcmLab.isRemote("192.168.51.217/24", null));
        assertFalse(FcmLab.isRemote("192.168.51.217/24", ""));
    }
}
