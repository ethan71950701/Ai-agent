package com.phoneai.overlay;

/** 正在跑的自動操作的狀況說明（純邏輯）：上一個操作還沒結束、又有新任務進來時，要讓使用者看得懂是怎麼回事、可以選擇停掉它。 */
final class RunStatus {
    private RunStatus() { }

    /** 多久沒有任何動靜就算「可能卡住了」（毫秒）。 */
    static final long STUCK_MS = 120_000;

    static boolean looksStuck(long nowMs, long lastBeatMs) { return nowMs - lastBeatMs > STUCK_MS; }

    private static String seconds(long ms) { return Math.max(0, ms / 1000) + L.t(" 秒"); }

    /** 給確認框的說明。step＝目前做到第幾步（0＝還沒開始第一步）。 */
    static String askStop(long nowMs, long startedMs, long lastBeatMs, int step) {
        String where = step > 0 ? L.t("目前在第 ") + step + L.t(" 步") : L.t("還在準備");
        String quiet = nowMs - lastBeatMs > 10_000 ? L.t("，最後一次動靜是 ") + seconds(nowMs - lastBeatMs) + L.t("前") + (looksStuck(nowMs, lastBeatMs) ? L.t("（可能卡住了）") : "") : "";
        return L.t("上一個操作還沒結束（已進行 ") + seconds(nowMs - startedMs) + "，" + where + quiet + L.t("）。\n要停止它、改做這一個嗎？");
    }

    /** 請它停、等了一陣子還是停不下來。 */
    static String cannotStop() {
        return L.t("上一個操作停不下來（可能卡住了）。請稍等一下再試；一直不行的話，到系統設定把 Vertex「強制停止」再重新打開。");
    }
}
