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
        void onLevel(float v);      // 0~1，給光球用
        void onDone(short[] pcm);   // 錄到的聲音；沒聽到聲音或中途取消 = null
        void onError(String msg);
    }

    static final int RATE = 16000;
    private static final String TAG = OverlayService.TAG;
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private volatile boolean stop, cancel;

    private SpeechCapture() { }

    static SpeechCapture start(Cb cb) {
        SpeechCapture c = new SpeechCapture();
        new Thread(() -> c.run(cb), "speech-capture").start();
        return c;
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
            if (ar.getState() != AudioRecord.STATE_INITIALIZED) throw new Exception("麥克風開不起來");
            ar.startRecording();
            UI.post(cb::onListening);
            ArrayList<short[]> chunks = new ArrayList<>();
            short[] buf = new short[1600];   // 0.1 秒一包
            double floor = 0;
            int calib = 0, silent = 0, total = 0;
            boolean spoke = false;
            while (!cancel) {
                int n = ar.read(buf, 0, buf.length);
                if (n < 0) throw new Exception("讀麥克風失敗（" + n + "）");
                long sq = 0;
                for (int i = 0; i < n; i++) sq += (long) buf[i] * buf[i];
                double rms = Math.sqrt((double) sq / Math.max(1, n));
                final float lv = (float) Math.min(1.0, rms / 4000.0);
                UI.post(() -> cb.onLevel(lv));
                chunks.add(Arrays.copyOf(buf, n));
                total += n;
                if (calib < 5) { floor += rms / 5; calib++; continue; }   // 前 0.5 秒量環境噪音
                double thr = Math.max(500, floor * 3);
                if (rms > thr) { spoke = true; silent = 0; } else if (spoke) silent++;
                if (spoke && (silent >= 10 || stop)) break;                // 說過話、停頓約 1 秒（或被叫停）
                if (!spoke && (stop || total > RATE * 8)) break;          // 還沒開口：被叫停或 8 秒
                if (total > RATE * 20) break;                              // 最長 20 秒
            }
            if (cancel) return;
            if (!spoke) { UI.post(() -> cb.onDone(null)); return; }
            short[] pcm = new short[total];
            int p = 0;
            for (short[] c : chunks) { System.arraycopy(c, 0, pcm, p, c.length); p += c.length; }
            final short[] out = pcm;
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
