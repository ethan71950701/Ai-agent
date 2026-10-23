package com.phoneai.overlay;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.SubscriptSpan;
import android.text.style.SuperscriptSpan;
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

    /** 朗讀與通知用的純文字：拿掉 Markdown 符號（粗體、標題、清單符號、程式碼標記；連結只留文字）。 */
    static String plain(String s) {
        if (s == null) return "";
        String t = MathText.flatText(s).replaceAll("(?m)^\\s*```.*$", "");   // 數學式先換成純文字（x² 這種）
        t = t.replaceAll("\\[([^\\]]+)\\]\\([^)]*\\)", "$1");
        t = t.replaceAll("(?m)^#+\\s*", "");
        t = t.replaceAll("(?m)^\\s*[-*•]\\s+", "");
        t = t.replace("**", "").replace("__", "").replace("`", "").replace("*", "");
        return t.replaceAll("\\n{3,}", "\n\n").trim();
    }

    /** 朗讀用的文字：數學式念成口語（「3 的二次方 加 x 的三次方 加 根號 5 等於 3」），其餘的 Markdown 符號拿掉。 */
    static String spoken(String s) {
        return plain(MathText.speakText(s, L.en()));
    }

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

    /** 一行文字：先切出數學式（$…$ 等）排成算式，其餘照粗體、行內程式碼處理。 */
    private static void inline(SpannableStringBuilder out, String t) {
        Matcher mm = MathText.MATH.matcher(t);
        int last = 0;
        while (mm.find()) {
            String in = MathText.inner(mm);
            if (!MathText.looksMath(in)) continue;   // 不像算式（例如價格 $5 到 $7）：原樣留在文字裡
            inlineText(out, t.substring(last, mm.start()));
            appendMath(out, in);
            last = mm.end();
        }
        inlineText(out, t.substring(last));
    }

    /** 排一個數學式：√、×、≤ 等符號、上標與下標縮小抬高或降低、變數字母用斜體。 */
    private static void appendMath(SpannableStringBuilder out, String expr) {
        String e = MathText.normalize(expr);
        int i = 0;
        while (i < e.length()) {
            char c = e.charAt(i);
            if ((c == '^' || c == '_') && i + 1 < e.length()) {
                String arg;
                int next;
                if (e.charAt(i + 1) == '{') {
                    int close = e.indexOf('}', i + 2);
                    if (close < 0) close = e.length();
                    arg = e.substring(i + 2, close);
                    next = Math.min(e.length(), close + 1);
                } else {
                    arg = String.valueOf(e.charAt(i + 1));
                    next = i + 2;
                }
                int s0 = out.length();
                out.append(arg.replace("{", "").replace("}", ""));
                int s1 = out.length();
                out.setSpan(c == '^' ? new SuperscriptSpan() : new SubscriptSpan(), s0, s1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new RelativeSizeSpan(0.75f), s0, s1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                i = next;
            } else if (c == '{' || c == '}') {
                i++;
            } else if (c < 128 && Character.isLetter(c)) {
                int s0 = out.length();
                out.append(c);
                out.setSpan(new StyleSpan(Typeface.ITALIC), s0, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                i++;
            } else {
                out.append(c);
                i++;
            }
        }
    }

    private static void inlineText(SpannableStringBuilder out, String t) {
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
