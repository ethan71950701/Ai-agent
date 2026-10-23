package com.phoneai.overlay;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 任務過程（mode 0：任務歷史，可加星星、有星的排前面）與自我檢查結果（mode 4：selftest.log）。
 * 任務歷史進來就讀一次；自我檢查還在跑（沒看到結束標記）時每 2 秒自動刷新。
 */
public class LogActivity extends Activity {
    private static final String SELFTEST = "/data/data/com.phoneai.overlay/files/selftest.log";
    private static final int MAX_CHARS = 60000;   // 太長的紀錄只留最後一段，免得畫面卡
    private int mode;
    private boolean visible;
    private final long startedAt = System.currentTimeMillis();
    private TextView body, status;
    private LinearLayout taskBox;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        mode = getIntent().getIntExtra("mode", 0);
        LinearLayout[] c = new LinearLayout[1];
        setContentView(Ui.screen(this, mode == 4 ? L.t("自我檢查") : L.t("任務過程"), true, c));
        Ui.enterSpring(this);
        LinearLayout box = c[0];
        status = Ui.infoCard(this, box, 13, false, true);
        if (mode == 4) {
            body = Ui.infoCard(this, box, 12, true, true);   // 長按可選取、複製（貼給人看用）
        } else {
            taskBox = new LinearLayout(this);
            taskBox.setOrientation(LinearLayout.VERTICAL);
            box.addView(taskBox);
        }
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
        if (mode == 4) reload();
        else reloadTasks();
    }

    private void reloadTasks() {
        new Thread(() -> {
            JSONArray list = TaskLog.list(this);
            runOnUiThread(() -> showTasks(list));
        }).start();
    }

    private void showTasks(JSONArray list) {
        taskBox.removeAllViews();
        if (list.length() == 0) {
            status.setText(L.t("還沒有任務紀錄。在首頁或問問看送出一句話後，會出現在這裡。"));
            return;
        }
        status.setText(L.t("點 ☆ 可以加星星，有星的會排在最前面。"));
        SimpleDateFormat f = new SimpleDateFormat("MM/dd HH:mm", Locale.TAIWAN);
        for (int k = 0; k < list.length(); k++) {
            try {
                JSONObject o = list.getJSONObject(k);
                final long id = o.getLong("id");
                final boolean starred = o.optBoolean("star");
                LinearLayout card = Ui.card(this, taskBox);

                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);

                LinearLayout col = new LinearLayout(this);
                col.setOrientation(LinearLayout.VERTICAL);
                TextView time = Ui.body(this, f.format(new Date(id)));
                TextView text = new TextView(this);
                text.setText(o.getString("text"));
                text.setTextSize(15);
                text.setTextColor(Ui.attr(this, com.google.android.material.R.attr.colorOnSurface));
                text.setTextIsSelectable(true);
                String reply = o.optString("reply", "");
                col.addView(time);
                col.addView(text);
                if (!reply.isEmpty()) col.addView(Ui.body(this, reply));
                JSONArray steps = o.optJSONArray("steps");   // 自動操作每一步做了什麼（含 AI 的想法）：點一下展開
                if (steps != null && steps.length() > 0) {
                    StringBuilder sb = new StringBuilder();
                    for (int s = 0; s < steps.length(); s++) sb.append(steps.optString(s)).append("\n\n");
                    final TextView stepsTv = Ui.body(this, sb.toString().trim());
                    stepsTv.setTypeface(Typeface.MONOSPACE);
                    stepsTv.setTextSize(12);
                    stepsTv.setVisibility(View.GONE);
                    final TextView hint = Ui.body(this, L.t("▸ 點一下看 AI 每一步做了什麼"));
                    hint.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
                    hint.setTextColor(Ui.attr(this, com.google.android.material.R.attr.colorPrimary));
                    hint.setOnClickListener(v -> {
                        boolean show = stepsTv.getVisibility() != View.VISIBLE;
                        stepsTv.setVisibility(show ? View.VISIBLE : View.GONE);
                        hint.setText(show ? L.t("▾ 收起") : L.t("▸ 點一下看 AI 每一步做了什麼"));
                    });
                    col.addView(hint);
                    col.addView(stepsTv);
                }
                row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));

                final StarView star = new StarView(this, Ui.attr(this, com.google.android.material.R.attr.colorPrimary),
                        Ui.attr(this, com.google.android.material.R.attr.colorOnSurfaceVariant));
                star.setOn(starred, false);
                star.setOnClickListener(v -> {
                    star.setOn(!starred, true);   // 先彈一下，再存檔重排
                    star.postDelayed(() -> new Thread(() -> {
                        TaskLog.setStar(this, id, !starred);
                        runOnUiThread(this::reloadTasks);
                    }).start(), 320);
                });
                row.addView(star);

                card.addView(row);
            } catch (Exception ignored) {
                // 壞掉的一筆跳過，其他照常顯示
            }
        }
    }

    private void reload() {
        new Thread(() -> {
            String text = null, diag = "";
            for (int k = 0; k < 3 && text == null; k++) {
                try {
                    Process p = new ProcessBuilder(Root.su(), "-c", "tail -c " + MAX_CHARS + " " + SELFTEST).redirectErrorStream(true).start();
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
                    status.setText(L.t("自我檢查還沒有結果。"));
                    body.setText("");
                } else {
                    status.setText(finished ? L.t("自我檢查完成（✓ 正常，✕ 有問題）") : L.t("自我檢查進行中…"));
                    body.setText(t);
                }
                // 自我檢查還沒結束就每 2 秒刷新，最多 3 分鐘
                if (visible && !finished && System.currentTimeMillis() - startedAt < 180_000L)
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
