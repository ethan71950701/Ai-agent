package com.phoneai.overlay;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 回答卡片的簡易 Markdown 排版：**粗體**、`行內程式碼`、``` 程式碼區塊 ```、# 標題、- 或 * 清單。其他符號原樣顯示。
 * 只給畫面用；朗讀還是讀原文。
 */
final class Md {
    private static final Pattern INLINE = Pattern.compile("\\*\\*(.+?)\\*\\*|`([^`]+)`");

    private Md() { }

    static SpannableStringBuilder render(String s) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        String[] lines = s.split("\n", -1);
        boolean code = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().startsWith("```")) {
                code = !code;
                continue;
            }
            int start = out.length();
            if (code) {
                out.append(line);
                out.setSpan(new TypefaceSpan("monospace"), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                boolean heading = line.startsWith("#");
                String t = heading ? line.replaceFirst("^#+\\s*", "") : line.replaceFirst("^\\s*[-*]\\s+", "•  ");
                inline(out, t);
                if (heading) out.setSpan(new StyleSpan(Typeface.BOLD), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (i < lines.length - 1) out.append('\n');
        }
        return out;
    }

    private static void inline(SpannableStringBuilder out, String t) {
        Matcher m = INLINE.matcher(t);
        int last = 0;
        while (m.find()) {
            out.append(t, last, m.start());
            int start = out.length();
            if (m.group(1) != null) {
                out.append(m.group(1));
                out.setSpan(new StyleSpan(Typeface.BOLD), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                out.append(m.group(2));
                out.setSpan(new TypefaceSpan("monospace"), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            last = m.end();
        }
        out.append(t, last, t.length());
    }
}
