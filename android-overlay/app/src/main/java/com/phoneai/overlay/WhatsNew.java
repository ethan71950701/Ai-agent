package com.phoneai.overlay;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 更新內容：打開 App 發現版本比上次看到的新（可能中間跳過好幾版），跳一個視窗列出這幾版的更新內容。
 * 內容來自 APK 裡附的 CHANGELOG.md（編譯時複製進 assets），不需要網路。「## Unreleased」就是這次編譯的版本。
 * 全新安裝不跳（沒有「上次」可比）；更新內容也可以從「功能 → 更新 → 更新內容」隨時看。
 */
final class WhatsNew {
    private WhatsNew() { }

    static final class Version {
        final int build;
        final List<String> lines = new ArrayList<>();
        Version(int build) { this.build = build; }
    }

    private static boolean shownThisRun;
    private static final int MAX_VERSIONS = 8;

    /** 解析 CHANGELOG.md：每個「## 1.0.NNNN」一段，「## Unreleased」算 currentBuild；每段的「- 」條列。由新到舊。 */
    static List<Version> parse(String md, int currentBuild) {
        List<Version> out = new ArrayList<>();
        Version cur = null;
        for (String raw : md.split("\n")) {
            String line = raw.trim();
            if (line.startsWith("## ")) {
                String h = line.substring(3).trim();
                cur = null;
                if (h.equalsIgnoreCase("Unreleased")) cur = new Version(currentBuild);
                else if (h.matches("1\\.0\\.\\d+")) cur = new Version(Integer.parseInt(h.substring(4)));
                if (cur != null) out.add(cur);
            } else if (cur != null && line.startsWith("- ")) {
                String item = line.substring(2).trim();
                if (item.contains("電腦版") || item.contains("Windows app") || item.contains("Windows 版")) continue;   // 跟 Release 說明一樣，不放電腦版的條目
                cur.lines.add(item);
            }
        }
        out.removeIf(v -> v.lines.isEmpty());
        out.sort((a, b) -> Integer.compare(b.build, a.build));
        return out;
    }

    /** 「中文｜English」取符合介面語言的那一半。 */
    static String pick(String item, boolean en) {
        int i = item.indexOf('｜');
        if (i < 0) return item;
        return (en ? item.substring(i + 1) : item.substring(0, i)).trim();
    }

    /** 組成要顯示的文字：build 大於 after 的版本，由新到舊，最多 MAX_VERSIONS 版（超過的不放，顯示時用可點的一行展開）。 */
    static String render(List<Version> all, int after, boolean en) { return render(all, after, en, MAX_VERSIONS); }

    /** 有幾版 build 大於 after。 */
    static int count(List<Version> all, int after) {
        int total = 0;
        for (Version v : all) if (v.build > after) total++;
        return total;
    }

    static String render(List<Version> all, int after, boolean en, int max) {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (Version v : all) {
            if (v.build <= after) continue;
            if (n == max) break;
            if (sb.length() > 0) sb.append("\n\n");
            sb.append("1.0.").append(v.build).append('\n');
            for (String l : v.lines) sb.append("• ").append(pick(l, en)).append('\n');
            n++;
        }
        return sb.toString().trim();
    }

    private static List<Version> load(Context c) {
        try (InputStream in = c.getAssets().open("CHANGELOG.md")) {
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), BuildConfig.VERSION_CODE);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** 版本比上次看到的新就跳視窗（每次執行最多一次）。第一次執行只記下版本、不跳。 */
    static void maybeShow(Activity a) {
        if (shownThisRun) return;
        SharedPreferences p = a.getSharedPreferences("cfg", Context.MODE_PRIVATE);
        int seen = p.getInt("whatsnew_seen", 0), now = BuildConfig.VERSION_CODE;
        if (seen >= now) return;
        shownThisRun = true;
        p.edit().putInt("whatsnew_seen", now).apply();
        if (seen == 0) return;   // 全新安裝（或這個功能剛加進來的第一版）：沒有「上次」可比
        List<Version> all = load(a);
        if (count(all, seen) > 0) show(a, all, seen);
    }

    /** 設定裡的「更新內容」：看最近幾版。 */
    static void showRecent(Activity a) {
        List<Version> all = load(a);
        if (all.isEmpty()) { android.widget.Toast.makeText(a, L.t("沒有更新內容"), android.widget.Toast.LENGTH_SHORT).show(); return; }
        show(a, all, 0);   // 全部版本，先列 8 版，其餘點「顯示更早的」展開
    }

    /** 視窗：先列最多 MAX_VERSIONS 版；還有更早的，最後一行「顯示更早的 N 版」可以點，點了展開全部。 */
    private static void show(Activity a, List<Version> all, int after) {
        float d = a.getResources().getDisplayMetrics().density;
        final TextView tv = new TextView(a);
        tv.setTextSize(15);
        tv.setLineSpacing(0, 1.15f);
        tv.setPadding((int) (24 * d), (int) (8 * d), (int) (24 * d), (int) (8 * d));
        tv.setTextColor(Ui.attr(a, com.google.android.material.R.attr.colorOnSurface));
        tv.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        bind(tv, all, after, MAX_VERSIONS);
        ScrollView sv = new ScrollView(a);
        sv.addView(tv);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(a).setTitle(L.t("更新內容") + " · 1.0." + BuildConfig.VERSION_CODE).setView(sv)
                .setPositiveButton(L.t("知道了"), null).show();
    }

    private static void bind(final TextView tv, final List<Version> all, final int after, int max) {
        final boolean en = L.en();
        String body = render(all, after, en, max);
        int more = count(all, after) - max;
        if (more <= 0) { tv.setText(body); return; }
        String link = en ? "Show " + more + " older version" + (more > 1 ? "s" : "") : "顯示更早的 " + more + " 版";
        android.text.SpannableString sp = new android.text.SpannableString(body + "\n\n" + link);
        sp.setSpan(new android.text.style.ClickableSpan() {
            @Override public void onClick(android.view.View v) { bind(tv, all, after, Integer.MAX_VALUE); }
            @Override public void updateDrawState(android.text.TextPaint ds) {
                ds.setColor(Ui.attr(tv.getContext(), com.google.android.material.R.attr.colorPrimary));
                ds.setUnderlineText(true);
            }
        }, body.length() + 2, sp.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tv.setText(sp);
    }
}
