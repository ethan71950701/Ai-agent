package com.phoneai.overlay;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** 真的開一個本機的互傳伺服器與客戶端（127.0.0.1），測續傳、校驗碼與錯誤處理。 */
public class ResumeTransferTest {
    private File root, inbox;
    private TransferServer server;
    private byte[] key;
    private TransferClient client;
    private final AtomicLong sentByServer = new AtomicLong();   // 伺服器「送出」的位元組（取檔時）
    private final AtomicLong receivedByServer = new AtomicLong();   // 伺服器「收到」的位元組（傳檔時）

    @Before
    public void setUp() throws Exception {
        root = Files.createTempDirectory("rt-root").toFile();
        inbox = new File(root, "Download/PhoneAI");
        inbox.mkdirs();
        key = TransferCrypto.random(32);
        final byte[] k = key;
        server = new TransferServer(new TransferServer.Host() {
            @Override public String myId() { return "srv"; }
            @Override public String myName() { return "server"; }
            @Override public byte[] keyOf(String peerId) { return peerId.equals("cli") ? k : null; }
            @Override public File root() { return root; }
            @Override public File inbox() { return inbox; }
            @Override public boolean pairingOpen() { return false; }
            @Override public void pairRequest(String peerId, String peerName, byte[] key, String sas) { }
            @Override public String pairState(String peerId) { return "none"; }
            @Override public void event(String peerId, String kind, String what, long size) {
                if (kind.equals("get")) sentByServer.addAndGet(size);
                if (kind.equals("put")) receivedByServer.addAndGet(size);
            }
            @Override public void peerName(String peerId, String name) { }
            @Override public void progress(String peerId, boolean toPeer, String name, long done, long total) { }
            @Override public void transferEnded() { }
            @Override public void peerSeen(String peerId, String ip) { }
        });
        server.start(0);
        client = new TransferClient("127.0.0.1", server.port(), "cli", "client", key);
    }

    @After
    public void tearDown() {
        server.stop();
    }

    private static byte[] data(int n, long seed) {
        byte[] b = new byte[n];
        new java.util.Random(seed).nextBytes(b);
        return b;
    }

    private File onlyFile(File dir) {
        File[] fs = dir.listFiles((d, n) -> !n.startsWith("."));
        assertNotNull(fs);
        assertEquals(1, fs.length);
        return fs[0];
    }

    /** 第 1 次打開是算指紋、第 2 次是真正開始傳：這一次傳到 failAfter 個位元組就丟 SocketException（模擬斷線）；其他次正常。 */
    private TransferClient.Source flaky(final byte[] bytes, final int failAfter, final int[] opens) {
        return () -> {
            final int which = opens[0]++;
            if (which != 1) return new ByteArrayInputStream(bytes);
            return new InputStream() {
                int pos;
                @Override public int read() throws IOException { byte[] one = new byte[1]; return read(one, 0, 1) < 0 ? -1 : one[0] & 255; }
                @Override public int read(byte[] b, int off, int len) throws IOException {
                    if (pos >= failAfter) throw new java.net.SocketException("Connection reset");
                    if (pos >= bytes.length) return -1;
                    int n = Math.min(len, Math.min(bytes.length - pos, 8 * 1024));
                    n = Math.min(n, failAfter - pos);
                    System.arraycopy(bytes, pos, b, off, n);
                    pos += n;
                    return n;
                }
            };
        };
    }

    @Test
    public void plainResumableUploadArrivesIntactWithChecksum() throws Exception {
        byte[] d = data(300_000, 1);
        String saved = client.putResumable(() -> new ByteArrayInputStream(d), d.length, "a.bin", null);
        assertEquals("a.bin", saved);
        assertArrayEquals(d, Files.readAllBytes(new File(inbox, "a.bin").toPath()));
        assertTrue(client.lastVerified);
        assertEquals(Checksum.hex(Checksum.newDigest().digest(d)), client.lastSha);
        assertEquals(d.length, receivedByServer.get());
        assertEquals(0, inbox.listFiles((x, n) -> n.endsWith(".part")).length);   // 沒留半成品
    }

    @Test
    public void interruptedUploadResumesFromTheBreakpoint() throws Exception {
        byte[] d = data(400_000, 2);
        int[] opens = new int[1];
        String saved = client.putResumable(flaky(d, 150_000, opens), d.length, "big.bin", null);
        assertEquals("big.bin", saved);
        assertArrayEquals(d, Files.readAllBytes(new File(inbox, "big.bin").toPath()));
        assertTrue("整檔校驗碼要涵蓋續傳前後兩段", client.lastVerified);
        assertEquals(Checksum.hex(Checksum.newDigest().digest(d)), client.lastSha);
        assertTrue("來源至少開了 3 次（算指紋、第一次傳、續傳）：" + opens[0], opens[0] >= 3);
        assertTrue("續傳只該送剩下的：伺服器第二次收到 " + receivedByServer.get(), receivedByServer.get() < d.length);
        assertEquals(0, inbox.listFiles((x, n) -> n.endsWith(".part")).length);
    }

    @Test
    public void leftoverPartialIsPickedUpByALaterUpload() throws Exception {
        byte[] d = data(200_000, 3);
        String fp = Resume.fingerprint(d, (int) Math.min(Resume.HEAD_BYTES, d.length), d.length, "later.bin");
        File part = new File(inbox, Resume.partName("cli", fp));
        try (FileOutputStream o = new FileOutputStream(part)) { o.write(d, 0, 120_000); }   // 上次傳到一半留下的
        assertEquals(120_000, client.remoteOffset(fp));
        client.putResumable(() -> new ByteArrayInputStream(d), d.length, "later.bin", null);
        assertArrayEquals(d, Files.readAllBytes(new File(inbox, "later.bin").toPath()));
        assertEquals(d.length - 120_000, receivedByServer.get());   // 只收了後半段
        assertTrue(client.lastVerified);
    }

    @Test
    public void wrongOffsetIsRefusedByTheServer() throws Exception {
        byte[] d = data(50_000, 4);
        String fp = Resume.fingerprint(d, d.length, d.length, "x.bin");
        File part = new File(inbox, Resume.partName("cli", fp));
        try (FileOutputStream o = new FileOutputStream(part)) { o.write(d, 0, 10_000); }
        // 直接用錯的斷點要求續傳：伺服器要拒絕（409），不能把檔案拼壞
        try {
            java.lang.reflect.Method m = TransferClient.class.getDeclaredMethod("putOnce", TransferClient.Source.class, long.class, String.class, String.class, long.class, TransferClient.Progress.class);
            m.setAccessible(true);
            m.invoke(client, (TransferClient.Source) () -> new ByteArrayInputStream(d), (long) d.length, "x.bin", fp, 25_000L, null);
            throw new AssertionError("should have been refused");
        } catch (java.lang.reflect.InvocationTargetException e) {
            assertTrue(String.valueOf(e.getCause()), String.valueOf(e.getCause().getMessage()).contains("409"));
        }
        assertEquals(10_000, part.length());   // 半成品沒被動
        assertFalse(new File(inbox, "x.bin").exists());
    }

    @Test
    public void interruptedDownloadResumesAndVerifies() throws Exception {
        byte[] d = data(500_000, 5);
        Files.write(new File(root, "movie.bin").toPath(), d);
        File dest = new File(inbox, "movie.bin");
        File part = TransferClient.partOf(dest);
        try (FileOutputStream o = new FileOutputStream(part)) { o.write(d, 0, 200_000); }   // 上次收到一半
        long n = client.download("movie.bin", dest, null);
        assertEquals(d.length, n);
        assertArrayEquals(d, Files.readAllBytes(dest.toPath()));
        assertFalse(part.exists());
        assertEquals(d.length - 200_000, sentByServer.get());   // 伺服器只送了後半段
        assertTrue("續傳後仍要核對整檔校驗碼", client.lastVerified);
        assertEquals(Checksum.hex(Checksum.newDigest().digest(d)), client.lastSha);
    }

    @Test
    public void downloadWithACorruptPartialIsDiscarded() throws Exception {
        byte[] d = data(100_000, 6);
        Files.write(new File(root, "f.bin").toPath(), d);
        File dest = new File(inbox, "f.bin");
        try (FileOutputStream o = new FileOutputStream(TransferClient.partOf(dest))) { o.write(data(40_000, 99)); }   // 半成品內容是錯的
        try {
            client.download("f.bin", dest, null);
            throw new AssertionError("a corrupted partial must not pass the checksum");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().startsWith("校驗碼不符"));
        }
        assertFalse("壞掉的半成品要刪掉，不然永遠接不起來", TransferClient.partOf(dest).exists());
        assertFalse(dest.exists());
        long n = client.download("f.bin", dest, null);   // 再取一次從頭來，這次成功
        assertEquals(d.length, n);
        assertArrayEquals(d, Files.readAllBytes(dest.toPath()));
    }

    @Test
    public void downloadFromTheStartStillWorks() throws Exception {
        byte[] d = data(90_000, 7);
        Files.write(new File(root, "g.bin").toPath(), d);
        File dest = new File(inbox, "g.bin");
        assertEquals(d.length, client.download("g.bin", dest, null));
        assertArrayEquals(d, Files.readAllBytes(dest.toPath()));
        assertTrue(client.lastVerified);
    }

    @Test
    public void spaceAndSizeQuestionsAreAnswered() throws Exception {
        assertEquals(null, client.spaceProblem(1000));
        String why = client.spaceProblem(TransferServer.MAX_UPLOAD_BYTES + 1);
        assertNotNull(why);
        assertTrue(why, why.contains("太大"));
    }

    @Test
    public void manualStopOfADownloadDeletesThePartial() throws Exception {
        byte[] d = data(400_000, 8);
        Files.write(new File(root, "stop.bin").toPath(), d);
        File dest = new File(inbox, "stop.bin");
        try {
            client.download("stop.bin", dest, (done, total) -> { if (done > 100_000) throw new TransferNotify.Stopped(); });
            throw new AssertionError("should have been stopped");
        } catch (TransferNotify.Stopped expected) { }
        assertFalse("手動停止：半成品要刪掉", TransferClient.partOf(dest).exists());
        assertFalse(dest.exists());
    }

    @Test
    public void manualStopOfAnUploadAsksThePeerToDeleteItsPartial() throws Exception {
        byte[] d = data(400_000, 9);
        try {
            client.putResumable(() -> new ByteArrayInputStream(d), d.length, "up.bin", (done, total) -> { if (done > 150_000) throw new TransferNotify.Stopped(); });
            throw new AssertionError("should have been stopped");
        } catch (TransferNotify.Stopped expected) { }
        assertTrue(Resume.validFp(client.lastFp));
        assertEquals("對方那邊的半成品也要刪掉", 0, client.remoteOffset(client.lastFp));
        assertEquals(0, inbox.listFiles((x, n) -> n.endsWith(".part")).length);
        assertFalse(new File(inbox, "up.bin").exists());
    }

    @Test
    public void accidentalDisconnectKeepsThePartialForResuming() throws Exception {
        byte[] d = data(300_000, 10);
        String fp = Resume.fingerprint(d, d.length, d.length, "keep.bin");
        File part = new File(inbox, Resume.partName("cli", fp));
        try (FileOutputStream o = new FileOutputStream(part)) { o.write(d, 0, 90_000); }
        assertEquals(90_000, client.remoteOffset(fp));   // 之前意外斷線留下的，沒有被誤刪
        client.discardRemote(fp);                        // 按「丟掉」才刪
        assertEquals(0, client.remoteOffset(fp));
        assertFalse(part.exists());
    }

    @Test
    public void pausedDownloadKeepsThePartialAndResumesLater() throws Exception {
        byte[] d = data(500_000, 11);
        Files.write(new File(root, "pause.bin").toPath(), d);
        File dest = new File(inbox, "pause.bin");
        try {
            client.download("pause.bin", dest, (done, total) -> { if (done > 150_000) throw new TransferNotify.Paused(); });
            throw new AssertionError("should have paused");
        } catch (TransferNotify.Paused expected) { }
        File part = TransferClient.partOf(dest);
        assertTrue("暫停：已收到的要保留", part.isFile() && part.length() > 0 && part.length() < d.length);
        long kept = part.length();
        sentByServer.set(0);
        assertEquals(d.length, client.download("pause.bin", dest, null));   // 按「繼續」
        assertArrayEquals(d, Files.readAllBytes(dest.toPath()));
        assertEquals("續傳只收剩下的", d.length - kept, sentByServer.get());
        assertTrue(client.lastVerified);
    }

    @Test
    public void pausedUploadKeepsThePeersPartialAndResumesLater() throws Exception {
        byte[] d = data(500_000, 12);
        try {
            client.putResumable(() -> new ByteArrayInputStream(d), d.length, "pup.bin", (done, total) -> { if (done > 200_000) throw new TransferNotify.Paused(); });
            throw new AssertionError("should have paused");
        } catch (TransferNotify.Paused expected) { }
        // 對方（伺服器）發現斷線需要一點時間；等它收尾後半成品應該在
        long have = 0, prev = -1;
        for (int i = 0; i < 50 && !(have > 0 && have == prev); i++) { prev = have; Thread.sleep(100); have = client.remoteOffset(client.lastFp); }   // 等它長度不再增加
        assertTrue("暫停：對方的半成品要保留，實際 " + have, have > 0 && have < d.length);
        receivedByServer.set(0);
        client.putResumable(() -> new ByteArrayInputStream(d), d.length, "pup.bin", null);   // 按「繼續」
        assertArrayEquals(d, Files.readAllBytes(new File(inbox, "pup.bin").toPath()));
        assertEquals("續傳只送剩下的", d.length - have, receivedByServer.get());
        assertTrue(client.lastVerified);
    }

    @Test
    public void pauseFlagStopsRetriesBeforeTheyStart() throws Exception {
        byte[] d = data(10_000, 13);
        TransferNotify.requestPause();
        try {
            client.putResumable(() -> new ByteArrayInputStream(d), d.length, "never.bin", null);
            throw new AssertionError("should be paused before even trying");
        } catch (TransferNotify.Paused expected) {
        } finally {
            TransferNotify.begin();   // 清掉旗標，不影響其他測試
        }
        assertFalse(new File(inbox, "never.bin").exists());
    }
}
