package com.phoneai.overlay;

import java.io.File;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 分段同時下載（不依賴 Android，純 Java，雲端可直接測）。為什麼：單一連線在某些網路路線上跑不快（實測使用者的手機下載 42MB 的模型很慢，
 * 而雲端同一個檔案 5 秒就下完），多條連線同時抓不同段落，常常能把頻寬用滿。伺服器不支援分段（沒有 Accept-Ranges）或大小未知時，退回單一連線。
 * 每一段失敗會從「已經寫到的位置」重試最多 3 次，不用整個重來。進度回呼在呼叫的執行緒上，每 0.4 秒一次。
 */
final class ParallelDownloader {
    interface Progress { void on(long done, long total, double bytesPerSec, int conns); }

    private ParallelDownloader() { }

    static void download(String url, File dest, int conns, Progress p) throws Exception {
        long total = -1;
        boolean ranges = false;
        HttpURLConnection h = open(url, "HEAD", null);
        try {
            if (h.getResponseCode() == 200) {
                total = h.getContentLengthLong();
                ranges = "bytes".equalsIgnoreCase(h.getHeaderField("Accept-Ranges"));
            }
        } finally {
            h.disconnect();
        }
        if (total <= 0 || !ranges || conns <= 1) {
            single(url, dest, total, p);
            return;
        }
        try (RandomAccessFile pre = new RandomAccessFile(dest, "rw")) { pre.setLength(total); }
        final int n = (int) Math.min(conns, Math.max(1, total / (1 << 20)));   // 檔案太小就不開那麼多條
        final long chunk = (total + n - 1) / n;
        final AtomicLong got = new AtomicLong();
        final AtomicReference<String> fail = new AtomicReference<>();
        Thread[] ts = new Thread[n];
        for (int i = 0; i < n; i++) {
            final long start = i * chunk, end = Math.min(total - 1, start + chunk - 1);
            ts[i] = new Thread(() -> {
                long pos = start;
                int tries = 0;
                while (pos <= end && fail.get() == null) {
                    try (RandomAccessFile raf = new RandomAccessFile(dest, "rw")) {
                        HttpURLConnection c = open(url, "GET", "bytes=" + pos + "-" + end);
                        try {
                            if (c.getResponseCode() != 206) throw new Exception("伺服器回 HTTP " + c.getResponseCode());
                            raf.seek(pos);
                            byte[] buf = new byte[65536];
                            try (InputStream in = c.getInputStream()) {
                                int r;
                                while (pos <= end && (r = in.read(buf, 0, (int) Math.min(buf.length, end - pos + 1))) > 0) {
                                    raf.write(buf, 0, r);
                                    pos += r;
                                    got.addAndGet(r);
                                }
                            }
                        } finally {
                            c.disconnect();
                        }
                    } catch (Exception e) {
                        if (++tries > 3) { fail.compareAndSet(null, String.valueOf(e.getMessage() != null ? e.getMessage() : e)); return; }
                        try { Thread.sleep(800L * tries); } catch (InterruptedException ie) { return; }
                    }
                }
            }, "dl-" + i);
            ts[i].start();
        }
        long lastT = System.currentTimeMillis(), lastG = 0;
        boolean alive = true;
        while (alive) {
            Thread.sleep(400);
            alive = false;
            for (Thread t : ts) if (t.isAlive()) { alive = true; break; }
            long now = System.currentTimeMillis(), g = got.get();
            double bps = (g - lastG) * 1000.0 / Math.max(1, now - lastT);
            lastT = now;
            lastG = g;
            p.on(Math.min(g, total), total, bps, n);
            if (fail.get() != null) break;
        }
        for (Thread t : ts) t.join(3000);
        if (fail.get() != null) throw new Exception("下載失敗：" + fail.get());
        if (got.get() < total) throw new Exception("下載不完整（" + got.get() + "／" + total + "）");
    }

    private static void single(String url, File dest, long total, Progress p) throws Exception {
        HttpURLConnection h = open(url, "GET", null);
        try {
            if (h.getResponseCode() != 200) throw new Exception("下載失敗 HTTP " + h.getResponseCode());
            if (total <= 0) total = h.getContentLengthLong();
            long got = 0, lastT = System.currentTimeMillis(), lastG = 0;
            try (InputStream in = h.getInputStream(); java.io.FileOutputStream out = new java.io.FileOutputStream(dest)) {
                byte[] buf = new byte[65536];
                int r;
                while ((r = in.read(buf)) > 0) {
                    out.write(buf, 0, r);
                    got += r;
                    long now = System.currentTimeMillis();
                    if (now - lastT >= 400) {
                        p.on(got, total, (got - lastG) * 1000.0 / (now - lastT), 1);
                        lastT = now;
                        lastG = got;
                    }
                }
            }
        } finally {
            h.disconnect();
        }
    }

    private static HttpURLConnection open(String url, String method, String range) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(20000);
        c.setReadTimeout(30000);
        if (range != null) c.setRequestProperty("Range", range);
        return c;
    }
}
