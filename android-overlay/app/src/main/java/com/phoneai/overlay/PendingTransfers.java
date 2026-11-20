package com.phoneai.overlay;

import android.content.Context;

import java.io.File;
import java.util.List;

/** 「沒傳完的檔案」清單的存取入口（實際邏輯在 PendingStore）。 */
final class PendingTransfers {
    private PendingTransfers() { }

    private static PendingStore store;

    static synchronized PendingStore store(Context c) {
        if (store == null) store = new PendingStore(new File(c.getApplicationContext().getFilesDir(), "transfer_pending.json"));
        return store;
    }

    static List<PendingStore.Entry> list(Context c) { return store(c).list(System.currentTimeMillis()); }

    static int count(Context c) { return list(c).size(); }

    /** 登記一筆傳輸（呼叫端之後交給 TransferClient.track）。 */
    static PendingStore.Entry entry(String kind, TransferStore.Peer peer, String name, String source, String remotePath, long size) {
        PendingStore.Entry e = new PendingStore.Entry();
        e.kind = kind;
        e.peerId = peer == null ? "" : peer.id;
        e.peerName = peer == null ? "" : peer.name;
        e.name = name;
        e.source = source == null ? "" : source;
        e.remotePath = remotePath == null ? "" : remotePath;
        e.size = Math.max(0, size);
        e.id = PendingStore.idFor(kind, e.peerId, kind.equals("down") ? e.remotePath : name, e.size);
        e.updatedAt = System.currentTimeMillis();
        return e;
    }

    /** 登記並開始追蹤；找不到對象（peer 為 null）就不追蹤。 */
    static void track(Context c, TransferClient cl, PendingStore.Entry e) {
        if (e.peerId.isEmpty()) return;
        cl.track(store(c), e);
    }
}
