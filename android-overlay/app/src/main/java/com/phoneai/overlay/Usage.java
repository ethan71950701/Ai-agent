package com.phoneai.overlay;

import android.app.Activity;
import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Claude 用量：每次 API 回應的 usage（輸入＋輸出 token）按日記在 files/usage.json。
 * 只記 token 數，不換算金額（價格請對照 Console Usage）。上限存在 cfg：budget_day、budget_month（token 數，0 = 不限）；
 * 超過上限後，Claude 相關功能先不送出，直接回一句說明。
 */
final class Usage {
    private static final int KEEP_DAYS = 400;

    private Usage() { }

    private static File file(Context c) { return new File(c.getFilesDir(), "usage.json"); }

    static String day(long t) { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(t)); }

    private static String today() { return day(System.currentTimeMillis()); }

    private static synchronized JSONObject load(Context c) {
        try {
            return new JSONObject(new String(Files.readAllBytes(file(c).toPath()), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    /** 記一次呼叫的用量（在背景執行緒呼叫）。 */
    static synchronized void add(Context c, long in, long out) {
        try {
            JSONObject d = load(c);
            String k = today();
            JSONObject e = d.optJSONObject(k);
            long i = (e == null ? 0 : e.optLong("in")) + in, o = (e == null ? 0 : e.optLong("out")) + out;
            d.put(k, new JSONObject().put("in", i).put("out", o));
            String cutoff = day(System.currentTimeMillis() - KEEP_DAYS * 86400000L);
            List<String> old = new ArrayList<>();
            java.util.Iterator<String> keys = d.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (key.compareTo(cutoff) < 0) old.add(key);
            }
            for (String key : old) d.remove(key);
            File f = file(c), tmp = new File(f.getPath() + ".tmp");
            Files.write(tmp.toPath(), d.toString().getBytes(StandardCharsets.UTF_8));
            if (!tmp.renameTo(f)) throw new IllegalStateException("usage.json rename failed");
        } catch (Exception ignored) {
            // 記帳失敗不影響任務本身
        }
    }

    /** 日期前綴的合計（"2026-10-22" 是某一天，"2026-10" 是某個月）：[輸入, 輸出]。 */
    static synchronized long[] sum(Context c, String prefix) {
        long[] s = new long[2];
        JSONObject d = load(c);
        java.util.Iterator<String> keys = d.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            if (!k.startsWith(prefix)) continue;
            JSONObject e = d.optJSONObject(k);
            if (e == null) continue;
            s[0] += e.optLong("in");
            s[1] += e.optLong("out");
        }
        return s;
    }

    static long limitDay(Context c) { return c.getSharedPreferences("cfg", Context.MODE_PRIVATE).getLong("budget_day", 0); }

    static long limitMonth(Context c) { return c.getSharedPreferences("cfg", Context.MODE_PRIVATE).getLong("budget_month", 0); }

    static void setLimits(Context c, long day, long month) {
        c.getSharedPreferences("cfg", Context.MODE_PRIVATE).edit().putLong("budget_day", day).putLong("budget_month", month).apply();
    }

    /** 已超過上限就回傳要顯示的說明；沒超過回 null。 */
    static String overLimit(Context c) {
        long ld = limitDay(c), lm = limitMonth(c);
        if (ld > 0) {
            long[] s = sum(c, today());
            if (s[0] + s[1] >= ld)
                return String.format(L.t("今天的 Claude 用量已達上限（%,d token）。可在「功能 → 維護 → 用量與上限」調整。"), ld);
        }
        if (lm > 0) {
            long[] s = sum(c, today().substring(0, 7));
            if (s[0] + s[1] >= lm)
                return String.format(L.t("這個月的 Claude 用量已達上限（%,d token）。可在「功能 → 維護 → 用量與上限」調整。"), lm);
        }
        return null;
    }

    private static long parseK(EditText e) {
        try {
            String t = e.getText().toString().trim();
            return t.isEmpty() ? 0 : Long.parseLong(t);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    /** 設定頁的「用量與上限」對話框：看今天與這個月用了多少，並設定上限（輸入千 token，0 = 不限）。 */
    static void dialog(Activity a) {
        float d = a.getResources().getDisplayMetrics().density;
        long[] td = sum(a, today()), tm = sum(a, today().substring(0, 7));
        TextView info = new TextView(a);
        info.setText(String.format(L.t("今天：%,d token（輸入 %,d、輸出 %,d）\n這個月：%,d token（輸入 %,d、輸出 %,d）\n\n這是 token 數，不是金額；金額請到 Anthropic Console 的 Usage 查。上限填千 token，0 或留空 = 不限。"),
                td[0] + td[1], td[0], td[1], tm[0] + tm[1], tm[0], tm[1]));
        info.setTextSize(14);
        LinearLayout form = new LinearLayout(a);
        form.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (20 * d);
        form.setPadding(p, p / 2, p, 0);
        form.addView(info);
        EditText dayEt = new EditText(a);
        dayEt.setHint(L.t("每日上限（千 token）"));
        dayEt.setInputType(InputType.TYPE_CLASS_NUMBER);
        if (limitDay(a) > 0) dayEt.setText(String.valueOf(limitDay(a) / 1000));
        EditText monEt = new EditText(a);
        monEt.setHint(L.t("每月上限（千 token）"));
        monEt.setInputType(InputType.TYPE_CLASS_NUMBER);
        if (limitMonth(a) > 0) monEt.setText(String.valueOf(limitMonth(a) / 1000));
        form.addView(dayEt);
        form.addView(monEt);
        new MaterialAlertDialogBuilder(a).setTitle(L.t("用量與上限")).setView(form)
                .setPositiveButton(L.t("儲存"), (dlg, w) -> setLimits(a, parseK(dayEt) * 1000, parseK(monEt) * 1000))
                .setNegativeButton(L.t("取消"), null).show();
    }
}
