package com.phoneai.overlay;

import java.util.Locale;

/** 傳輸進度的速度與剩餘時間（純 Java）：用一小段時間的平均，免得數字一直跳；也負責把長檔名縮短、組出通知文字。 */
final class TransferProgress {
    private long lastAt, lastBytes;
    private double speed;   // bytes/秒，平滑過的

    /** 每次進度更新呼叫；回傳目前的速度（bytes/秒）。距離上次取樣不到 1 秒就沿用舊值。 */
    synchronized double update(long nowMs, long doneBytes) {
        if (lastAt == 0 || doneBytes < lastBytes) {   // 第一次，或進度倒退（重新開始、續傳換了起點）
            lastAt = nowMs;
            lastBytes = doneBytes;
            speed = 0;
            return 0;
        }
        long dt = nowMs - lastAt;
        if (dt >= 1000) {
            double inst = (doneBytes - lastBytes) * 1000.0 / dt;
            speed = speed == 0 ? inst : speed * 0.6 + inst * 0.4;
            lastAt = nowMs;
            lastBytes = doneBytes;
        }
        return speed;
    }

    /** 剩餘秒數；速度或總量不明回 -1。 */
    static long etaSeconds(long total, long done, double bytesPerSec) {
        if (bytesPerSec < 1 || total <= 0 || done >= total) return -1;
        return Math.round((total - done) / bytesPerSec);
    }

    static String speedText(double bps) {
        if (bps < 1) return "";
        if (bps < 1024 * 1024) return String.format(Locale.US, "%.0f KB/s", bps / 1024);
        return String.format(Locale.US, "%.1f MB/s", bps / 1048576.0);
    }

    static String etaText(long sec) {
        if (sec < 0) return "";
        if (sec < 60) return sec + " 秒";
        if (sec < 3600) return (sec / 60) + " 分" + (sec % 60 == 0 ? "" : " " + (sec % 60) + " 秒");
        return (sec / 3600) + " 小時" + ((sec % 3600) / 60 == 0 ? "" : " " + ((sec % 3600) / 60) + " 分");
    }

    /** 收合時只看得到一行：百分比、速度、剩餘時間。 */
    static String compact(int pct, double bps, long etaSec) {
        StringBuilder sb = new StringBuilder().append(pct).append('%');
        String sp = speedText(bps);
        if (!sp.isEmpty()) sb.append("　").append(sp);
        String eta = etaText(etaSec);
        if (!eta.isEmpty()) sb.append("　剩 ").append(eta);
        return sb.toString();
    }

    /** 檔名太長就把中間換成「…」，保留開頭和結尾（副檔名看得到）。 */
    static String ellipsizeMiddle(String s, int max) {
        if (s == null || max < 5 || s.length() <= max) return s == null ? "" : s;
        int keepEnd = Math.min(12, (max - 1) / 2);
        int keepStart = max - 1 - keepEnd;
        return s.substring(0, keepStart) + "…" + s.substring(s.length() - keepEnd);
    }
}
