package com.phoneai.overlay;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig;
import com.k2fsa.sherpa.onnx.OfflineStream;

import java.io.File;

/**
 * 離線語音辨識：SenseVoice（sherpa-onnx，非串流：說完一次辨識）。取代先前的 Vosk。
 * 為什麼：實測 SenseVoice 比三星內建辨識還準，而且會自動加標點、把數字轉成阿拉伯數字；三星也能用，不必依賴系統辨識服務。
 * 語言設成自動偵測（空字串）：雲端實測與指定 zh 結果相同，但能處理中英夾雜。
 * 模型檔放在 filesDir/sherpa/sensevoice（按按鈕才下載，不進 repo）；引擎只載入一次，共用。
 */
final class SherpaEngine {
    static final String MODEL_ID = "sensevoice";
    private static final String LANG = "";   // 自動偵測語言
    private static SherpaEngine cached;
    private static final Object LOCK = new Object();
    /** 閒置多久就從記憶體移除（模型約 230MB，平常不用就該釋放；下次用再載入，約 1～2 秒）。 */
    private static final long IDLE_MS = 2 * 60 * 1000L;
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static final Runnable IDLE_RELEASE = () -> new Thread(SherpaEngine::release, "sensevoice-idle").start();

    private final OfflineRecognizer rec;

    private SherpaEngine(OfflineRecognizer r) { rec = r; }

    static File dir(Context c) { return new File(new File(c.getFilesDir(), "sherpa"), MODEL_ID); }

    /** 模型已經下載並核對完成（有完成標記）。 */
    static boolean ready(Context c) { return new File(dir(c), ".ok").exists(); }

    /** 辨識一段聲音（16kHz 單聲道、-1～1）。沒載入就先載入（約 1～2 秒）；辨識完重新計時，閒置 2 分鐘後自動釋放。模型沒裝會丟例外。 */
    static String recognize(Context c, float[] samples, int sampleRate) {
        synchronized (LOCK) {
            if (cached == null) cached = open(dir(c));
            String t = cached.recognizeNow(samples, sampleRate);
            UI.removeCallbacks(IDLE_RELEASE);
            UI.postDelayed(IDLE_RELEASE, IDLE_MS);
            return t;
        }
    }

    /** 立刻從記憶體移除引擎（閒置釋放、或刪除模型時呼叫）。 */
    static void release() {
        synchronized (LOCK) {
            UI.removeCallbacks(IDLE_RELEASE);
            if (cached != null) { cached.close(); cached = null; }
        }
    }

    /** 載入指定資料夾裡的模型（model.int8.onnx、tokens.txt）。 */
    static SherpaEngine open(File dir) {
        String model = new File(dir, "model.int8.onnx").getAbsolutePath();
        String tokens = new File(dir, "tokens.txt").getAbsolutePath();
        OfflineModelConfig mc = OfflineModelConfig.builder()
                .setSenseVoice(OfflineSenseVoiceModelConfig.builder().setModel(model).setLanguage(LANG).setInverseTextNormalization(true).build())
                .setTokens(tokens).setNumThreads(2).setDebug(false).build();
        OfflineRecognizerConfig cfg = OfflineRecognizerConfig.builder().setOfflineModelConfig(mc).setDecodingMethod("greedy_search").build();
        return new SherpaEngine(new OfflineRecognizer(cfg));
    }

    private String recognizeNow(float[] samples, int sampleRate) {
        OfflineStream s = rec.createStream();
        try {
            s.acceptWaveform(samples, sampleRate);
            rec.decode(s);
            String t = rec.getResult(s).getText();
            return t == null ? "" : t.trim();
        } finally {
            s.release();
        }
    }

    private void close() { rec.release(); }
}
