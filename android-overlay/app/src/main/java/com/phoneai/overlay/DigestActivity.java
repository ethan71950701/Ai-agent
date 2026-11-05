package com.phoneai.overlay;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

/** 通知整理的結果：摘要，以及可以加入行事曆或提醒的事（每一項按「加入」才會加入）。 */
public class DigestActivity extends Activity {
    private LinearLayout box;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout[] c = new LinearLayout[1];
        setContentView(Ui.screen(this, L.t("通知整理"), true, c));
        Ui.enterSpring(this);
        box = c[0];
        render();
    }

    private void render() {
        box.removeAllViews();
        JSONObject o = NotifDigest.load(this);
        if (o == null) {
            Ui.infoCard(this, box, 14, false, true).setText(L.t("還沒有整理過。到「功能 → 通知整理」設定時間，或按「立即整理」。"));
            return;
        }
        String when = new java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.TAIWAN).format(new java.util.Date(o.optLong("time")));
        Ui.section(this, box, L.t("摘要") + "（" + when + "）");
        Ui.infoCard(this, box, 15, false, true).setText(o.optString("summary"));
        JSONArray items = o.optJSONArray("items");
        Ui.section(this, box, L.t("可以加入行事曆或提醒的事"));
        if (items == null || items.length() == 0) {
            Ui.infoCard(this, box, 14, false, false).setText(L.t("這次沒有找到有日期或時間的事。"));
            return;
        }
        for (int i = 0; i < items.length(); i++) {
            final JSONObject it = items.optJSONObject(i);
            if (it == null) continue;
            LinearLayout card = Ui.card(this, box);
            TextView t = Ui.body(this, it.optString("title"));
            t.setTextSize(16);
            card.addView(t);
            String sub = ("calendar".equals(it.optString("type")) ? L.t("行事曆") : L.t("提醒"))
                    + (it.optString("date").isEmpty() ? "" : " · " + it.optString("date")) + (it.optString("time").isEmpty() ? "" : " " + it.optString("time"))
                    + (it.optString("from").isEmpty() ? "" : " · " + L.t("來自 ") + it.optString("from"));
            card.addView(Ui.body(this, sub));
            final boolean added = it.optBoolean("added");
            final MaterialButton btn = Ui.button(this, added ? L.t("已加入") : ("calendar".equals(it.optString("type")) ? L.t("加入行事曆") : L.t("加入提醒")), null);
            btn.setEnabled(!added);
            btn.setOnClickListener(v -> {
                btn.setEnabled(false);
                new Thread(() -> {
                    String r = NotifDigest.add(this, it);
                    boolean ok = !r.startsWith("錯誤") && !r.contains("失敗");
                    if (ok) {
                        try { it.put("added", true); } catch (Exception ignored) { }
                        NotifDigest.save(this, o);
                    }
                    runOnUiThread(() -> {
                        android.widget.Toast.makeText(this, r, android.widget.Toast.LENGTH_LONG).show();
                        btn.setText(ok ? L.t("已加入") : L.t("再試一次"));
                        btn.setEnabled(!ok);
                    });
                }).start();
            });
            card.addView(btn);
        }
    }
}
