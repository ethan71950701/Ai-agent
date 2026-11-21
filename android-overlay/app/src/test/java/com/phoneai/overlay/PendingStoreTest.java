package com.phoneai.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

public class PendingStoreTest {
    private File f;
    private PendingStore s;

    @Before
    public void setUp() throws Exception {
        f = File.createTempFile("pending", ".json");
        f.delete();
        s = new PendingStore(f);
    }

    private PendingStore.Entry entry(String id, long now) {
        PendingStore.Entry e = new PendingStore.Entry();
        e.id = id; e.kind = "up"; e.peerId = "p1"; e.peerName = "小米13"; e.name = "a.zip"; e.size = 1000; e.done = 0; e.updatedAt = now;
        e.source = "content://x/1";
        return e;
    }

    @Test
    public void emptyAndCorruptFilesGiveAnEmptyList() throws Exception {
        assertTrue(s.list(1).isEmpty());
        Files.write(f.toPath(), "not json".getBytes());
        assertTrue(s.list(1).isEmpty());
    }

    @Test
    public void beginProgressRemovePersistAcrossInstances() {
        long t = 1_000_000;
        s.begin(entry("up:p1:a.zip:1000", t));
        s.progress("up:p1:a.zip:1000", 420, 1000, "fp123", t + 5000, false);
        List<PendingStore.Entry> l = new PendingStore(f).list(t + 6000);   // 另一個實例讀同一個檔案：重開 App 之後也還在
        assertEquals(1, l.size());
        assertEquals(420, l.get(0).done);
        assertEquals(42, l.get(0).percent());
        assertEquals("fp123", l.get(0).fp);
        assertEquals("小米13", l.get(0).peerName);
        s.remove("up:p1:a.zip:1000", t + 6500);
        assertTrue(s.list(t + 7000).isEmpty());
    }

    @Test
    public void sameIdReplacesAndNewestComesFirst() {
        long t = 1_000_000;
        s.begin(entry("A", t));
        s.begin(entry("B", t + 1));
        PendingStore.Entry again = entry("A", t + 2);
        again.done = 7;
        s.begin(again);
        List<PendingStore.Entry> l = s.list(t + 3);
        assertEquals(2, l.size());
        assertEquals("A", l.get(0).id);
        assertEquals(7, l.get(0).done);
    }

    @Test
    public void progressWritesAreThrottledUnlessForced() {
        long t = 1_000_000;
        s.begin(entry("A", t));
        s.progress("A", 100, 1000, null, t + 1000, false);   // 距離上次不到 3 秒：不寫
        assertEquals(0, s.find("A", t + 1500).done);
        s.progress("A", 100, 1000, null, t + 1000, true);    // 強制：寫
        assertEquals(100, s.find("A", t + 1500).done);
        s.progress("A", 300, 1000, null, t + 4500, false);   // 超過 3 秒：寫
        assertEquals(300, s.find("A", t + 5000).done);
    }

    @Test
    public void oldEntriesExpireAndBadOnesAreDropped() throws Exception {
        long t = 1_000_000_000L;
        s.begin(entry("old", t));
        s.begin(entry("new", t + PendingStore.EXPIRE_MS));
        List<PendingStore.Entry> l = s.list(t + PendingStore.EXPIRE_MS + 10);
        assertEquals(1, l.size());
        assertEquals("new", l.get(0).id);
        Files.write(f.toPath(), "[{\"id\":\"\",\"kind\":\"up\"},{\"id\":\"x\",\"kind\":\"weird\"},{\"id\":\"ok\",\"kind\":\"down\",\"updatedAt\":5}]".getBytes());
        l = s.list(10);
        assertEquals(1, l.size());
        assertEquals("ok", l.get(0).id);
        assertNull(s.find("nope", 10));
        assertNotNull(s.find("ok", 10));
    }

    @Test
    public void idIsStableForTheSameTransfer() {
        assertEquals(PendingStore.idFor("up", "p", "a.zip", 5), PendingStore.idFor("up", "p", "a.zip", 5));
        assertTrue(!PendingStore.idFor("up", "p", "a.zip", 5).equals(PendingStore.idFor("down", "p", "a.zip", 5)));
    }

    @Test
    public void reasonIsStoredAndSurvivesReload() throws Exception {
        java.io.File f = java.io.File.createTempFile("pend", ".json");
        PendingStore st = new PendingStore(f);
        PendingStore.Entry e = new PendingStore.Entry();
        e.id = "up:p:a.bin:10"; e.kind = "up"; e.peerId = "p"; e.peerName = "P"; e.name = "a.bin"; e.size = 10;
        st.begin(e);
        st.setReason(e.id, "連不上對方");
        assertEquals("連不上對方", new PendingStore(f).find(e.id).reason);
        st.setReason("nope", "x");   // 沒有這一筆：不出錯
        assertEquals("連不上對方", new PendingStore(f).find(e.id).reason);
    }
}
