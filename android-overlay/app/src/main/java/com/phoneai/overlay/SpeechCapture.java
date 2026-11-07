package com.phoneai.overlay;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * 錄一句話（16kHz 單聲道）：用音量判斷有沒有人說話，說完停頓約 1 秒就結束；8 秒都沒開口就放棄；最長 20 秒。
 * 只負責錄音，不做辨識（辨識交給 SherpaEngine）。callback 都在主執行緒。在 App 前景錄音在小米上可行（麥克風實驗已驗證）。
 */
final class SpeechCapture {
    interface Cb {
        void onListening();
        void onLevel(float v);      // 0~1，給聲波條用
        void onDone(short[] pcm);   // 錄到的聲音；沒聽到聲音或中途取消 = null
        void onError(String msg);
    }

    static final int RATE = 16000;
    private static final String TAG = OverlayService.TAG;
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private volatile boolean stop, cancel;

    private SpeechCapture() { }

    static SpeechCapture start(Cb cb) { return start(null, cb, null); }

    /**
     * partial 不是 null 時邊說邊辨識：SenseVoice 不是串流模型，所以每約 0.8 秒把「到目前為止的整段聲音」重新辨識一次，
     * 把最新的文字交給 partial（主執行緒）。上一次還沒辨識完就跳過這一輪，不會越積越多；前面的字可能被後面的修正。
     */
    static SpeechCapture start(android.content.Context ctx, Cb cb, java.util.function.Consumer<String> partial) {
        SpeechCapture c = new SpeechCapture();
        c.ctx = ctx == null ? null : ctx.getApplicationContext();
        c.partial = partial;
        new Thread(() -> c.run(cb), "speech-capture").start();
        return c;
    }

    private android.content.Context ctx;
    private java.util.function.Consumer<String> partial;
    private final java.util.concurrent.atomic.AtomicBoolean decoding = new java.util.concurrent.atomic.AtomicBoolean();
    /** 最近一次錄音是不是撞到長度上限（話可能還沒說完：呼叫端不要自動送出）。 */
    static volatile boolean lastHitCap;
    private volatile boolean finished;
    private volatile String lastPartial;
    private static final java.util.concurrent.ExecutorService PARTIAL_EX = java.util.concurrent.Executors.newSingleThreadExecutor();

    /** 已經分段辨識完、固定下來的前面幾段文字（說很長時，每約 15 秒的停頓處先辨識一段）。 */
    private final StringBuilder committed = new StringBuilder();
    private final java.util.concurrent.atomic.AtomicInteger pendingCommits = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.List<java.util.concurrent.Future<?>> commitFutures = new ArrayList<>();
    private static final java.util.concurrent.ExecutorService COMMIT_EX = java.util.concurrent.Executors.newSingleThreadExecutor();
    /** 最近一次錄音：前面分段已經辨識好的文字、以及最後一段有沒有人聲（給 {@link #recognizeFinal} 用）。 */
    private static volatile String lastCommitted = "";
    private static volatile boolean lastSegSpeech;
    private static volatile short[] lastAll;   // 最近一次錄音的前 20 秒（聲紋比對用）

    private String committedNow() { synchronized (committed) { return committed.toString(); } }

    /** 把 chunks[from..] 這一段送去辨識，結果按順序接在 committed 後面。 */
    private void commitSegment(ArrayList<short[]> chunks, int from, int samples) {
        final short[] snap = join(chunks, from, samples);
        pendingCommits.incrementAndGet();
        synchronized (commitFutures) {
            commitFutures.add(COMMIT_EX.submit(() -> {
                try {
                    String t = SherpaEngine.recognize(ctx, toFloat(snap), RATE);
                    if (t != null && !t.isEmpty()) synchronized (committed) { committed.append(t); }
                } catch (Throwable ignored) {
                } finally {
                    pendingCommits.decrementAndGet();
                }
            }));
        }
        lastPartial = null;
    }

    private static short[] join(ArrayList<short[]> chunks, int from, int samples) {
        short[] out = new short[samples];
        int p = 0;
        for (int i = from; i < chunks.size() && p < samples; i++) {
            short[] c = chunks.get(i);
            int n = Math.min(c.length, samples - p);
            System.arraycopy(c, 0, out, p, n);
            p += n;
        }
        return out;
    }

    /** 說完之後的最後辨識：前面分段辨識好的文字 ＋ 最後一段（沒有人聲就不辨識）。 */
    static String recognizeFinal(android.content.Context c, short[] pcm) {
        short[] all = lastAll;
        if (all != null && all.length > 0) SpeakerId.scoreAsync(c, toFloat(all));   // 聲紋（實驗）：背景算跟你登錄的聲紋像不像，只記錄、不影響辨識
        String head = lastCommitted;
        String tail = "";
        if (lastSegSpeech && pcm != null && pcm.length > RATE / 5) tail = SherpaEngine.recognize(c, toFloat(pcm), RATE);
        return (head + (tail == null ? "" : tail)).trim();
    }

    private void maybePartial(ArrayList<short[]> chunks, int from, int samples) {
        if (partial == null || ctx == null || decoding.get() || pendingCommits.get() > 0 || samples < RATE / 2) return;
        final short[] snap = join(chunks, from, samples);
        final String head = committedNow();
        decoding.set(true);
        PARTIAL_EX.execute(() -> {
            try {
                String t0 = SherpaEngine.recognize(ctx, toFloat(snap), RATE);
                final String t = t0 == null ? "" : head + t0;   // 畫面上顯示：前面固定的 ＋ 這一段目前聽到的
                if (!finished && !cancel && !t.isEmpty() && pendingCommits.get() == 0) {
                    // 穩定化：整段重新辨識時，偶爾新結果比上一次短很多（前面的字掉了）。那種就先不更新畫面，等下一輪；最後一次完整辨識不受影響
                    String prev = lastPartial;
                    if (prev != null && t.length() < prev.length() * 0.7f && !prev.startsWith(t)) return;
                    lastPartial = t;
                    UI.post(() -> { if (!finished && !cancel) partial.accept(t); });
                }
            } catch (Throwable ignored) {
            } finally {
                decoding.set(false);
            }
        });
    }

    /** 立刻結束，交出目前錄到的聲音。 */
    void stop() { stop = true; }

    /** 丟掉，不回報結果。 */
    void cancel() { cancel = true; }

    /** 16 位元 PCM → -1～1 的 float（辨識引擎要的格式）。 */
    static float[] toFloat(short[] pcm) {
        float[] f = new float[pcm.length];
        for (int i = 0; i < f.length; i++) f[i] = pcm[i] / 32768f;
        return f;
    }

    private void run(Cb cb) {
        AudioRecord ar = null;
        try {
            int min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            ar = new AudioRecord(MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min, 16384));
            if (ar.getState() != AudioRecord.STATE_INITIALIZED) throw new Exception(L.t("麥克風開不起來"));
            ar.startRecording();
            UI.post(cb::onListening);
            ArrayList<short[]> chunks = new ArrayList<>();
            short[] buf = new short[1600];   // 0.1 秒一包
            double floor = 0;
            int calib = 0, silent = 0, total = 0;
            int segStart = 0, segSamples = 0;   // 目前這一段從第幾包開始、有幾個取樣；說很長時每約 15 秒分一段先辨識
            boolean segSpeech = false;
            boolean spoke = false;
            float pauseS = 1.6f;   // 停頓多久才算說完（設定 → 語音可調）；以前固定 1 秒，講話稍微停一下就被切掉
            try { pauseS = App.ctx.getSharedPreferences("cfg", android.content.Context.MODE_PRIVATE).getFloat("voice_pause_s", 1.6f); } catch (Throwable ignored) { }
            final int endChunks = Math.max(6, Math.min(40, Math.round(pauseS * 10)));   // 每包 0.1 秒
            while (!cancel) {
                int n = ar.read(buf, 0, buf.length);
                if (n < 0) throw new Exception(L.t("讀麥克風失敗（") + n + L.t("）"));
                long sq = 0;
                for (int i = 0; i < n; i++) sq += (long) buf[i] * buf[i];
                double rms = Math.sqrt((double) sq / Math.max(1, n));
                final float lv = (float) Math.min(1.0, rms / 4000.0);
                UI.post(() -> cb.onLevel(lv));
                chunks.add(Arrays.copyOf(buf, n));
                total += n;
                segSamples += n;
                if (spoke && chunks.size() % 8 == 0) maybePartial(chunks, segStart, segSamples);   // 約每 0.8 秒邊說邊辨識一次（只辨識目前這一段，不會越來越慢）
                if (calib < 5) { floor += rms / 5; calib++; continue; }   // 前 0.5 秒量環境噪音
                double thr = Math.max(500, floor * 3);
                if (rms > thr) { spoke = true; segSpeech = true; silent = 0; } else if (spoke) silent++;
                if (ctx != null && spoke && !stop) {   // 這一段已經夠長、又剛好停頓一下：先辨識掉，繼續錄下一段；一路沒停頓就最多 25 秒硬切
                    boolean atPause = silent >= 3;
                    if ((segSamples >= RATE * 15 && atPause) || segSamples >= RATE * 25) {
                        commitSegment(chunks, segStart, segSamples);
                        segStart = chunks.size();
                        segSamples = 0;
                        segSpeech = false;
                    }
                }
                if (spoke && (silent >= endChunks || stop)) break;         // 說過話、停頓超過設定的秒數（或被叫停）
                if (!spoke && (stop || total > RATE * 8)) break;          // 還沒開口：被叫停或 8 秒
                if (total > RATE * 180) break;                             // 安全上限 3 分鐘（旁邊一直有聲音時不要錄到天荒地老）
            }
            finished = true;   // 之後不再送邊說邊辨識的結果，免得蓋掉最後的完整辨識
            lastHitCap = !cancel && !stop && spoke && total > RATE * 180;
            // 等前面分段的辨識都做完，再把整段文字交出去
            synchronized (commitFutures) {
                for (java.util.concurrent.Future<?> f : commitFutures) { try { f.get(40, java.util.concurrent.TimeUnit.SECONDS); } catch (Throwable ignored) { } }
            }
            lastCommitted = committedNow();
            lastAll = join(chunks, 0, Math.min(total, RATE * 20));
            lastSegSpeech = segSpeech || (segStart == 0 && spoke);
            try {   // 診斷：為什麼停止錄音（語音被提早切掉、或自己送出時，看「最近事件」）
                String why = cancel ? "被取消" : stop ? "手動停止" : !spoke ? "沒開口" : total > RATE * 180 ? "到了 3 分鐘上限" : "停頓超過 " + String.format(java.util.Locale.US, "%.1f", endChunks / 10f) + " 秒";
                LifeLog.add(App.ctx, "錄音結束：" + why + "，共 " + String.format(java.util.Locale.US, "%.1f", total / (float) RATE) + " 秒");
            } catch (Throwable ignored) { }
            if (cancel) return;
            if (!spoke) { UI.post(() -> cb.onDone(null)); return; }
            final short[] out = join(chunks, segStart, segSamples);   // 只有最後一段；前面的已經辨識好、放在 lastCommitted（用 recognizeFinal 接起來）
            UI.post(() -> cb.onDone(out));
        } catch (Throwable e) {
            Log.w(TAG, "speech capture: " + e);
            final String msg = String.valueOf(e.getMessage() != null ? e.getMessage() : e);
            if (!cancel) UI.post(() -> cb.onError(msg));
        } finally {
            if (ar != null) {
                try { ar.stop(); } catch (Exception ignored) { }
                ar.release();
            }
        }
    }
}
