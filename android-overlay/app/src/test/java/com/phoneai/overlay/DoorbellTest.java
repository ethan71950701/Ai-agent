package com.phoneai.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

public class DoorbellTest {
    private static final long NOW = 1_700_000_000_000L;
    private byte[] key;
    private Doorbell.Verifier v;

    @Before
    public void setUp() {
        key = TransferCrypto.random(32);
        final byte[] k = key;
        v = new Doorbell.Verifier(id -> id.equals("phoneA") ? k : null);
    }

    private Map<String, String> ring() {
        return Doorbell.make(key, "phoneA", "192.168.1.20", 47321, "/sdcard/DCIM/a.jpg", "a.jpg", 12345, NOW);
    }

    @Test
    public void validDoorbellRoundTrips() {
        Doorbell.Result r = v.verify(ring(), NOW + 1500, "192.168.1.55");
        assertTrue(r.error, r.ok());
        assertEquals("phoneA", r.ring.from);
        assertEquals("192.168.1.20", r.ring.ip);
        assertEquals(47321, r.ring.port);
        assertEquals("/sdcard/DCIM/a.jpg", r.ring.path);
        assertEquals("a.jpg", r.ring.name);
        assertEquals(12345, r.ring.size);
    }

    @Test
    public void messageFitsInFcmLimitAndHidesContent() {
        Map<String, String> m = ring();
        int total = 0;
        for (Map.Entry<String, String> e : m.entrySet()) total += e.getKey().length() + e.getValue().length();
        assertTrue("size " + total, total < 1000);
        assertFalse(m.get("ct").contains("a.jpg"));
        assertFalse(m.toString().contains("192.168"));
    }

    @Test
    public void fieldNamesAvoidFcmReservedWords() {
        for (String k : ring().keySet()) {
            assertFalse(k, k.equals("from") || k.equals("message_type") || k.startsWith("google") || k.startsWith("gcm") || k.equals("notification"));
        }
    }

    @Test
    public void expiredOrFutureIsRejected() {
        assertFalse(v.verify(ring(), NOW + 61_000, null).ok());
        assertFalse(v.verify(ring(), NOW - 61_000, null).ok());
        assertTrue(v.verify(ring(), NOW + 59_000, null).ok());
    }

    @Test
    public void sameDoorbellOnlyWorksOnce() {
        Map<String, String> m = ring();
        assertTrue(v.verify(m, NOW, null).ok());
        Doorbell.Result again = v.verify(m, NOW + 100, null);
        assertFalse(again.ok());
        assertEquals("重複的門鈴", again.error);
    }

    @Test
    public void unknownSenderOrWrongKeyIsRejected() {
        Map<String, String> other = Doorbell.make(TransferCrypto.random(32), "phoneA", "192.168.1.20", 47321, "/x", "x", 1, NOW);
        assertFalse(v.verify(other, NOW, null).ok());   // 用別的金鑰做的：解密失敗
        Map<String, String> stranger = Doorbell.make(key, "stranger", "192.168.1.20", 47321, "/x", "x", 1, NOW);
        assertEquals("沒配對過的裝置", v.verify(stranger, NOW, null).error);
    }

    @Test
    public void anyTamperedFieldBreaksIt() {
        for (String field : new String[]{"src", "ts", "nonce", "ct", "v"}) {
            Map<String, String> m = ring();
            String old = m.get(field);
            String bad = field.equals("ts") ? String.valueOf(Long.parseLong(old) + 1)
                    : field.equals("v") ? "2"
                    : field.equals("src") ? "phoneB"
                    : field.equals("nonce") ? TransferCrypto.hex(TransferCrypto.random(16))
                    : old.substring(0, old.length() - 4) + (old.endsWith("AAAA") ? "BBBB" : "AAAA");
            m.put(field, bad);
            assertFalse("改了 " + field + " 還是通過", v.verify(m, NOW, null).ok());
        }
        // 換成別的裝置 id 也要有金鑰：把 from 改成有金鑰的 id，但密文是用原本的 AAD 做的，還是失敗
        Doorbell.Verifier two = new Doorbell.Verifier(id -> key);
        Map<String, String> m = ring();
        m.put("src", "phoneB");
        assertFalse(two.verify(m, NOW, null).ok());
    }

    @Test
    public void garbageInputNeverThrows() {
        Map<String, String> m = new HashMap<>();
        assertFalse(v.verify(m, NOW, null).ok());
        m.put("v", "1"); m.put("src", "phoneA"); m.put("ts", "abc"); m.put("nonce", "zz"); m.put("ct", "!!!");
        assertFalse(v.verify(m, NOW, null).ok());
        m.put("ts", String.valueOf(NOW)); m.put("nonce", "0123456789abcdef0123456789abcdef"); m.put("ct", "!!!");
        assertFalse(v.verify(m, NOW, null).ok());
        m.put("ct", new String(new char[5000]).replace('\0', 'A'));
        assertFalse(v.verify(m, NOW, null).ok());
    }

    @Test
    public void onlyPrivateLanAddressesAreAccepted() {
        for (String ip : new String[]{"10.0.0.5", "172.16.1.1", "172.31.255.1", "192.168.0.9"}) assertTrue(ip, Doorbell.isPrivateLan(ip));
        for (String ip : new String[]{"8.8.8.8", "127.0.0.1", "100.64.1.2", "169.254.1.1", "172.32.0.1", "172.15.0.1", "192.169.0.1",
                "evil.example.com", "192.168.1.1.5", "192.168.1.256", "", null}) assertFalse(String.valueOf(ip), Doorbell.isPrivateLan(ip));
        Map<String, String> pub = Doorbell.make(key, "phoneA", "8.8.8.8", 47321, "/x", "x", 1, NOW);
        assertEquals("位址不是區網私有位址", v.verify(pub, NOW, null).error);
    }

    @Test
    public void subnetCheckUsesMyAddress() {
        Map<String, String> m = ring();
        assertTrue(v.verify(m, NOW, "192.168.1.99").ok());
        Doorbell.Verifier v2 = new Doorbell.Verifier(id -> key);
        assertTrue(v2.verify(ring(), NOW, "192.168.7.99").error.startsWith("位址不在目前的網段"));
        assertTrue(Doorbell.sameSubnet24("10.1.2.3", "10.1.2.250"));
        assertTrue(Doorbell.sameSubnet("172.23.4.118/22", "172.23.5.20"));
        assertFalse(Doorbell.sameSubnet("172.23.4.118/24", "172.23.5.20"));
        assertTrue(Doorbell.sameSubnet("172.23.4.118/16", "172.23.200.9"));
        assertFalse(Doorbell.sameSubnet("172.23.4.118/22", "172.23.8.20"));
        assertFalse(Doorbell.sameSubnet("192.168.1.5/0", "10.0.0.1"));   // 前綴太小不當成同網段
        assertFalse(Doorbell.sameSubnet("192.168.1.5/abc", "192.168.1.9"));
        assertFalse(Doorbell.sameSubnet(null, "192.168.1.9"));
        assertFalse(Doorbell.sameSubnet24("10.1.2.3", "10.1.3.3"));
    }

    @Test
    public void serveDoorbellWorksFromOutsideTheLanOverTailscale() {
        Map<String, String> ts = Doorbell.makeServe(key, "phoneA", "100.101.102.103", 47321, NOW, true);
        Doorbell.Result r = v.verify(ts, NOW, "192.168.1.55/24");   // 對方在別的網路：不看網段
        assertTrue(r.error, r.ok());
        assertTrue(r.ring.remote);
        assertEquals("100.101.102.103", r.ring.ip);
        Doorbell.Verifier v2 = new Doorbell.Verifier(id -> key);
        Doorbell.Result none = v2.verify(Doorbell.makeServe(key, "phoneA", "", 47321, NOW, true), NOW, "192.168.1.55/24");   // 叫醒用不到位址，空的也行
        assertTrue(none.error, none.ok());
        Doorbell.Verifier v3 = new Doorbell.Verifier(id -> key);
        Doorbell.Result other = v3.verify(Doorbell.makeServe(key, "phoneA", "10.9.9.9", 47321, NOW), NOW, "192.168.1.55/24");   // 別的區網：叫醒不看網段
        assertTrue(other.error, other.ok());
        assertFalse(other.ring.remote);
    }

    @Test
    public void serveDoorbellStillRejectsPublicAddressesAndForgeries() {
        assertFalse(new Doorbell.Verifier(id -> key).verify(Doorbell.makeServe(key, "phoneA", "8.8.8.8", 47321, NOW, true), NOW, null).ok());
        assertFalse(new Doorbell.Verifier(id -> key).verify(Doorbell.makeServe(key, "phoneA", "evil.example.com", 47321, NOW, true), NOW, null).ok());
        assertFalse(new Doorbell.Verifier(id -> key).verify(Doorbell.makeServe(TransferCrypto.random(32), "phoneA", "100.64.0.1", 47321, NOW, true), NOW, null).ok());   // 金鑰不對
        assertFalse(new Doorbell.Verifier(id -> key).verify(Doorbell.makeServe(key, "phoneA", "100.64.0.1", 47321, NOW - 90_000, true), NOW, null).ok());   // 過期
        Doorbell.Verifier once = new Doorbell.Verifier(id -> key);
        Map<String, String> m = Doorbell.makeServe(key, "phoneA", "100.64.0.1", 47321, NOW, true);
        assertTrue(once.verify(m, NOW, null).ok());
        assertFalse(once.verify(m, NOW, null).ok());   // 只認一次
    }

    @Test
    public void tailnetRange() {
        assertTrue(Doorbell.isTailnet("100.64.0.1"));
        assertTrue(Doorbell.isTailnet("100.127.255.254"));
        assertFalse(Doorbell.isTailnet("100.63.0.1"));
        assertFalse(Doorbell.isTailnet("100.128.0.1"));
        assertFalse(Doorbell.isTailnet("192.168.1.1"));
        assertFalse(Doorbell.isTailnet(null));
    }

    @Test
    public void fileDoorbellStillNeedsTheLanAndTheSameSubnet() {
        Map<String, String> m = Doorbell.make(key, "phoneA", "100.101.102.103", 47321, "/x", "x", 1, NOW);
        assertFalse("取檔的門鈴要連回發送端，Tailscale 位址不收", new Doorbell.Verifier(id -> key).verify(m, NOW, null).ok());
    }

    @Test
    public void fcmTokenFormatCheck() {
        String ok = "dXJ0Z2R3cWV0bW1yZ2Y6QVBBOTFiR2xvbmdmY210b2tlbl8tYWJjMTIz:APA91bGxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
        assertTrue(Doorbell.validToken(ok));
        assertFalse(Doorbell.validToken(null));
        assertFalse(Doorbell.validToken(""));
        assertFalse(Doorbell.validToken("short"));
        assertFalse(Doorbell.validToken(ok + "&x=1"));
        assertFalse(Doorbell.validToken(ok + "\n"));
        assertFalse(Doorbell.validToken(new String(new char[401]).replace('\0', 'a')));
    }

    @Test
    public void badFieldsAreRejected() {
        byte[] k = key;
        Object[][] bad = {
                {"192.168.1.20", 0, "/x", "x", 1L}, {"192.168.1.20", 70000, "/x", "x", 1L},
                {"192.168.1.20", 1, "", "x", 1L}, {"192.168.1.20", 1, "/x\n/y", "x", 1L},
                {"192.168.1.20", 1, "/x", "a\u0000b", 1L}, {"192.168.1.20", 1, "/x", "x", -1L},
                {"192.168.1.20", 1, "/x", "x", Doorbell.MAX_SIZE + 1}, {"192.168.1.20", 1, new String(new char[1025]).replace('\0', 'a'), "x", 1L},
                {"192.168.1.20", 1, "/x", new String(new char[256]).replace('\0', 'a'), 1L}};
        for (Object[] b : bad) {
            Map<String, String> m = Doorbell.make(k, "phoneA", (String) b[0], (Integer) b[1], (String) b[2], (String) b[3], (Long) b[4], NOW);
            assertNull(java.util.Arrays.toString(b), (Object) (v.verify(m, NOW, null).ok() ? "accepted" : null));
        }
    }

    @Test
    public void rejectedMessagesDoNotUseUpTheNonce() {
        Map<String, String> m = ring();
        assertFalse(v.verify(m, NOW + 100_000, null).ok());   // 過期被拒
        assertTrue(v.verify(m, NOW + 1000, null).ok());       // 之後在有效時間內重送一次仍可以（過期沒佔 nonce）
        assertNotNull(m);
    }

    @Test
    public void addressReportRoundTripsAndOnlyAcceptsTailscaleAddresses() {
        byte[] key = TransferCrypto.random(32);
        Doorbell.Verifier v = new Doorbell.Verifier(id -> id.equals("peer") ? key : null);
        long now = 1_000_000L;
        Doorbell.Result ok = v.verify(Doorbell.makeAddr(key, "peer", "100.101.102.103", 47321, now), now, "192.168.1.5/24");
        assertTrue(ok.error, ok.ok());
        assertEquals("addr", ok.ring.mode);
        assertEquals("100.101.102.103", ok.ring.ip);
        assertEquals(47321, ok.ring.port);
        // 區網位址、公開位址、亂格式都不收（不能讓人用推播把我們的紀錄改成別的位址）
        for (String bad : new String[]{"192.168.1.9", "8.8.8.8", "127.0.0.1", "not an ip", ""})
            assertFalse(bad, v.verify(Doorbell.makeAddr(key, "peer", bad, 47321, now), now, null).ok());
        assertFalse("埠號不對", v.verify(Doorbell.makeAddr(key, "peer", "100.101.102.103", 0, now), now, null).ok());
        // 同一則只認一次、金鑰不對、過期都照樣拒絕
        java.util.Map<String, String> m = Doorbell.makeAddr(key, "peer", "100.101.102.103", 47321, now);
        assertTrue(v.verify(m, now, null).ok());
        assertFalse(v.verify(m, now, null).ok());
        assertFalse(new Doorbell.Verifier(id -> TransferCrypto.random(32)).verify(Doorbell.makeAddr(key, "peer", "100.101.102.103", 47321, now), now, null).ok());
        assertFalse(v.verify(Doorbell.makeAddr(key, "peer", "100.101.102.103", 47321, now - 120_000), now, null).ok());
    }
}
