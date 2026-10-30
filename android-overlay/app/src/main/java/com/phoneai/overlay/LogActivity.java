package com.phoneai.overlay;

import android.app.Activity;
import android.content.Intent;
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
            box.addView(Ui.button(this, L.t("匯出紀錄（分享成文字檔）"), v ->
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle(L.t("匯出紀錄"))
                            .setMessage(L.t("會把最近 20 筆任務（含每一步的思考、動作、結果、token）和診斷資訊（版本、模式、AI 服務的網址與模型）存成文字檔，讓你用分享選單傳出去。不含 API Key；但任務文字和畫面上的內容可能有個人資料，分享前請自己看過。"))
                            .setPositiveButton(L.t("分享"), (d, w) -> LogExport.share(this))
                            .setNeutralButton(L.t("存到下載"), (d, w) -> LogExport.saveToDownloads(this))
                            .setNegativeButton(L.t("取消"), null).show()));
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

    private android.graphics.drawable.Drawable obtainRipple() {
        android.util.TypedValue tv = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        return getResources().getDrawable(tv.resourceId, getTheme());
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
                long tokens = o.optLong("tokens", 0);
                int steps = o.optJSONArray("detail") == null ? 0 : o.optJSONArray("detail").length();
                String sum = (steps > 0 ? L.t("共 ") + steps + L.t(" 步 · ") : "") + L.t("用了 ") + String.format(Locale.US, "%,d", tokens) + " token";
                double estCost = o.optDouble("cost", 0);
                if (estCost > 0) sum += String.format(Locale.US, L.t(" · 估價約 $%.4f"), estCost);
                TextView info = Ui.body(this, sum + "　›");
                info.setTextColor(Ui.attr(this, com.google.android.material.R.attr.colorPrimary));
                info.setPadding(0, Ui.dp(this, 6), 0, 0);
                col.addView(info);
                card.setOnClickListener(v -> {   // 點整張卡片進入任務詳細頁（每一步的思考與 token）
                    Intent it = new Intent(this, TaskDetailActivity.class).putExtra("id", id);
                    startActivity(it);
                });
                card.setClickable(true);
                card.setForeground(obtainRipple());
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
                final View bounce = card;
                bounce.postDelayed(() -> Ui.bounceIn(bounce), 40L * k);   // 一張一張彈出來
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
