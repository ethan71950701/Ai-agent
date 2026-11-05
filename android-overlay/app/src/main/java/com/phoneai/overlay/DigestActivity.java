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
    /** 帶這個 extra 開啟 = 一進來就開始整理（顯示轉圈圈，整理完才換成結果）。 */
    static final String EXTRA_RUN = "run";
    private static volatile boolean running;
    private static java.lang.ref.WeakReference<DigestActivity> current = new java.lang.ref.WeakReference<>(null);
    private LinearLayout box;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout[] c = new LinearLayout[1];
        setContentView(Ui.screen(this, L.t("通知整理"), true, c));
        Ui.enterSpring(this);
        box = c[0];
        current = new java.lang.ref.WeakReference<>(this);
        if (running) showLoading();
        else if (getIntent().getBooleanExtra(EXTRA_RUN, false)) startRun();
        else render();
    }

    /** 中間一個轉圈圈與說明：讀通知、等 AI 整理要一段時間，讓人知道它正在做事。 */
    private void showLoading() {
        box.removeAllViews();
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setGravity(android.view.Gravity.CENTER);
        wrap.setPadding(0, Ui.dp(this, 120), 0, 0);
        wrap.addView(new ExpressiveLoader(this));   // 主題色、會旋轉變形的載入動畫
        TextView t = new TextView(this);
        t.setText(L.t("整理通知中，請稍候…"));
        t.setTextSize(15);
        t.setGravity(android.view.Gravity.CENTER);
        t.setPadding(0, Ui.dp(this, 16), 0, 0);
        wrap.addView(t);
        TextView hint = new TextView(this);
        hint.setText(L.t("正在讀取通知，再交給 AI 整理，大約要幾秒到幾十秒"));
        hint.setTextSize(12);
        hint.setAlpha(0.6f);
        hint.setGravity(android.view.Gravity.CENTER);
        hint.setPadding(Ui.dp(this, 24), Ui.dp(this, 6), Ui.dp(this, 24), 0);
        wrap.addView(hint);
        box.addView(wrap, new LinearLayout.LayoutParams(-1, -2));
    }

    private void startRun() {
        running = true;
        showLoading();
        final android.content.Context app = getApplicationContext();
        new Thread(() -> {
            String err;
            try { err = NotifDigest.run(app, false); } catch (Throwable e) { err = String.valueOf(e); }
            running = false;
            final String fe = err;
            DigestActivity a = current.get();
            if (a != null && !a.isFinishing() && !a.isDestroyed()) a.runOnUiThread(() -> a.finishRun(fe));
        }).start();
    }

    private void finishRun(String err) {
        if (err == null) { render(); return; }
        box.removeAllViews();
        Ui.infoCard(this, box, 14, false, true).setText(err);
        MaterialButton retry = Ui.button(this, L.t("再試一次"), null);
        retry.setOnClickListener(v -> startRun());
        box.addView(retry);
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
            if (it != null) addItemCard(o, it);
        }
    }

    /** 一件事一張卡片：標題可以改，日期、時間點一下用選擇器改（時間可以清掉），類型可以切換；按「加入」用改過的內容加入。 */
    private void addItemCard(final JSONObject o, final JSONObject it) {
        LinearLayout card = Ui.card(this, box);
        final boolean added = it.optBoolean("added");
        final android.widget.EditText title = new android.widget.EditText(this);
        title.setText(it.optString("title"));
        title.setHint(L.t("標題"));
        title.setTextSize(16);
        title.setSingleLine(true);
        title.setEnabled(!added);
        card.addView(title);
        if (!it.optString("from").isEmpty()) card.addView(Ui.body(this, L.t("來自 ") + it.optString("from")));

        final String[] date = {it.optString("date")}, time = {it.optString("time")}, type = {it.optString("type", "reminder")};
        final MaterialButton dateBtn = Ui.button(this, "", null), timeBtn = Ui.button(this, "", null), typeBtn = Ui.button(this, "", null);
        final Runnable refresh = () -> {
            dateBtn.setText(date[0].isEmpty() ? L.t("日期：沒有（點一下選）") : L.t("日期：") + date[0]);
            timeBtn.setText(time[0].isEmpty() ? L.t("時間：沒有（點一下選）") : L.t("時間：") + time[0] + L.t("（長按清除）"));
            typeBtn.setText("calendar".equals(type[0]) ? L.t("加到：行事曆（點一下改成提醒）") : L.t("加到：提醒（點一下改成行事曆）"));
        };
        refresh.run();
        dateBtn.setOnClickListener(v -> {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            try {
                if (!date[0].isEmpty()) cal.setTime(new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(date[0]));
            } catch (Exception ignored) { }
            new android.app.DatePickerDialog(this, (dp, y, m, dd) -> {
                date[0] = String.format(java.util.Locale.US, "%04d-%02d-%02d", y, m + 1, dd);
                refresh.run();
            }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)).show();
        });
        timeBtn.setOnClickListener(v -> {
            int h = 9, mi = 0;
            if (time[0].matches("\\d{1,2}:\\d{2}")) { h = Integer.parseInt(time[0].split(":")[0]); mi = Integer.parseInt(time[0].split(":")[1]); }
            new android.app.TimePickerDialog(this, (tp, hh, mm) -> {
                time[0] = String.format(java.util.Locale.US, "%02d:%02d", hh, mm);
                refresh.run();
            }, h, mi, true).show();
        });
        timeBtn.setOnLongClickListener(v -> { time[0] = ""; refresh.run(); return true; });
        typeBtn.setOnClickListener(v -> { type[0] = "calendar".equals(type[0]) ? "reminder" : "calendar"; refresh.run(); });
        for (MaterialButton bt : new MaterialButton[]{dateBtn, timeBtn, typeBtn}) {
            bt.setEnabled(!added);
            card.addView(bt);
        }

        final MaterialButton addBtn = Ui.button(this, added ? L.t("已加入") : L.t("加入"), null);
        addBtn.setEnabled(!added);
        addBtn.setOnClickListener(v -> {
            String t = title.getText().toString().trim();
            if (t.isEmpty()) { android.widget.Toast.makeText(this, L.t("標題不能空白"), android.widget.Toast.LENGTH_SHORT).show(); return; }
            if ("calendar".equals(type[0]) && date[0].isEmpty()) { android.widget.Toast.makeText(this, L.t("加到行事曆要選日期"), android.widget.Toast.LENGTH_SHORT).show(); return; }
            try { it.put("title", t).put("date", date[0]).put("time", time[0]).put("type", type[0]); } catch (Exception ignored) { }
            addBtn.setEnabled(false);
            new Thread(() -> {
                String r = NotifDigest.add(this, it);
                boolean ok = !r.startsWith("錯誤") && !r.contains("失敗");
                try { it.put("added", ok); } catch (Exception ignored) { }
                NotifDigest.save(this, o);
                runOnUiThread(() -> {
                    android.widget.Toast.makeText(this, r, android.widget.Toast.LENGTH_LONG).show();
                    addBtn.setText(ok ? L.t("已加入") : L.t("再試一次"));
                    addBtn.setEnabled(!ok);
                    if (ok) { title.setEnabled(false); dateBtn.setEnabled(false); timeBtn.setEnabled(false); typeBtn.setEnabled(false); }
                });
            }).start();
        });
        card.addView(addBtn);
    }
}
