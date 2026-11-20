package com.phoneai.overlay;

/**
 * 臨時開起的互傳伺服器什麼時候可以關（純 Java）：
 * 一直沒有人連進來 → 等 90 秒就關；有人連過、之後所有連線都結束 → 再等 10 秒就關；最久 10 分鐘（防止卡住一直開著）。
 * 傳輸中（還有連線）不會關。每隔幾秒呼叫一次 tick。
 */
final class IdleWatch {
    static final long NO_VISITOR_MS = 90_000, AFTER_DONE_MS = 10_000, HARD_LIMIT_MS = 600_000;

    private final long start;
    private boolean sawConnection;
    private long idleSince = -1;

    IdleWatch(long startMs) { start = startMs; }

    /** active：目前的連線數。回 true 表示可以關了。 */
    boolean tick(long nowMs, int active) {
        if (nowMs - start >= HARD_LIMIT_MS) return true;
        if (active > 0) {
            sawConnection = true;
            idleSince = -1;
            return false;
        }
        if (!sawConnection) return nowMs - start >= NO_VISITOR_MS;
        if (idleSince < 0) idleSince = nowMs;
        return nowMs - idleSince >= AFTER_DONE_MS;
    }
}
