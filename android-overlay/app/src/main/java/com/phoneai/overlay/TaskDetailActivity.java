package com.phoneai.overlay;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 單一任務的詳細頁：總共用了多少 token，以及每一步的思考、動作、結果與用量。卡片一張一張彈出來。 */
public class TaskDetailActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout[] c = new LinearLayout[1];
        setContentView(Ui.screen(this, L.t("任務過程"), true, c));
        Ui.enterSpring(this);
        LinearLayout box = c[0];
        JSONObject t = TaskLog.get(this, getIntent().getLongExtra("id", 0));
        if (t == null) {
            box.addView(Ui.body(this, L.t("找不到這筆任務。")));
            return;
        }
        long tokens = t.optLong("tokens", 0);
        JSONArray detail = t.optJSONArray("detail");
        int steps = detail == null ? 0 : detail.length();

        LinearLayout head = Ui.card(this, box);
        head.addView(Ui.body(this, new SimpleDateFormat("MM/dd HH:mm", Locale.TAIWAN).format(new Date(t.optLong("id")))));
        TextView q = Ui.body(this, t.optString("text"));
        q.setTextSize(16);
        head.addView(q);
        if (!t.optString("reply").isEmpty()) head.addView(Ui.body(this, t.optString("reply")));

        LinearLayout total = Ui.card(this, box);
        total.addView(Ui.body(this, L.t("共用了 ") + String.format(Locale.US, "%,d", tokens) + " token"
                + (steps > 0 ? L.t("（") + steps + L.t(" 步）") : "")));
        double estCost = t.optDouble("cost", 0);
        if (estCost > 0) total.addView(Ui.body(this, String.format(Locale.US, L.t("估價約 $%.4f（第三方價格，以 Console 為準）"), estCost)));

        if (steps == 0) {
            total.addView(Ui.body(this, L.t("這次沒有自動操作，只是一次對話。")));
            return;
        }
        for (int i = 0; i < steps; i++) {
            JSONObject d = detail.optJSONObject(i);
            if (d == null) continue;
            LinearLayout card = Ui.card(this, box);
            TextView title = Ui.body(this, L.t("第 ") + d.optInt("step") + L.t(" 步 · ") + d.optString("action"));
            title.setTextSize(15);
            title.setTextColor(Ui.attr(this, com.google.android.material.R.attr.colorPrimary));
            card.addView(title);
            String think = d.optString("think");
            card.addView(Ui.body(this, L.t("思考：") + (think.isEmpty() ? L.t("（沒有寫）") : think)));
            String result = d.optString("result");
            if (!result.isEmpty()) card.addView(Ui.body(this, L.t("結果：") + result));
            card.addView(Ui.body(this, L.t("這一步用了 ") + String.format(Locale.US, "%,d", d.optLong("tokens")) + " token"));
            if (!d.optString("model").isEmpty()) {   // 這一步用的模型與估價（舊紀錄沒有這些欄位就不顯示）
                card.addView(Ui.body(this, String.format(Locale.US, L.t("%s · 輸入 %,d、輸出 %,d · 估價約 $%.4f"),
                        d.optString("model"), d.optLong("in"), d.optLong("out"), d.optDouble("cost"))));
                card.addView(Ui.body(this, String.format(Locale.US, L.t("快取：寫入 %,d、讀取 %,d"), d.optLong("cw"), d.optLong("cr"))));
                if (d.has("retry")) {   // Haiku 的回覆看不懂、這一步多花了一次 Sonnet
                    card.addView(Ui.body(this, String.format(Locale.US, L.t("Haiku 的回覆看不懂，這一步改用 Sonnet 重問（回覆開頭：%s）"), d.optString("retry"))));
                }
                if (d.has("tree")) {   // 元件清單模式的診斷
                    int tn = d.optInt("tree");
                    card.addView(Ui.body(this, tn > 0 ? String.format(Locale.US, L.t("元件清單：%d 個元件"), tn)
                            : tn == 0 ? L.t("元件清單：沒有讀到元件，這一步改用截圖")
                            : tn == -3 ? L.t("元件清單：畫面連續沒變，這一步改用截圖＋Sonnet")
                            : L.t("元件清單：上一步點不到編號，這一步改用截圖＋Sonnet")));
                }
            }
            final View v = card;
            v.postDelayed(() -> Ui.bounceIn(v), 60L * i + 120);   // 一張一張彈出來
        }
    }
}
