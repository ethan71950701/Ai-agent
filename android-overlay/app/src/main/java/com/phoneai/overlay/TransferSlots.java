package com.phoneai.overlay;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 同時進行的傳輸各自一格（純 Java）：每條執行緒在自己的那一格裡有「停止」「暫停」旗標，
 * 一筆結束只收掉自己的，不會清掉別人的；按通知上的暫停／停止只作用在「通知上顯示的那一筆」（最早開始、還活著的）。
 * 久沒更新（卡死、忘了收尾）的格子會被當作已經結束，免得「有傳輸進行中」永遠成立。
 */
final class TransferSlots {
    /** 傳輸的執行緒已經結束、卻沒收尾的格子立刻當作結束；執行緒還活著（可能卡在網路寫入）的，放寬到這麼久沒更新才當作結束。 */
    static final long STALE_MS = 600_000;

    static final class Slot {
        final String id;
        volatile boolean stop, pause, shown;   // shown：通知已經為這一筆顯示過（用來只在第一次進度時收起助理面板）
        volatile long touched;
        volatile java.io.Closeable closer;   // 這一筆目前用的連線：按停止／暫停時直接關掉它，卡住的寫入才會立刻跳出來
        final Thread owner = Thread.currentThread();
        Slot(String id, long now) { this.id = id; this.touched = now; }
    }

    private final Map<String, Slot> slots = new LinkedHashMap<>();
    private final ThreadLocal<Slot> mine = new ThreadLocal<>();
    private int counter;
    private static final ThreadLocal<java.io.Closeable> THREAD_CLOSER = new ThreadLocal<>();

    /** 這條執行緒現在用的連線：記下來，之後（或現在已經有的）這一格的停止／暫停會把它關掉。 */
    synchronized void bindCloser(java.io.Closeable c) {
        THREAD_CLOSER.set(c);
        Slot s = mine.get();
        if (s != null && slots.containsKey(s.id)) s.closer = c;
    }

    private static void closeAsync(final java.io.Closeable c) {
        if (c == null) return;
        Thread t = new Thread(() -> { try { c.close(); } catch (Exception ignored) { } }, "transfer-close");   // 另開執行緒：呼叫端可能是主執行緒
        t.setDaemon(true);
        t.start();
    }

    private void prune(long now) {
        for (Iterator<Slot> it = slots.values().iterator(); it.hasNext();) {
            Slot s = it.next();
            if (!s.owner.isAlive() || now - s.touched > STALE_MS) it.remove();
        }
    }

    /** 這條執行緒的格子；沒有就開一格（算進「進行中」）。每次呼叫都算「還活著」。 */
    synchronized Slot enter(long now) {
        prune(now);
        Slot s = mine.get();
        if (s == null || !slots.containsKey(s.id)) {
            s = new Slot("t" + (++counter), now);
            mine.set(s);
            slots.put(s.id, s);
        }
        s.touched = now;
        java.io.Closeable tc = THREAD_CLOSER.get();
        if (tc != null) s.closer = tc;
        return s;
    }

    /** 這條執行緒目前的格子（不建立）；沒有或已被當成結束回 null。 */
    synchronized Slot current(long now) {
        prune(now);
        Slot s = mine.get();
        return s != null && slots.containsKey(s.id) ? s : null;
    }

    /** 新的傳輸要開始：丟掉這條執行緒上一次留下的格子（旗標跟著清掉）。 */
    synchronized void begin() {
        Slot s = mine.get();
        if (s != null) slots.remove(s.id);
        mine.remove();
    }

    /** 這條執行緒的傳輸結束：只收掉自己的格子。回傳收掉之後是不是沒有任何進行中的傳輸了。 */
    synchronized boolean leave(long now) {
        begin();
        prune(now);
        return slots.isEmpty();
    }

    synchronized boolean active(long now) {
        prune(now);
        return !slots.isEmpty();
    }

    /** 通知上顯示的那一筆：最早開始、還活著的。 */
    synchronized Slot primary(long now) {
        prune(now);
        Iterator<Slot> it = slots.values().iterator();
        return it.hasNext() ? it.next() : null;
    }

    synchronized boolean isPrimary(Slot s, long now) {
        Slot p = primary(now);
        return p != null && p == s;
    }

    /** 按了停止：只停通知上顯示的那一筆。沒有進行中的就什麼都不做。 */
    synchronized boolean stopPrimary(long now) {
        Slot p = primary(now);
        if (p == null) return false;
        p.stop = true;
        closeAsync(p.closer);
        return true;
    }

    synchronized boolean pausePrimary(long now) {
        Slot p = primary(now);
        if (p == null) return false;
        p.pause = true;
        closeAsync(p.closer);
        return true;
    }
}
