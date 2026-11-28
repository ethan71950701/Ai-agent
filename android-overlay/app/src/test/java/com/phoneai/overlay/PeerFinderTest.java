package com.phoneai.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

/** 找對方的判斷順序（PeerFinder）：用假的網路、推播、Tailscale 與時鐘，照實際遇過的情況各走一次。 */
public class PeerFinderTest {

    /** 假環境：每個動作都花一點（假的）時間；「哪些位址連得上」「第幾次搜尋才找到」都可以設定。 */
    static final class Fake implements PeerFinder.Env {
        long clock = 0;
        TransferStore.Peer stored;
        String lanNet = "192.168.1.112/24";
        boolean tsOn = true, tsTurnsOn = true, canRing = true, stop;
        String wakeErr;
        int scanFindsAfter = -1, directFindsAfter = -1;   // 第幾次（從 1 數）搜尋／直接連才找到；-1＝永遠找不到
        int reportTsAfterScans = -1;                       // 對方第幾次搜尋之後回報 Tailscale 位址
        String reportedTsIp = "100.99.77.50";
        Set<String> reachable = new HashSet<>();           // 連得上的 ip:port
        int scans, directs, wakes, tsTurnOns;
        List<Boolean> wakeRemote = new ArrayList<>();      // 每次叫醒時有沒有請對方「當作外網」
        boolean everWokenRemote;
        List<String> logs = new ArrayList<>(), phases = new ArrayList<>();

        static TransferDiscovery.Found found(String ip, int port) {
            TransferDiscovery.Found f = new TransferDiscovery.Found();
            f.id = "peer";
            f.name = "s25+";
            f.ip = ip;
            f.port = port;
            return f;
        }

        @Override public TransferDiscovery.Found scan(int timeoutMs) {
            clock += timeoutMs;
            scans++;
            if (everWokenRemote && reportTsAfterScans > 0 && scans >= reportTsAfterScans) {   // 跟真的對方一樣：只有被當作外網叫醒，才會開 Tailscale 並回報位址
                stored.tsIp = reportedTsIp;
                stored.tsPort = 47321;
            }
            return scanFindsAfter > 0 && scans >= scanFindsAfter ? found("192.168.1.130", 47321) : null;
        }
        @Override public TransferDiscovery.Found directLan() {
            clock += 300;
            directs++;
            return directFindsAfter > 0 && directs >= directFindsAfter ? found("192.168.1.131", 47321) : null;
        }
        @Override public boolean canRing() { return canRing; }
        @Override public boolean ping(TransferStore.Peer peer, String ip, int port) {
            clock += 500;
            return reachable.contains(ip + ":" + port);
        }
        @Override public TransferStore.Peer fresh() { return stored; }
        @Override public String lanNet() { return lanNet; }
        @Override public boolean tailscaleOn() { return tsOn; }
        @Override public void turnOnTailscale() { tsTurnOns++; if (tsTurnsOn) tsOn = true; }
        @Override public String wake(boolean remote) {
            wakes++;
            wakeRemote.add(remote);
            if (remote && wakeErr == null) everWokenRemote = true;
            return wakeErr;
        }
        @Override public long now() { return clock; }
        @Override public boolean stopRequested() { return stop; }
        @Override public void log(String line) { logs.add(line); }
        @Override public void phase(String text, String chip) { phases.add(chip); }
    }

    static TransferStore.Peer peer() {
        TransferStore.Peer p = new TransferStore.Peer();
        p.id = "peer";
        p.name = "s25+";
        p.ip = "192.168.1.105";
        p.port = 47321;
        p.tsIp = "100.99.77.49";
        p.tsPort = 47321;
        return p;
    }

    static Fake fake(TransferStore.Peer p) {
        Fake f = new Fake();
        f.stored = p;
        return f;
    }

    @Test public void broadcastFindsPeerRightAway() throws Exception {
        TransferStore.Peer p = peer();
        Fake e = fake(p);
        e.scanFindsAfter = 1;
        PeerFinder.Result r = PeerFinder.find(e, p);
        assertEquals("192.168.1.130", r.ip);
        assertNotNull(r.found);
        assertEquals(0, e.wakes);
        assertEquals(0, e.directs);
    }

    @Test public void broadcastBlockedButSavedOrScannedAddressWorks() throws Exception {
        TransferStore.Peer p = peer();
        Fake e = fake(p);
        e.directFindsAfter = 1;
        PeerFinder.Result r = PeerFinder.find(e, p);
        assertEquals("192.168.1.131", r.ip);
        assertEquals(0, e.wakes);
        assertTrue(e.logs.toString().contains("廣播搜尋沒找到"));
    }

    @Test public void savedTailscaleAddressWorksWithoutWaking() throws Exception {
        TransferStore.Peer p = peer();
        Fake e = fake(p);
        e.reachable.add("100.99.77.49:47321");
        PeerFinder.Result r = PeerFinder.find(e, p);
        assertEquals("100.99.77.49", r.ip);
        assertNull(r.found);
        assertEquals(0, e.wakes);
    }

    /** 2026-11-25 實際遇到的情況：兩支手機在不同路由器但網段都是 192.168.1.x、這支 Tailscale 沒開。要主動開 Tailscale、叫醒，等對方回報新位址。 */
    @Test public void differentRouterSameSubnetTurnsOnTailscaleAndWaitsForReport() throws Exception {
        TransferStore.Peer p = peer();
        Fake e = fake(p);
        e.tsOn = false;
        e.reportTsAfterScans = 4;   // 叫醒後過幾輪，對方才回報它的 Tailscale 位址
        e.reachable.add("100.99.77.50:47321");
        PeerFinder.Result r = PeerFinder.find(e, p);
        assertEquals(1, e.tsTurnOns);
        assertEquals("100.99.77.50", r.ip);
        assertTrue(e.phases.contains("開外網"));
        assertTrue(e.wakes >= 1);
    }

    /**
     * 2026-11-27 查出的原因：上面那種情況，這支判斷要走外網、自己開了 Tailscale，但叫醒推播以前是用網段重算（網段一樣＝同一個區網），
     * 送出去的是「不是外網」，對方就不開 Tailscale、也不回報位址，這邊只能一直連舊的 Tailscale 位址到逾時。每一次叫醒（含補送）都要說是外網。
     */
    @Test public void differentRouterSameSubnetAsksPeerToTreatItAsRemote() throws Exception {
        TransferStore.Peer p = peer();
        Fake e = fake(p);
        e.tsOn = false;
        e.reportTsAfterScans = 12;   // 對方晚一點才回報：中間會補送推播
        e.reachable.add("100.99.77.50:47321");
        PeerFinder.Result r = PeerFinder.find(e, p);
        assertEquals("100.99.77.50", r.ip);
        assertEquals("升級成外網後才開 Tailscale", 1, e.tsTurnOns);
        assertEquals("第一次先當區網叫醒", Boolean.FALSE, e.wakeRemote.get(0));
        assertEquals("升級後要說是外網", Boolean.TRUE, e.wakeRemote.get(e.wakeRemote.size() - 1));
        assertTrue("等太久：" + e.clock, e.clock <= PeerFinder.WAIT_REMOTE_MS);
    }

    /** 2026-11-28 使用者回報：同一個 Wi-Fi、對方螢幕關著（伺服器沒開，廣播也被丟掉），以前會莫名其妙開 Tailscale。對方被叫醒後在區網出現，就不該碰 Tailscale。 */
    @Test public void sameWifiPeerWithScreenOffDoesNotTurnOnTailscale() throws Exception {
        TransferStore.Peer p = peer();
        Fake e = fake(p);
        e.tsOn = false;
        e.scanFindsAfter = 4;   // 叫醒後幾輪對方的伺服器起來、在區網出現
        PeerFinder.Result r = PeerFinder.find(e, p);
        assertEquals("192.168.1.130", r.ip);
        assertEquals(0, e.tsTurnOns);
        assertFalse(e.wakeRemote.contains(true));
        assertFalse(e.phases.contains("開外網"));
    }

    @Test public void differentSubnetStartsRemoteImmediately() throws Exception {
        TransferStore.Peer p = peer();
        Fake e = fake(p);
        e.tsOn = false;
        e.lanNet = "192.168.51.20/24";   // 網段跟對方記下的 192.168.1.x 不同：一開始就是外網
        e.reportTsAfterScans = 2;
        e.reachable.add("100.99.77.50:47321");
        PeerFinder.find(e, p);
        assertEquals(1, e.tsTurnOns);
        assertEquals(Boolean.TRUE, e.wakeRemote.get(0));
    }

    @Test public void sameWifiWithTailscaleOnWakesAsLocal() throws Exception {
        TransferStore.Peer p = peer();
        p.tsIp = null;
        p.tsPort = 0;
        Fake e = fake(p);
        e.scanFindsAfter = 3;   // 同一個 Wi-Fi、這支 Tailscale 開著：當作區網叫醒，不必請對方開 Tailscale
        PeerFinder.find(e, p);
        assertEquals(1, e.wakes);
        assertEquals(Boolean.FALSE, e.wakeRemote.get(0));
    }

    @Test public void sameWifiDoesNotTurnOnTailscaleAndWaitsAbout25s() throws Exception {
        TransferStore.Peer p = peer();
        p.tsIp = null;
        p.tsPort = 0;
        Fake e = fake(p);
        e.reachable.add("192.168.1.105:47321");   // 叫醒都沒出現，最後用記下的位址再測一次：通了
        PeerFinder.Result r = PeerFinder.find(e, p);
        assertEquals(0, e.tsTurnOns);
        assertEquals("192.168.1.105", r.ip);
        long waited = e.clock;
        assertTrue("等太久：" + waited, waited < PeerFinder.WAIT_LAN_MS + 5000);
        assertTrue("等太短：" + waited, waited >= PeerFinder.WAIT_LAN_MS);
        assertTrue("應該補送推播：" + e.wakes, e.wakes >= 3);   // 第一次＋每 8 秒補一次
    }

    @Test public void remoteWaitsLonger() throws Exception {
        TransferStore.Peer p = peer();
        p.tsIp = null;
        p.tsPort = 0;
        Fake e = fake(p);
        e.lanNet = null;   // 沒連 Wi-Fi：外網
        e.reachable.add("192.168.1.105:47321");
        PeerFinder.find(e, p);
        assertTrue(e.clock >= PeerFinder.WAIT_REMOTE_MS);
    }

    @Test public void wakeFailureFallsBackAndReportsNoAnswer() {
        TransferStore.Peer p = peer();
        Fake e = fake(p);
        e.wakeErr = "HTTP 404 UNREGISTERED";
        try {
            PeerFinder.find(e, p);
            fail();
        } catch (Exception ex) {
            assertTrue(ex.getMessage(), ex.getMessage().contains("沒有回應"));
        }
        assertTrue(e.logs.toString().contains("推播叫醒失敗"));
        assertTrue(e.logs.toString().contains("token 已失效"));
        assertEquals(1, e.wakes);
    }

    @Test public void cannotRingAndNothingReachable() {
        TransferStore.Peer p = peer();
        Fake e = fake(p);
        e.canRing = false;
        try {
            PeerFinder.find(e, p);
            fail();
        } catch (Exception ex) {
            assertTrue(ex.getMessage().contains("沒有回應"));
        }
        assertEquals(0, e.wakes);
    }

    @Test public void noAddressAtAll() {
        TransferStore.Peer p = peer();
        p.ip = null;
        p.port = 0;
        p.tsIp = null;
        p.tsPort = 0;
        Fake e = fake(p);
        e.canRing = false;
        try {
            PeerFinder.find(e, p);
            fail();
        } catch (Exception ex) {
            assertTrue(ex.getMessage().contains("可能沒開機"));
        }
    }

    @Test public void stopButtonEndsTheWait() {
        TransferStore.Peer p = peer();
        Fake e = fake(p);
        e.stop = true;
        try {
            PeerFinder.find(e, p);
            fail();
        } catch (TransferNotify.Stopped s) {
            // 對
        } catch (Exception ex) {
            fail("應該是 Stopped：" + ex);
        }
    }

    @Test public void peerShowsUpOnLanDuringWait() throws Exception {
        TransferStore.Peer p = peer();
        p.tsIp = null;
        p.tsPort = 0;
        Fake e = fake(p);
        e.scanFindsAfter = 3;   // 第 1 次（叫醒前）沒找到，叫醒後第 2 輪找到
        PeerFinder.Result r = PeerFinder.find(e, p);
        assertEquals("192.168.1.130", r.ip);
        assertNotNull(r.found);
        assertEquals(1, e.wakes);
        assertFalse(e.clock >= PeerFinder.WAIT_LAN_MS);
    }
}
