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

    /**
     * 強制結束程序，連它啟動的子孫程序一起（Java 9 的 Process.descendants()；Android 沒有這個方法就只結束主程序）。
     * 用反射呼叫，這樣在 Android 上編譯與執行都不會出錯。root 的 su 是另一個身分的程序，App 本來就殺不到它的子孫，那部分靠 su 在連線斷掉時自己收拾。
     */
    static void killTree(Process p) {
        try {
            Object stream = Process.class.getMethod("descendants").invoke(p);
            Object[] handles = ((java.util.stream.Stream<?>) stream).toArray();
            Class<?> ph = Class.forName("java.lang.ProcessHandle");
            for (Object h : handles) { try { ph.getMethod("destroyForcibly").invoke(h); } catch (Throwable ignored) { } }
        } catch (Throwable ignored) { }
        p.destroyForcibly();
    }

    /** 二進位輸出（截圖）的結果。 */
    static final class Bytes {
        int code = -1;
        byte[] data = new byte[0];
        String err = "";
        boolean timedOut, tooBig;
        boolean ok() { return !timedOut && !tooBig; }
    }

    /**
     * 讀二進位輸出（例如 screencap 的 PNG）：逾時就結束整個程序樹；輸出超過 maxBytes 視為失敗（不是截斷，截斷的 PNG 沒有用）。
     * 標準錯誤另外讀（只留前 4 KB）；兩邊都讀，程序才不會卡在寫入。
     */
    static Bytes drainBytes(final Process p, final long timeoutMs, final int maxBytes) throws Exception {
        final Bytes r = new Bytes();
        final ByteArrayOutputStream o = new ByteArrayOutputStream(Math.min(maxBytes, 1 << 20));
        final ByteArrayOutputStream e = new ByteArrayOutputStream();
        Thread out = new Thread(() -> {
            try (InputStream in = p.getInputStream()) {
                byte[] b = new byte[16 * 1024];
                int n;
                while ((n = in.read(b)) > 0) {
                    synchronized (o) {
                        if (o.size() + n > maxBytes) { r.tooBig = true; continue; }   // 超過上限：繼續讀掉丟棄，程序才會結束
                        o.write(b, 0, n);
                    }
                }
            } catch (Exception ignored) { }
        }, "proc-bytes-out");
        Thread err = new Thread(() -> {
            try (InputStream in = p.getErrorStream()) {
                byte[] b = new byte[1024];
                int n;
                while ((n = in.read(b)) > 0) { synchronized (e) { if (e.size() < 4096) e.write(b, 0, Math.min(n, 4096 - e.size())); } }
            } catch (Exception ignored) { }
        }, "proc-bytes-err");
        out.setDaemon(true);
        err.setDaemon(true);
        out.start();
        err.start();
        boolean done = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        if (!done) {
            r.timedOut = true;
            killTree(p);
            try { p.waitFor(2, TimeUnit.SECONDS); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        }
        out.join(done ? 3000 : 500);
        err.join(300);
        if (out.isAlive()) { try { p.getInputStream().close(); } catch (Exception ignored) { } out.interrupt(); }
        synchronized (o) { r.data = o.toByteArray(); }
        synchronized (e) { r.err = e.toString("UTF-8"); }
        r.code = done ? p.exitValue() : -1;
        return r;
    }

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
            killTree(p);
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
