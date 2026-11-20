package com.phoneai.overlay;

import java.io.EOFException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

/** 續傳用的純邏輯：檔案指紋、暫存檔名、斷點是否可接、哪些錯誤值得自動重試。 */
final class Resume {
    private Resume() { }

    static final int HEAD_BYTES = 1024 * 1024;   // 指紋只看檔案開頭 1 MiB（加上大小與檔名），不必把整個檔案先讀一遍

    /** 同一個檔案（開頭、大小、檔名都相同）每次算出同一個指紋，續傳時兩邊靠它認得「是同一份」。32 個十六進位字元。 */
    static String fingerprint(byte[] head, int len, long size, String name) {
        java.security.MessageDigest md = Checksum.newDigest();
        md.update(head, 0, Math.max(0, Math.min(len, head.length)));
        md.update(("|" + size + "|" + (name == null ? "" : name)).getBytes(StandardCharsets.UTF_8));
        return Checksum.hex(md.digest()).substring(0, 32);
    }

    static boolean validFp(String fp) { return fp != null && fp.matches("[0-9a-f]{32}"); }

    /** 接收端存半成品的檔名：帶上傳送者編號（不同裝置互不干擾）與指紋，名稱固定，下次才找得到。 */
    static String partName(String peerId, String fp) {
        java.security.MessageDigest md = Checksum.newDigest();
        md.update((peerId == null ? "" : peerId).getBytes(StandardCharsets.UTF_8));
        return ".resume-" + Checksum.hex(md.digest()).substring(0, 8) + "-" + fp + ".part";
    }

    /** 對方要從 want 接著傳：want 為 0 就是重頭來（舊的半成品會被蓋掉）；大於 0 時，半成品長度必須剛好等於 want，而且不能超過總大小。 */
    static boolean offsetOk(long have, long want, long size) {
        if (want == 0) return true;
        return want > 0 && want <= size && have == want;
    }

    /** 這種錯誤是網路暫時的問題，值得自動接著傳；校驗碼不符、空間不夠、被拒絕這類不是。 */
    static boolean retryable(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SocketException || t instanceof SocketTimeoutException || t instanceof EOFException) return true;
            String m = t.getMessage();
            if (m != null && (m.startsWith("failed to connect to") || m.contains("Connection reset") || m.contains("Broken pipe")
                    || m.contains("對方沒有回應") || m.contains("truncated") || m.contains("Software caused connection abort"))) return true;
        }
        return false;
    }

    static long backoffMs(int attempt) { return Math.min(10_000L, 2_000L * Math.max(1, attempt)); }

    static final int MAX_ATTEMPTS = 6;
}
