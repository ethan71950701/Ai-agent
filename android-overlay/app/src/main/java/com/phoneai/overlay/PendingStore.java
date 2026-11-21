package com.phoneai.overlay;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 沒傳完的檔案清單（純 Java，存成一個小 JSON 檔）：意外斷線、重試後還是失敗、或 App 被殺掉的傳輸會留在這裡，
 * 使用者可以在「互傳檔案」頁按「繼續」或「丟掉」。成功或手動按「停止」的會被移除。超過 7 天的自動清掉。
 */
final class PendingStore {
    static final long EXPIRE_MS = 7L * 24 * 3600 * 1000;
    static final long WRITE_EVERY_MS = 3000;   // 傳輸中更新進度的最小間隔，免得一直寫檔

    static final class Entry {
        String id, kind;          // kind：up（我傳給對方）或 down（我從對方取）
        String peerId, peerName;
        String name;              // 顯示用檔名
        String source = "";       // up：來源（content:// 位址或檔案路徑）；down：空
        String remotePath = "";   // down：對方那邊的路徑
        String fp = "";           // up：續傳用的指紋（要請對方丟掉半成品時用）
        String reason = "";       // 最近一次停下來的原因（暫停、或失敗的錯誤訊息），給使用者看
        long size, done, updatedAt;

        int percent() { return size > 0 ? (int) Math.min(100, done * 100 / size) : 0; }
    }

    private final File file;
    private final java.util.Map<String, Long> lastWrite = new java.util.HashMap<>();

    PendingStore(File file) { this.file = file; }

    static String idFor(String kind, String peerId, String nameOrPath, long size) {
        return kind + ":" + peerId + ":" + nameOrPath + ":" + size;
    }

    synchronized List<Entry> list(long nowMs) {
        List<Entry> out = new ArrayList<>();
        boolean dropped = false;
        try {
            JSONArray a = new JSONArray(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                Entry e = new Entry();
                e.id = o.optString("id");
                e.kind = o.optString("kind");
                e.peerId = o.optString("peerId");
                e.peerName = o.optString("peerName");
                e.name = o.optString("name");
                e.source = o.optString("source");
                e.remotePath = o.optString("remotePath");
                e.fp = o.optString("fp");
                e.reason = o.optString("reason");
                e.size = o.optLong("size");
                e.done = o.optLong("done");
                e.updatedAt = o.optLong("updatedAt");
                if (e.id.isEmpty() || (!e.kind.equals("up") && !e.kind.equals("down"))) { dropped = true; continue; }
                if (nowMs - e.updatedAt > EXPIRE_MS) { dropped = true; continue; }
                out.add(e);
            }
        } catch (Exception ignored) { }   // 沒有檔案或壞掉：當作空清單
        if (dropped) save(out);
        return out;
    }

    /** 開始（或重新開始）一筆；同一個 id 會被取代。 */
    synchronized void begin(Entry e) {
        List<Entry> l = list(e.updatedAt == 0 ? System.currentTimeMillis() : e.updatedAt);
        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).id.equals(e.id)) l.remove(i);
        if (e.updatedAt == 0) e.updatedAt = System.currentTimeMillis();
        l.add(0, e);
        save(l);
        lastWrite.put(e.id, e.updatedAt);
    }

    /** 傳輸中更新進度：最多每 3 秒寫一次檔。force＝一定寫（失敗收尾時）。 */
    synchronized void progress(String id, long done, long size, String fp, long nowMs, boolean force) {
        Long last = lastWrite.get(id);
        if (!force && last != null && nowMs - last < WRITE_EVERY_MS) return;
        List<Entry> l = list(nowMs);
        boolean hit = false;
        for (Entry e : l) {
            if (!e.id.equals(id)) continue;
            e.done = done;
            if (size > 0) e.size = size;
            if (fp != null && !fp.isEmpty()) e.fp = fp;
            e.updatedAt = nowMs;
            hit = true;
        }
        if (hit) { save(l); lastWrite.put(id, nowMs); }
    }

    /** 記下這一筆停下來的原因（暫停、或失敗的錯誤），之後在「沒傳完」清單顯示。 */
    synchronized void setReason(String id, String reason) {
        List<Entry> l = list(System.currentTimeMillis());
        boolean hit = false;
        for (Entry e : l) if (e.id.equals(id)) { e.reason = reason == null ? "" : reason; hit = true; }
        if (hit) save(l);
    }

    synchronized void remove(String id) { remove(id, System.currentTimeMillis()); }

    synchronized void remove(String id, long nowMs) {
        List<Entry> l = list(nowMs);
        boolean hit = false;
        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).id.equals(id)) { l.remove(i); hit = true; }
        if (hit) save(l);
        lastWrite.remove(id);
    }

    synchronized Entry find(String id, long nowMs) {
        for (Entry e : list(nowMs)) if (e.id.equals(id)) return e;
        return null;
    }

    synchronized Entry find(String id) { return find(id, System.currentTimeMillis()); }

    private void save(List<Entry> l) {
        try {
            JSONArray a = new JSONArray();
            for (Entry e : l) {
                a.put(new JSONObject().put("id", e.id).put("kind", e.kind).put("peerId", e.peerId).put("peerName", e.peerName)
                        .put("name", e.name).put("source", e.source).put("remotePath", e.remotePath).put("fp", e.fp).put("reason", e.reason == null ? "" : e.reason)
                        .put("size", e.size).put("done", e.done).put("updatedAt", e.updatedAt));
            }
            File tmp = new File(file.getPath() + ".tmp");
            Files.write(tmp.toPath(), a.toString().getBytes(StandardCharsets.UTF_8));
            if (!tmp.renameTo(file)) { Files.write(file.toPath(), a.toString().getBytes(StandardCharsets.UTF_8)); tmp.delete(); }
        } catch (Exception ignored) { }
    }
}
