package com.phoneai.overlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import com.k2fsa.sherpa.onnx.OnlineStream;
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor;
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.List;

/**
 * 聲紋（實驗，只量不攔）：用 sherpa-onnx 的說話人辨識模型（3D-Speaker CAM++，中文，約 28MB）把一段聲音轉成一串數字（聲紋向量），
 * 跟你登錄的聲紋比餘弦相似度（-1～1，越大越像）。目前只在診斷頁顯示每一句話跟你的相似度，不影響任何功能，用來看實際分數、決定門檻。
 * 模型放在 filesDir/sherpa/speaker（按按鈕才下載，不進 repo）；聲紋向量存在設定檔，不離開手機。
 */
final class SpeakerId {
    /** 可選的聲紋模型：不同模型的分數尺度不一樣，所以各自有自己的資料夾與聲紋（換模型要重新登錄、重新量門檻）。 */
    static final class Model {
        final String id, label, file, url, sha, dirName, keySuffix;
        final long size;
        Model(String id, String label, String file, String url, long size, String sha, String dirName, String keySuffix) {
            this.id = id; this.label = label; this.file = file; this.url = url; this.size = size; this.sha = sha; this.dirName = dirName; this.keySuffix = keySuffix;
        }
    }
    private static final String BASE = "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/";
    static final Model[] MODELS = {
            // 第一個模型沿用舊的資料夾與設定鍵（dirName 為 "speaker"、keySuffix 為空），已經登錄的聲紋不會不見
            new Model("campplus", "CAM++（28 MB，小而快）", "campplus.onnx", BASE + "3dspeaker_speech_campplus_sv_zh-cn_16k-common.onnx",
                    28281138L, "f682b514c05d947ee3fa91cd6ec6c5c7543479a128373fa29b1faedccd21fd11", "speaker", ""),
            new Model("eres2net_large", "ERes2Net large（116 MB，較準較重）", "eres2net_large.onnx", BASE + "3dspeaker_speech_eres2net_large_sv_zh-cn_3dspeaker_16k.onnx",
                    116058710L, "19547e85b6c14ec44b8add4e7cb9ce353c7e995d4f1c9ffd408176ac3a2d6895", "speaker_eres2net_large", "_eres2net_large"),
    };

    static Model model(Context c) {
        String id = c.getSharedPreferences("cfg", Context.MODE_PRIVATE).getString("spk_model", MODELS[0].id);
        for (Model m : MODELS) if (m.id.equals(id)) return m;
        return MODELS[0];
    }

    static void selectModel(Context c, String id) {
        c.getSharedPreferences("cfg", Context.MODE_PRIVATE).edit().putString("spk_model", id).apply();
        release();   // 換模型：舊的抽取器釋放掉
        lastInfo = "";
    }

    private static String key(Context c) { return "spk_emb" + model(c).keySuffix; }
    private static String keyN(Context c) { return "spk_n" + model(c).keySuffix; }
    private static final Object LOCK = new Object();
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static final long IDLE_MS = 2 * 60 * 1000L;
    private static SpeakerEmbeddingExtractor ex;
    private static final Runnable IDLE_RELEASE = () -> new Thread(SpeakerId::release, "speaker-idle").start();

    /** 最近一句話的比對結果（診斷頁用）。 */
    static volatile String lastInfo = "";

    private SpeakerId() { }

    static File dir(Context c) { return new File(new File(c.getFilesDir(), "sherpa"), model(c).dirName); }

    static boolean ready(Context c) { return new File(dir(c), ".ok").exists(); }

    static boolean enrolled(Context c) { return c.getSharedPreferences("cfg", Context.MODE_PRIVATE).getString(key(c), "").length() > 0; }

    /** 下載並核對模型（可能在背景執行緒；進度文字交給 say）。 */
    static void download(Context c, java.util.function.Consumer<String> say) throws Exception {
        final Model m = model(c);
        File d = dir(c);
        SherpaEngine.deleteRec(d);
        d.mkdirs();
        File tmp = new File(d, m.file + ".part"), dest = new File(d, m.file);
        try {
            ParallelDownloader.download(m.url, tmp, 4, (got, total, bps, conns) ->
                    say.accept(String.format(java.util.Locale.US, L.t("下載聲紋模型：%d%%（%d／%d MB，%.1f MB/s）"),
                            total > 0 ? got * 100 / total : 0, got >> 20, total >> 20, bps / 1048576.0)));
            if (tmp.length() != m.size) throw new Exception(L.t("聲紋模型大小不對（") + tmp.length() + L.t("）"));
            say.accept(L.t("核對校驗碼…"));
            if (!sha256(tmp).equalsIgnoreCase(m.sha)) throw new Exception(L.t("聲紋模型校驗碼不符（不是預期的檔案，已丟棄）"));
            if (!tmp.renameTo(dest)) throw new Exception(L.t("搬移檔案失敗"));
        } catch (Exception e) {
            tmp.delete();
            throw e;
        }
        new File(d, ".ok").createNewFile();
    }

    private static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte x : md.digest()) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    static void release() {
        synchronized (LOCK) {
            UI.removeCallbacks(IDLE_RELEASE);
            if (ex != null) { ex.release(); ex = null; }
        }
    }

    /** 16kHz 單聲道 -1～1 的聲音 → 聲紋向量（太短或失敗回 null）。 */
    static float[] embed(Context c, float[] samples) {
        synchronized (LOCK) {
            try {
                if (ex == null) {
                    ex = new SpeakerEmbeddingExtractor(SpeakerEmbeddingExtractorConfig.builder()
                            .setModel(new File(dir(c), model(c).file).getAbsolutePath()).setNumThreads(2).setDebug(false).build());
                }
                OnlineStream s = ex.createStream();
                try {
                    s.acceptWaveform(samples, SpeechCapture.RATE);
                    s.inputFinished();
                    if (!ex.isReady(s)) return null;
                    return ex.compute(s);
                } finally {
                    s.release();
                }
            } catch (Throwable t) {
                CrashLog.w("聲紋", t);
                return null;
            } finally {
                UI.removeCallbacks(IDLE_RELEASE);
                UI.postDelayed(IDLE_RELEASE, IDLE_MS);
            }
        }
    }

    private static float[] normalize(float[] v) {
        double n = 0;
        for (float x : v) n += x * x;
        n = Math.sqrt(n);
        float[] o = new float[v.length];
        if (n > 0) for (int i = 0; i < v.length; i++) o[i] = (float) (v[i] / n);
        return o;
    }

    private static String encode(float[] v) {
        ByteBuffer b = ByteBuffer.allocate(v.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float x : v) b.putFloat(x);
        return Base64.encodeToString(b.array(), Base64.NO_WRAP);
    }

    private static float[] decode(String s) {
        byte[] raw = Base64.decode(s, Base64.NO_WRAP);
        ByteBuffer b = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        float[] v = new float[raw.length / 4];
        for (int i = 0; i < v.length; i++) v[i] = b.getFloat();
        return v;
    }

    /** 登錄：每一段聲音算一個聲紋，各自正規化後取平均再正規化，存起來。回傳實際用到幾段。 */
    static int enroll(Context c, List<float[]> clips) {
        float[] sum = null;
        int n = 0;
        for (float[] clip : clips) {
            float[] e = embed(c, clip);
            if (e == null) continue;
            e = normalize(e);
            if (sum == null) sum = new float[e.length];
            for (int i = 0; i < e.length; i++) sum[i] += e[i];
            n++;
        }
        if (sum == null) return 0;
        c.getSharedPreferences("cfg", Context.MODE_PRIVATE).edit().putString(key(c), encode(normalize(sum))).putInt(keyN(c), n).apply();
        return n;
    }

    static void clear(Context c) {
        c.getSharedPreferences("cfg", Context.MODE_PRIVATE).edit().remove(key(c)).remove(keyN(c)).apply();
        lastInfo = "";
    }

    /** 這段聲音跟登錄聲紋的餘弦相似度；沒登錄、模型沒下載或太短回 NaN。 */
    static double score(Context c, float[] samples) {
        SharedPreferences sp = c.getSharedPreferences("cfg", Context.MODE_PRIVATE);
        String enc = sp.getString(key(c), "");
        if (enc.isEmpty() || !ready(c) || samples.length < SpeechCapture.RATE * 3 / 2) return Double.NaN;
        float[] me = decode(enc);
        float[] e = embed(c, samples);
        if (e == null || e.length != me.length) return Double.NaN;
        e = normalize(e);
        double dot = 0;
        for (int i = 0; i < e.length; i++) dot += e[i] * me[i];
        return dot;
    }

    /** 最近一句話的相似度（NaN＝沒比對：沒登錄、模型沒下載、太短）。 */
    static volatile double lastScore = Double.NaN;

    /** 現在就算（呼叫端要在背景執行緒），結果寫進 lastScore、lastInfo 與「最近事件」。 */
    static void scoreNow(final Context c, final float[] samples) {
        lastScore = Double.NaN;
        if (!enrolled(c) || !ready(c)) return;
        double s = score(c, samples);
        lastScore = s;
        String secs = String.format(java.util.Locale.US, "%.1f", samples.length / (float) SpeechCapture.RATE);
        lastInfo = Double.isNaN(s) ? L.t("上一句太短（") + secs + L.t(" 秒），沒有比對")
                : String.format(java.util.Locale.US, L.t("上一句跟你的聲紋相似度 %.2f（%s 秒）"), s, secs);
        LifeLog.add(c, "聲紋（" + model(c).id + "）：" + (Double.isNaN(s) ? "太短" : String.format(java.util.Locale.US, "%.2f", s)) + "（" + secs + " 秒）");
    }

    /** 在背景算相似度（只記錄，診斷用）。 */
    static void scoreAsync(final Context c, final float[] samples) {
        if (!enrolled(c) || !ready(c)) return;
        new Thread(() -> scoreNow(c, samples), "speaker-score").start();
    }

    /** 「只回應我的聲音」開著、已登錄、而且最近一句有比對到、分數低於門檻：不要自動送出。比不出來（太短）就放行。 */
    static boolean gateOn(Context c) { return c.getSharedPreferences("cfg", Context.MODE_PRIVATE).getBoolean("spk_gate", false); }

    static float threshold(Context c) { return c.getSharedPreferences("cfg", Context.MODE_PRIVATE).getFloat("spk_thr", 0.5f); }

    static boolean rejects(Context c) {
        double s = lastScore;
        return gateOn(c) && enrolled(c) && ready(c) && !Double.isNaN(s) && s < threshold(c);
    }

    static String statusText(Context c) {
        String model = ready(c) ? L.t("模型已下載") : L.t("模型還沒下載");
        String me = enrolled(c) ? L.t("已登錄（") + c.getSharedPreferences("cfg", Context.MODE_PRIVATE).getInt(keyN(c), 0) + L.t(" 段）") : L.t("還沒登錄");
        return L.t("模型：") + L.t(model(c).label) + "\n　" + model + "、" + me + (lastInfo.isEmpty() ? "" : "\n　" + lastInfo);
    }
}
