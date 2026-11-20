package com.phoneai.overlay;

/**
 * 臨時開起的互傳伺服器什麼時候可以關（純 Java）：
 * 一直沒有人連進來 → 等 90 秒就關；有人連過、之後所有連線都結束 → 再等 10 秒就關；傳輸中（有連線）不會被切斷，大檔案可能傳很久；只有超過 6 小時的保險上限才停（防止真的卡死）。
 * 傳輸中（還有連線）不會關。每隔幾秒呼叫一次 tick。
 */
final class IdleWatch {
    static final long NO_VISITOR_MS = 90_000, AFTER_DONE_MS = 10_000, MAX_ACTIVE_MS = 6L * 3600_000;

    private final long start, afterDone;
    private boolean sawConnection;
    private long idleSince = -1;

    IdleWatch(long startMs) { this(startMs, AFTER_DONE_MS); }

    /** afterDoneMs：最後一個連線結束後再等多久才關（瀏覽時要久一點，不然每點一層都要重新叫醒）。 */
    IdleWatch(long startMs, long afterDoneMs) { start = startMs; afterDone = afterDoneMs; }

    /** active：目前的連線數。回 true 表示可以關了。 */
    boolean tick(long nowMs, int active) {
        if (active > 0 && nowMs - start >= MAX_ACTIVE_MS) return true;
        if (active > 0) {
            sawConnection = true;
            idleSince = -1;
            return false;
        }
        if (!sawConnection) return nowMs - start >= NO_VISITOR_MS;
        if (idleSince < 0) idleSince = nowMs;
        return nowMs - idleSince >= afterDone;
    }
}
