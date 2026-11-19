package com.phoneai.overlay;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * 執行外部程序（su、sh）的輸出讀取，純 Java（不依賴 Android，可單元測試）。
 * 重點是整個生命週期都有界：逾時就強制結束、輸出只留前 maxBytes（多的讀掉丟棄，程序才不會卡在寫入）、
 * 讀取放在另一條執行緒，呼叫端絕不會因為「子孫程序還握著輸出管道」而卡住。
 */
final class ProcRun {
    private ProcRun() { }

    /** 結束碼 -1 代表逾時（已強制結束）。 */
    static final int TIMED_OUT = -1;

    /**
     * 讀完程序的輸出（標準輸出；呼叫端要用 redirectErrorStream(true) 把錯誤輸出合進來，不然錯誤輸出沒人讀可能塞滿）。
     * @return {Integer 結束碼（逾時＝-1）, String 輸出文字（逾時時最後加一行說明）}
     */
    static Object[] drain(final Process p, final long timeoutMs, final int maxBytes) throws Exception {
        final ByteArrayOutputStream o = new ByteArrayOutputStream();
        Thread reader = new Thread(() -> {
            try (InputStream in = p.getInputStream()) {
                byte[] b = new byte[4096];
                int n;
                while ((n = in.read(b)) > 0) {
                    synchronized (o) {
                        int room = maxBytes - o.size();
                        if (room > 0) o.write(b, 0, Math.min(n, room));
                    }
                }
            } catch (Exception ignored) { }
        }, "proc-reader");
        reader.setDaemon(true);
        reader.start();
        boolean done = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        if (!done) {
            p.destroyForcibly();
            try { p.waitFor(2, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        reader.join(done ? 2000 : 500);   // 程序結束後再給讀取一點時間把管道裡剩下的讀完；有子孫程序握著管道就不再等
        if (reader.isAlive()) {
            try { p.getInputStream().close(); } catch (Exception ignored) { }
            reader.interrupt();
        }
        String text;
        synchronized (o) { text = o.toString("UTF-8"); }
        if (!done) return new Object[]{TIMED_OUT, text + "\n(逾時，已強制結束)"};
        return new Object[]{p.exitValue(), text};
    }

    /**
     * su 本身沒能把腳本交出去（不是腳本自己失敗）：結束碼 126／127，或輸出是 su 的選項／用法錯誤。
     * 只有這種情況才換下一種 su 寫法再試；腳本已經跑過、只是結束碼不是 0，絕不能再跑一次（會重複執行有副作用的指令）。
     */
    static boolean suNotLaunched(int code, String out) {
        if (code == 0 || code == TIMED_OUT) return false;
        if (code == 126 || code == 127) return true;
        String l = out == null ? "" : out.toLowerCase(java.util.Locale.ROOT);
        return l.startsWith("su:") || l.contains("\nsu:") || l.contains("invalid option") || l.contains("unknown option")
                || l.contains("unrecognized option") || l.contains("usage: su");
    }
}
