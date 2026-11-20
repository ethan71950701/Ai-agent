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
}
