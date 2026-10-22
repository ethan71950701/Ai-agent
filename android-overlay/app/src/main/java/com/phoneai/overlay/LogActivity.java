package com.phoneai.overlay;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * 任務過程（mode 0：Termux 的 assistant.log）與自我檢查結果（mode 4：selftest.log）。
 * 沒有按鈕：進來就讀一次；自我檢查還在跑（沒看到結束標記）時每 2 秒自動刷新。
 */
public class LogActivity extends Activity {
    private static final String FILE = "/data/data/com.phoneai.overlay/files/assistant.log";   // 任務過程紀錄（App 內的自動操作迴圈還沒寫入，目前會顯示「沒有紀錄」）
    private static final String SELFTEST = "/data/data/com.phoneai.overlay/files/selftest.log";
    private static final int MAX_CHARS = 60000;   // 太長的紀錄只留最後一段，免得畫面卡
    private int mode;
    private boolean visible;
    private final long startedAt = System.currentTimeMillis();
    private TextView body, status;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        mode = getIntent().getIntExtra("mode", 0);
        LinearLayout[] c = new LinearLayout[1];
        setContentView(Ui.screen(this, mode == 4 ? L.t("自我檢查") : L.t("任務過程"), true, c));
        Ui.enterSpring(this);
        LinearLayout box = c[0];
        status = Ui.infoCard(this, box, 13, false, true);
        body = Ui.infoCard(this, box, 12, true, true);   // 長按可選取、複製（貼給人看用）
    }

    @Override
    protected void onPause() {
        visible = false;
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        reload();
    }

    private void reload() {
        new Thread(() -> {
            String text = null, diag = "";
            for (int k = 0; k < 3 && text == null; k++) {
                try {
                    Process p = new ProcessBuilder(Root.su(), "-c", "tail -c " + MAX_CHARS + " " + (mode == 4 ? SELFTEST : FILE)).redirectErrorStream(true).start();
                    String out = readAll(p.getInputStream());
                    if (p.waitFor() == 0) text = out;
                    else diag = out.trim();
                } catch (Exception e) {
                    diag = String.valueOf(e.getMessage());
                }
            }
            final String t = text, dg = diag;
            runOnUiThread(() -> {
                boolean finished = t != null && t.contains("=== 結束 ===");
                if (t == null) {
                    status.setText(L.t("讀不到紀錄（") + dg + L.t("）。可能還沒從面板問過問題，或這支手機沒有 root。"));
                    body.setText("");
                } else if (t.trim().isEmpty()) {
                    status.setText(mode == 4 ? L.t("自我檢查還沒有結果。") : L.t("還沒有從面板送出過任務。"));
                    body.setText("");
                } else {
                    status.setText(mode == 4 ? (finished ? L.t("自我檢查完成（✓ 正常，✕ 有問題）") : L.t("自我檢查進行中…"))
                            : L.t("上一次任務的完整輸出"));
                    body.setText(t);
                }
                // 自我檢查還沒結束就每 2 秒刷新，最多 3 分鐘
                if (mode == 4 && visible && !finished && System.currentTimeMillis() - startedAt < 180_000L)
                    body.postDelayed(this::reload, 2000);
            });
        }).start();
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
        return o.toString("UTF-8");
    }
}
