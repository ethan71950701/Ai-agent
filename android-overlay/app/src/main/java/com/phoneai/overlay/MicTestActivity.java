package com.phoneai.overlay;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 麥克風實驗（小米語音辨識用）：確認「我們自己的 App 直接錄音」在這支手機上行不行。
 * 背景：小米 13 上，系統辨識服務（Google／小米）都因麥克風 appops 鎖在 foreground 而失敗；但我們的面板在前景，
 * 自己用 AudioRecord 錄音不一定會被擋。如果能錄到聲音，就可以改用 App 內的離線辨識（例如 Vosk）而不需要系統辨識服務。
 * 這頁只錄 3 秒、顯示收到多少資料、聲音最大值（峰值）與平均音量。Android 在權限被擋時常常不報錯，而是給一整段 0，
 * 所以「峰值是 0」= 被靜音擋住；有數值（說話時通常 > 1000）= 可以錄。不會儲存、不會上傳任何音訊。
 */
public class MicTestActivity extends Activity {
    private TextView out;
    private com.google.android.material.button.MaterialButton go;
    private static final int REQ = 71;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout[] c = new LinearLayout[1];
        setContentView(Ui.screen(this, "麥克風實驗", true, c));
        LinearLayout box = c[0];
        TextView hint = Ui.infoCard(this, box, 14, false, false);
        hint.setText("按下「錄 3 秒」後，馬上對著手機說話（例如連續說「一二三四五」）。只在這支手機上算音量，不儲存、不上傳。");
        go = Ui.pill(this, "錄 3 秒", true, v -> start());
        Ui.buttons(this, box, go);
        out = Ui.infoCard(this, box, 14, false, true);
    }

    private void start() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            out.setText("還沒有麥克風權限，請在跳出的視窗按允許。");
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ);
            return;
        }
        go.setEnabled(false);
        out.setText("錄音中…請說話（3 秒）");
        new Thread(this::record, "mic-test").start();
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] p, int[] g) {
        if (req == REQ && g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) start();
        else out.setText("沒有取得麥克風權限（結果：" + (g.length > 0 ? g[0] : "無") + "）");
    }

    private void record() {
        StringBuilder r = new StringBuilder();
        AudioRecord ar = null;
        try {
            final int rate = 16000;
            int min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            r.append("最小緩衝：").append(min).append("\n");
            ar = new AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min, 8192));
            r.append("AudioRecord 狀態：").append(ar.getState() == AudioRecord.STATE_INITIALIZED ? "已初始化" : "初始化失敗(" + ar.getState() + ")").append("\n");
            if (ar.getState() != AudioRecord.STATE_INITIALIZED) {
                done(r.append("→ 這支手機不讓我們開錄音。").toString());
                return;
            }
            ar.startRecording();
            r.append("錄音狀態：").append(ar.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING ? "錄音中" : "沒有在錄(" + ar.getRecordingState() + ")").append("\n");
            short[] buf = new short[4096];
            long end = System.currentTimeMillis() + 3000, total = 0, sumSq = 0;
            int peak = 0, errs = 0;
            while (System.currentTimeMillis() < end) {
                int n = ar.read(buf, 0, buf.length);
                if (n < 0) { errs++; r.append("read 錯誤碼 ").append(n).append("\n"); break; }
                for (int i = 0; i < n; i++) {
                    int v = Math.abs(buf[i]);
                    if (v > peak) peak = v;
                    sumSq += (long) buf[i] * buf[i];
                }
                total += n;
            }
            double rms = total > 0 ? Math.sqrt((double) sumSq / total) : 0;
            r.append("收到取樣數：").append(total).append("（3 秒約 48000）\n");
            r.append("聲音峰值：").append(peak).append("／32767\n");
            r.append(String.format(java.util.Locale.US, "平均音量(RMS)：%.0f\n", rms));
            if (total == 0 || errs > 0) r.append("→ 讀不到資料：被系統擋住。\n");
            else if (peak == 0) r.append("→ 整段都是 0：很可能被系統靜音（權限被擋但不報錯）。\n");
            else if (peak < 300) r.append("→ 有資料但很小：可能沒說話，或麥克風被降音量。再試一次大聲說。\n");
            else r.append("→ 可以錄到聲音！之後可以考慮用 App 內的離線辨識，不需要系統辨識服務。\n");
        } catch (Throwable e) {
            r.append("出錯：").append(e).append("\n");
        } finally {
            if (ar != null) {
                try { ar.stop(); } catch (Exception ignored) { }
                ar.release();
            }
        }
        done(r.toString());
    }

    private void done(String s) {
        runOnUiThread(() -> { out.setText(s); go.setEnabled(true); });
    }
}
