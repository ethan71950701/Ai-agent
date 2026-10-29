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
    /** in＝輸入 token（含快取的寫入與讀取），out＝輸出，cw＝快取寫入，cr＝快取讀取（cw、cr 是 in 裡的明細）。 */
    static synchronized void add(Context c, long in, long out, long cw, long cr) { add(c, "Claude", in, out, cw, cr); }

    /** service＝這次請求用的服務（Claude 或網址的主機名稱），另外記每個服務當天的請求次數與 token。 */
    static synchronized void add(Context c, String service, long in, long out, long cw, long cr) {
        try {
            JSONObject d = load(c);
            String k = today();
            JSONObject e = d.optJSONObject(k);
            long i = (e == null ? 0 : e.optLong("in")) + in, o = (e == null ? 0 : e.optLong("out")) + out;
            long w = (e == null ? 0 : e.optLong("cw")) + cw, r = (e == null ? 0 : e.optLong("cr")) + cr;
            JSONObject svc = e == null || e.optJSONObject("svc") == null ? new JSONObject() : e.getJSONObject("svc");
            JSONObject one = svc.optJSONObject(service) == null ? new JSONObject() : svc.getJSONObject(service);
            svc.put(service, new JSONObject().put("req", one.optLong("req") + 1).put("in", one.optLong("in") + in).put("out", one.optLong("out") + out));
            d.put(k, new JSONObject().put("in", i).put("out", o).put("cw", w).put("cr", r).put("svc", svc));
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

    /** 日期前綴的合計（"2026-10-22" 是某一天，"2026-10" 是某個月）：[輸入, 輸出, 快取寫入, 快取讀取]。 */
    static synchronized long[] sum(Context c, String prefix) {
        long[] s = new long[4];
        JSONObject d = load(c);
        java.util.Iterator<String> keys = d.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            if (!k.startsWith(prefix)) continue;
            JSONObject e = d.optJSONObject(k);
            if (e == null) continue;
            s[0] += e.optLong("in");
            s[1] += e.optLong("out");
            s[2] += e.optLong("cw");
            s[3] += e.optLong("cr");
        }
        return s;
    }

    /** 各服務在日期前綴範圍內的 [請求次數, 輸入, 輸出]，依服務名稱。 */
    static synchronized java.util.Map<String, long[]> byService(Context c, String prefix) {
        java.util.Map<String, long[]> out = new java.util.TreeMap<>();
        JSONObject d = load(c);
        java.util.Iterator<String> keys = d.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            JSONObject e = k.startsWith(prefix) ? d.optJSONObject(k) : null;
            JSONObject svc = e == null ? null : e.optJSONObject("svc");
            if (svc == null) continue;
            java.util.Iterator<String> names = svc.keys();
            while (names.hasNext()) {
                String n = names.next();
                JSONObject o = svc.optJSONObject(n);
                if (o == null) continue;
                long[] a = out.get(n);
                if (a == null) out.put(n, a = new long[3]);
                a[0] += o.optLong("req");
                a[1] += o.optLong("in");
                a[2] += o.optLong("out");
            }
        }
        return out;
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
        info.setText(String.format(L.t("今天：%,d token（輸入 %,d、輸出 %,d）\n　快取寫入 %,d、快取讀取 %,d\n這個月：%,d token（輸入 %,d、輸出 %,d）\n\n這是 token 數，不是金額；金額請到 Anthropic Console 的 Usage 查。上限填千 token，0 或留空 = 不限。"),
                td[0] + td[1], td[0], td[1], td[2], td[3], tm[0] + tm[1], tm[0], tm[1]));
        info.setTextSize(14);
        LinearLayout form = new LinearLayout(a);
        form.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (20 * d);
        form.setPadding(p, p / 2, p, 0);
        form.addView(info);
        form.addView(breakdown(a, today(), L.t("今天各服務")));
        form.addView(breakdown(a, today().substring(0, 7), L.t("這個月各服務")));
        addUsageLinks(a, form, info);
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

    private static TextView breakdown(Activity a, String prefix, String title) {
        StringBuilder sb = new StringBuilder("\n").append(title).append("：");
        java.util.Map<String, long[]> m = byService(a, prefix);
        if (m.isEmpty()) sb.append(L.t("（還沒有紀錄）"));
        for (java.util.Map.Entry<String, long[]> e : m.entrySet())
            sb.append("\n　").append(e.getKey()).append("　").append(e.getValue()[0]).append(L.t(" 次、")).append(String.format(Locale.US, "%,d", e.getValue()[1] + e.getValue()[2])).append(" token");
        TextView t = new TextView(a);
        t.setText(sb.toString());
        t.setTextSize(14);
        return t;
    }

    /** 「查詢餘額」（OpenRouter、DeepSeek 有公開的餘額查詢）與各服務官網的用量頁連結。 */
    private static void addUsageLinks(Activity a, LinearLayout form, TextView info) {
        java.util.LinkedHashMap<String, String> pages = new java.util.LinkedHashMap<>();
        boolean balance = false;
        for (LlmConfig.Slot s : new LlmConfig.Slot[]{LlmConfig.cheap(a), LlmConfig.vision(a)}) {
            LlmPresets.Preset ps = LlmPresets.forSlot(s);
            if (ps != null && !ps.usageUrl.isEmpty()) pages.put(ps.shortName(), ps.usageUrl);
            if (s.openai() && (LlmConfig.host(s.base).contains("openrouter") || LlmConfig.host(s.base).contains("deepseek"))) balance = true;
        }
        if (!LlmConfig.cheap(a).openai() || !LlmConfig.vision(a).openai()) pages.put("Claude", "https://console.anthropic.com/settings/usage");
        if (balance) {
            com.google.android.material.button.MaterialButton b = Ui.pill(a, L.t("查詢餘額（OpenRouter／DeepSeek）"), false, v -> {
                info.setText(L.t("查詢中…"));
                new Thread(() -> {
                    String r = LlmBalance.query(a);
                    a.runOnUiThread(() -> info.setText(r));
                }).start();
            });
            form.addView(b, new LinearLayout.LayoutParams(-1, Ui.dp(a, 44)));
        }
        for (java.util.Map.Entry<String, String> e : pages.entrySet()) {
            com.google.android.material.button.MaterialButton b = Ui.pill(a, L.t("開啟官網用量頁：") + e.getKey(), false, v -> {
                try { a.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(e.getValue()))); } catch (Exception ignored) { }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Ui.dp(a, 44));
            lp.topMargin = Ui.dp(a, 6);
            form.addView(b, lp);
        }
    }

    // ---------- 任務用量：同一個執行緒裡的 Claude 呼叫，累加到目前這個任務（給任務過程用） ----------

    /** 每百萬 token 的美元價 [輸入, 輸出]（第三方整理，請以 Console 為準）；快取寫入約 1.25 倍輸入價，快取讀取 0.1 倍。 */
    static double[] price(String model) {
        if (model.contains("haiku")) return new double[]{0.10, 0.50};
        if (model.contains("sonnet")) return new double[]{2.00, 10.00};
        return new double[]{0, 0};
    }

    /** 模型代號轉成給人看的名字，例如 claude-haiku-5-5 → Haiku 5.5。 */
    static String modelName(String model) {
        if (model == null || model.isEmpty()) return "";
        if (!model.startsWith("claude-")) return model;   // 別家的模型名稱照原樣顯示
        String s = model.replace("claude-", "");
        int dash = s.indexOf('-');
        if (dash < 0) return s;
        String name = s.substring(0, dash);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1) + " " + s.substring(dash + 1).replace('-', '.');
    }

    /** 累計：[總 token, 輸入（含快取）, 輸出, 快取寫入, 快取讀取]。 */
    private static final ThreadLocal<long[]> TALLY = new ThreadLocal<>();
    private static final ThreadLocal<double[]> COST = new ThreadLocal<>();

    static void beginTally() { TALLY.set(new long[5]); COST.set(new double[1]); }

    /** 結束這個任務的計算，回傳用了多少 token。 */
    static long endTally() {
        long[] t = TALLY.get();
        TALLY.remove();
        COST.remove();
        return t == null ? 0 : t[0];
    }

    /** 目前為止這個任務用了多少 token（沒在計算就回 0）。 */
    static long tallyNow() {
        long[] t = TALLY.get();
        return t == null ? 0 : t[0];
    }

    /** 目前為止各項數字的複本，給每一步算差額用。 */
    static long[] tallyCounts() {
        long[] t = TALLY.get();
        return t == null ? new long[5] : t.clone();
    }

    /** 目前為止估算的花費（美元）。 */
    static double tallyCost() {
        double[] c = COST.get();
        return c == null ? 0 : c[0];
    }

    /** 記一次 Claude 呼叫：in 含快取，cw、cr 是其中的快取寫入與讀取。 */
    static void tallyAdd(String model, long in, long out, long cw, long cr) {
        long[] t = TALLY.get();
        if (t != null) {
            t[0] += in + out;
            t[1] += in;
            t[2] += out;
            t[3] += cw;
            t[4] += cr;
        }
        double[] c = COST.get();
        if (c != null) {
            double[] p = price(model);
            long base = in - cw - cr;
            c[0] += (base * p[0] + cw * p[0] * 1.25 + cr * p[0] * 0.10 + out * p[1]) / 1000000.0;
        }
    }
}
