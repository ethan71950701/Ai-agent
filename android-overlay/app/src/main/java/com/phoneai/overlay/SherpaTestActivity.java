package com.phoneai.overlay;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;

/**
 * SenseVoice 語音辨識：下載模型（Hugging Face，失敗換鏡像；下載完核對 SHA-256 與我們測過的檔案相同）、
 * 以及「錄一句話試試看」。模型安裝後，面板的語音就會自動用它。
 */
public class SherpaTestActivity extends Activity {
    private static final int REQ = 72;

    private com.google.android.material.button.MaterialButton dl, rec;
    private TextView out, status;
    private final StringBuilder history = new StringBuilder();
    private volatile boolean busy;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout[] c = new LinearLayout[1];
        setContentView(Ui.screen(this, "語音辨識", true, c));
        Ui.enterSpring(this);
        LinearLayout box = c[0];
        TextView hint = Ui.infoCard(this, box, 14, false, false);
        hint.setText("SenseVoice 離線辨識。先下載模型（約 228MB，建議連 Wi-Fi，下載中請不要離開這一頁）。裝好後，面板的語音就會用它：說完停頓約 1 秒會自動結束，"
                + "說完才出字。聲音只在這支手機上處理，不上傳。");
        dl = Ui.pill(this, "", true, v -> toggleDownload());
        refreshDl();
        rec = Ui.pill(this, "錄一句試試看", false, v -> startRecord());
        Ui.buttons(this, box, dl, rec);
        status = Ui.infoCard(this, box, 14, false, true);
        out = Ui.infoCard(this, box, 16, false, true);
    }

    private void refreshDl() {
        dl.setText(SherpaEngine.ready(this)
                ? "SenseVoice：已安裝（點一下刪除）"
                : "下載 SenseVoice（約 228 MB）");
    }

    // ---------- 下載 ----------

    private void toggleDownload() {
        if (busy) return;
        if (SherpaEngine.ready(this)) {
            SherpaEngine.release();
            deleteRec(SherpaEngine.dir(this));
            refreshDl();
            status.setText("已刪除模型。");
            return;
        }
        busy = true;
        dl.setEnabled(false);
        new Thread(() -> {
            String err = null;
            try {
                SherpaEngine.download(SherpaTestActivity.this, m -> runOnUiThread(() -> dl.setText(m)));
            } catch (Throwable e) {
                err = String.valueOf(e.getMessage() != null ? e.getMessage() : e);
                deleteRec(SherpaEngine.dir(this));
            }
            final String e2 = err;
            runOnUiThread(() -> {
                busy = false;
                dl.setEnabled(true);
                refreshDl();
                status.setText(e2 == null ? "模型安裝完成。" : "下載失敗：" + e2 + "（點一下重試）");
            });
        }, "sensevoice-dl").start();
    }

    private static void deleteRec(File f) { SherpaEngine.deleteRec(f); }

    // ---------- 試錄 ----------

    private void startRecord() {
        if (busy) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ);
            return;
        }
        if (!SherpaEngine.ready(this)) { status.setText("請先下載模型。"); return; }
        busy = true;
        rec.setEnabled(false);
        status.setText("請說話…（說完停頓約 1 秒會自動結束）");
        SpeechCapture.start(new SpeechCapture.Cb() {
            @Override public void onListening() { }
            @Override public void onLevel(float v) { }
            @Override public void onDone(short[] pcm) {
                if (pcm == null) { showResult("沒聽到聲音，請再按一次。"); return; }
                status.setText("辨識中…");
                new Thread(() -> {
                    try {
                        long t0 = System.nanoTime();
                        String text = SherpaEngine.recognize(SherpaTestActivity.this, SpeechCapture.toFloat(pcm), SpeechCapture.RATE);
                        double sec = (System.nanoTime() - t0) / 1e9;
                        showResult(String.format(java.util.Locale.US, "音長 %.1f 秒，辨識 %.2f 秒：\n%s",
                                pcm.length / (double) SpeechCapture.RATE, sec, text.isEmpty() ? "（沒有結果）" : text));
                    } catch (Throwable e) {
                        showResult("辨識失敗：" + e);
                    }
                }, "sensevoice-test").start();
            }
            @Override public void onError(String msg) { showResult("錄音失敗：" + msg); }
        });
    }

    private void showResult(String result) {
        runOnUiThread(() -> {
            if (result.contains("\n") || result.startsWith("音長")) {
                history.insert(0, result + "\n\n");
                out.setText(history.toString());
                status.setText("完成。可以再錄一句；結果可以長按選取、複製。");
            } else {
                status.setText(result);
            }
            busy = false;
            rec.setEnabled(true);
        });
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] p, int[] g) {
        if (req == REQ && g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) startRecord();
        else status.setText("沒有麥克風權限。");
    }
}
