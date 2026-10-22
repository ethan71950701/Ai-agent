package com.phoneai.overlay;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import java.io.File;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * 背景虛擬螢幕的即時影片預覽：連上 root 端（vdisplay.py --serve）在 127.0.0.1:47196 轉送的 H.264 原始串流
 * （第一行要送本 App 的 token，只有 App 自己和 root 讀得到），用手機的硬體解碼器直接畫到 TextureView。
 * 比「每半秒截一張圖」順很多（約 30 fps、延遲很低）。連不上或解碼失敗就回呼 onFail，由 PreviewView 退回截圖模式。
 */
final class H264Stream implements TextureView.SurfaceTextureListener {
    interface Callback {
        void onFirstFrame();
        void onFail(String why);
    }

    static final int PORT = 47196;

    private final Context ctx;
    private final Callback cb;
    private volatile boolean running;
    private volatile Socket sock;
    private Thread worker;
    private volatile boolean gotFrame;
    private volatile boolean failed;

    private final int vw, vh;

    H264Stream(Context ctx, int vw, int vh, Callback cb) {
        this.ctx = ctx;
        this.vw = vw;
        this.vh = vh;
        this.cb = cb;
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
        if (running) return;
        running = true;
        final Surface surface = new Surface(st);
        worker = new Thread(() -> run(surface), "vd-h264");
        worker.start();
    }

    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) { }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
        stop();
        return true;
    }

    @Override public void onSurfaceTextureUpdated(SurfaceTexture st) { }

    void stop() {
        running = false;
        try { if (sock != null) sock.close(); } catch (Exception ignored) { }
        if (worker != null) worker.interrupt();
    }

    private void fail(String why) {
        if (failed) return;
        failed = true;
        if (running && !gotFrame) cb.onFail(why);  // 已經出過畫面後才斷線（例如任務結束）不算失敗，不用退回
    }

    private void run(Surface surface) {
        MediaCodec codec = null;
        Thread out = null;
        try {
            String token = new String(Files.readAllBytes(new File(ctx.getFilesDir(), "token").toPath()),
                    StandardCharsets.UTF_8).trim();
            Socket s = new Socket();
            sock = s;
            s.connect(new InetSocketAddress("127.0.0.1", PORT), 2000);
            s.getOutputStream().write((token + "\n").getBytes(StandardCharsets.UTF_8));
            s.getOutputStream().flush();

            MediaFormat fmt = MediaFormat.createVideoFormat("video/avc", vw, vh);
            fmt.setInteger(MediaFormat.KEY_LOW_LATENCY, 1);
            codec = MediaCodec.createDecoderByType("video/avc");
            codec.configure(fmt, surface, null, 0);
            codec.start();
            final MediaCodec dec = codec;
            out = new Thread(() -> drainOutput(dec), "vd-h264-out");
            out.start();

            InputStream in = s.getInputStream();
            byte[] acc = new byte[1 << 20];
            int len = 0;
            byte[] chunk = new byte[1 << 16];
            long pts = 0;
            int n;
            while (running && (n = in.read(chunk)) > 0) {
                if (len + n > acc.length) {  // 單一幀特別大時才會長；上限 16MB 以免失控
                    if (acc.length >= (16 << 20)) { len = 0; }
                    else acc = java.util.Arrays.copyOf(acc, acc.length * 2);
                }
                System.arraycopy(chunk, 0, acc, len, n);
                len += n;
                List<int[]> nals = split(acc, len);  // [起點, 終點)，最後一個可能不完整的不在裡面
                int consumed = 0;
                for (int[] r : nals) {
                    pts += feed(codec, acc, r[0], r[1], pts);
                    consumed = r[1];
                }
                if (consumed > 0) {
                    System.arraycopy(acc, consumed, acc, 0, len - consumed);
                    len -= consumed;
                }
            }
            fail(L.t("串流結束"));
        } catch (Exception e) {
            Log.i(OverlayService.TAG, "h264 stream: " + e);
            fail(String.valueOf(e));
        } finally {
            running = false;
            try { if (sock != null) sock.close(); } catch (Exception ignored) { }
            if (out != null) {
                out.interrupt();
                try { out.join(500); } catch (InterruptedException ignored) { }
            }
            try { if (codec != null) { codec.stop(); codec.release(); } } catch (Exception ignored) { }
            try { surface.release(); } catch (Exception ignored) { }
        }
    }

    /** 在 acc[0, len) 裡找起始碼（00 00 01），回傳每個「完整」NAL 的 [起點, 終點)；起點含起始碼；最後一個不一定完整，不回傳。 */
    static List<int[]> split(byte[] acc, int len) {
        List<Integer> starts = new ArrayList<>();
        for (int i = 0; i + 2 < len; i++) {
            if (acc[i] == 0 && acc[i + 1] == 0 && acc[i + 2] == 1) {
                starts.add(i > 0 && acc[i - 1] == 0 ? i - 1 : i);
                i += 2;
            }
        }
        List<int[]> res = new ArrayList<>();
        for (int k = 0; k + 1 < starts.size(); k++) res.add(new int[]{starts.get(k), starts.get(k + 1)});
        return res;
    }

    /** 餵一個 NAL 給解碼器；回傳要把時間往前推多少（畫面幀才推）。 */
    private long feed(MediaCodec codec, byte[] a, int from, int to, long pts) throws InterruptedException {
        int hdr = (a[from + 2] == 1) ? 3 : 4;
        int type = a[from + hdr] & 0x1F;
        if (type == 9) return 0;  // AUD 只是 vdisplay.py 補來當「這張畫面結束」的記號，不餵給解碼器（有的解碼器把單獨一個 AUD 當成一張壞畫面）
        int idx = -1;
        for (int tries = 0; tries < 4 && running && idx < 0; tries++) idx = codec.dequeueInputBuffer(15000);
        if (idx < 0) return 0;  // 解碼器忙不過來：丟掉這個 NAL（到下一個關鍵幀前畫面可能有點破）
        ByteBuffer ib = codec.getInputBuffer(idx);
        int size = to - from;
        if (ib == null || size > ib.capacity()) {
            codec.queueInputBuffer(idx, 0, 0, pts, 0);
            return 0;
        }
        ib.clear();
        ib.put(a, from, size);
        codec.queueInputBuffer(idx, 0, size, pts, 0);
        return (type == 1 || type == 5) ? 16667 : 0;
    }

    private void drainOutput(MediaCodec codec) {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        long t0 = System.nanoTime();
        int shown = 0;
        try {
            while (running) {
                int i = codec.dequeueOutputBuffer(info, 10000);
                if (i >= 0) {
                    codec.releaseOutputBuffer(i, true);  // 馬上顯示，不等時間戳（低延遲）
                    shown++;
                    long now = System.nanoTime();
                    if (now - t0 >= 3_000_000_000L) {  // 每 3 秒記一次「解碼後實際送去顯示」的幀率；畫面靜止時會很低（沒有新畫面）
                        Log.i(OverlayService.TAG, String.format(java.util.Locale.US, "preview fps shown=%.1f",
                                shown * 1e9 / (now - t0)));
                        t0 = now;
                        shown = 0;
                    }
                    if (!gotFrame) {
                        gotFrame = true;
                        cb.onFirstFrame();
                    }
                }
            }
        } catch (Exception e) {
            Log.i(OverlayService.TAG, "h264 out: " + e);
            fail(String.valueOf(e));
        }
    }
}
