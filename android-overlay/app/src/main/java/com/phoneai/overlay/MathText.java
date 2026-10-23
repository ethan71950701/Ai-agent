package com.phoneai.overlay;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 數學式處理（純字串，不依賴 Android）。聊天助理用 LaTeX 寫數學式（$x^2 + 3 = 7$）：
 * normalize／flat 把它轉成畫面與通知看得懂的符號（√、×、≤、上標…），spoken 轉成口語給朗讀
 * （「x 的二次方 加 3 等於 7」）。只處理常見的符號，其他指令只留名字。
 */
final class MathText {
    /** 數學式：$$…$$、$…$、\(…\)、\[…\]。 */
    static final Pattern MATH = Pattern.compile("\\$\\$(.+?)\\$\\$|\\$([^$\\n]+?)\\$|\\\\\\((.+?)\\\\\\)|\\\\\\[(.+?)\\\\\\]", Pattern.DOTALL);
    private static final Pattern MATHISH = Pattern.compile("[\\\\^_={}+]");
    private static final Pattern FRAC = Pattern.compile("\\\\d?frac\\s*\\{([^{}]*)\\}\\s*\\{([^{}]*)\\}");
    private static final Pattern SQRT_BRACE = Pattern.compile("\\\\sqrt\\s*\\{([^{}]*)\\}");
    private static final Pattern SQRT_ONE = Pattern.compile("\\\\sqrt\\s*([A-Za-z0-9])");
    private static final Pattern POW_BRACE = Pattern.compile("\\^\\s*\\{([^{}]*)\\}");
    private static final Pattern POW_ONE = Pattern.compile("\\^\\s*([A-Za-z0-9])");
    private static final Pattern DEGREE = Pattern.compile("\\^\\s*\\{?\\\\circ\\}?");
    private static final Pattern SUB_BRACE = Pattern.compile("_\\s*\\{([^{}]*)\\}");
    private static final Pattern SUB_ONE = Pattern.compile("_\\s*([A-Za-z0-9])");
    private static final Pattern CMD = Pattern.compile("\\\\([A-Za-z]+)");

    /** 指令 → {畫面符號, 中文口語, 英文口語}。長的放前面（\leq 要在 \le 前面）。 */
    private static final String[][] SYMBOLS = {
            {"times", "×", "乘", "times"}, {"div", "÷", "除以", "divided by"}, {"cdot", "·", "乘", "times"},
            {"pm", "±", "正負", "plus or minus"}, {"mp", "∓", "負正", "minus or plus"},
            {"leq", "≤", "小於等於", "less than or equal to"}, {"geq", "≥", "大於等於", "greater than or equal to"},
            {"neq", "≠", "不等於", "not equal to"}, {"approx", "≈", "約等於", "approximately"},
            {"le", "≤", "小於等於", "less than or equal to"}, {"ge", "≥", "大於等於", "greater than or equal to"},
            {"ne", "≠", "不等於", "not equal to"}, {"infty", "∞", "無限大", "infinity"}, {"pi", "π", "圓周率", "pi"},
            {"alpha", "α", "阿爾法", "alpha"}, {"beta", "β", "貝塔", "beta"}, {"gamma", "γ", "伽瑪", "gamma"},
            {"delta", "δ", "德爾塔", "delta"}, {"Delta", "Δ", "德爾塔", "delta"}, {"theta", "θ", "西塔", "theta"},
            {"lambda", "λ", "拉姆達", "lambda"}, {"mu", "μ", "謬", "mu"}, {"sigma", "σ", "西格瑪", "sigma"},
            {"omega", "ω", "歐米加", "omega"}, {"sum", "∑", "總和", "the sum of"}, {"int", "∫", "積分", "the integral of"},
            {"rightarrow", "→", "趨近", "approaches"}, {"to", "→", "趨近", "approaches"}, {"ldots", "…", "等等", "and so on"},
            {"circ", "°", "度", "degrees"}, {"degree", "°", "度", "degrees"},
    };

    private MathText() { }

    static String inner(Matcher m) {
        for (int g = 1; g <= 4; g++) if (m.group(g) != null) return m.group(g);
        return "";
    }

    /** 像數學式才算（避免把「$5 到 $7」當成算式）。 */
    static boolean looksMath(String s) { return MATHISH.matcher(s).find(); }

    private interface Fn { String apply(Matcher m); }

    private static String sub(Pattern p, String s, Fn f) {
        Matcher m = p.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(f.apply(m)));
        m.appendTail(sb);
        return sb.toString();
    }

    private static String cleanup(String e) {
        String t = e.replaceAll("\\\\(left|right|bigg|Bigg|big|Big)(?![A-Za-z])", "");
        t = t.replaceAll("\\\\[,;:!]", "").replace("\\quad", " ").replace("\\ ", " ");
        return t;
    }

    private static String wrap(String x) {
        String s = x.trim();
        return s.length() <= 1 ? s : "(" + s + ")";
    }

    private static String symbol(String cmd, int col) {
        for (String[] r : SYMBOLS) if (r[0].equals(cmd)) return r[col];
        return null;
    }

    /** 畫面用：LaTeX 指令換成符號（√、×、≤…）；^ 與 _ 留著，給排版時做上標與下標。 */
    static String normalize(String expr) {
        String t = cleanup(expr);
        for (int i = 0; i < 5; i++) t = sub(FRAC, t, m -> wrap(m.group(1)) + "/" + wrap(m.group(2)));
        t = sub(SQRT_BRACE, t, m -> "√" + wrap(m.group(1)));
        t = sub(SQRT_ONE, t, m -> "√" + m.group(1));
        t = DEGREE.matcher(t).replaceAll("°");
        t = sub(CMD, t, m -> {
            String s = symbol(m.group(1), 1);
            return s != null ? s : m.group(1);
        });
        return t;
    }

    private static final String SUP_FROM = "0123456789+-n", SUP_TO = "⁰¹²³⁴⁵⁶⁷⁸⁹⁺⁻ⁿ";
    private static final String SUB_FROM = "0123456789", SUB_TO = "₀₁₂₃₄₅₆₇₈₉";

    private static String script(String arg, String from, String to, String fallbackPrefix) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arg.length(); i++) {
            int k = from.indexOf(arg.charAt(i));
            if (k < 0) return fallbackPrefix + "(" + arg + ")";
            sb.append(to.charAt(k));
        }
        return sb.toString();
    }

    /** 通知用的純文字：上標下標換成 Unicode 小字，沒有對應的就用 ^(…)。 */
    static String flat(String expr) {
        String t = normalize(expr);
        t = sub(POW_BRACE, t, m -> script(m.group(1).trim(), SUP_FROM, SUP_TO, "^"));
        t = sub(POW_ONE, t, m -> script(m.group(1), SUP_FROM, SUP_TO, "^"));
        t = sub(SUB_BRACE, t, m -> script(m.group(1).trim(), SUB_FROM, SUB_TO, "_"));
        t = sub(SUB_ONE, t, m -> script(m.group(1), SUB_FROM, SUB_TO, "_"));
        return t.replace("{", "").replace("}", "").replaceAll("\\s+", " ").trim();
    }

    private static String cn(String n) {
        switch (n) {
            case "2": return "二";
            case "3": return "三";
            case "4": return "四";
            case "5": return "五";
            case "6": return "六";
            case "7": return "七";
            case "8": return "八";
            case "9": return "九";
            case "10": return "十";
            default: return n;
        }
    }

    private static String power(String n, boolean en) {
        String s = n.trim();
        if (en) {
            if (s.equals("2")) return " squared ";
            if (s.equals("3")) return " cubed ";
            return " to the power of " + s + " ";
        }
        return " 的" + cn(s) + "次方 ";
    }

    /** 朗讀用：LaTeX → 口語（中文：「x 的二次方 加 3 等於 7」；en = true 用英文）。 */
    static String spoken(String expr, boolean en) {
        String t = cleanup(expr);
        for (int i = 0; i < 5; i++)
            t = sub(FRAC, t, m -> en ? " " + m.group(1) + " over " + m.group(2) + " " : " " + m.group(2) + " 分之 " + m.group(1) + " ");
        t = sub(SQRT_BRACE, t, m -> (en ? " square root of " : " 根號 ") + m.group(1) + " ");
        t = sub(SQRT_ONE, t, m -> (en ? " square root of " : " 根號 ") + m.group(1) + " ");
        t = DEGREE.matcher(t).replaceAll(en ? " degrees " : " 度 ");
        t = sub(POW_BRACE, t, m -> power(m.group(1), en));
        t = sub(POW_ONE, t, m -> power(m.group(1), en));
        t = sub(SUB_BRACE, t, m -> m.group(1));
        t = sub(SUB_ONE, t, m -> m.group(1));
        t = sub(CMD, t, m -> {
            String s = symbol(m.group(1), en ? 3 : 2);
            return " " + (s != null ? s : m.group(1)) + " ";
        });
        t = t.replace("{", " ").replace("}", " ");
        t = t.replace("=", en ? " equals " : " 等於 ").replace("+", en ? " plus " : " 加 ")
                .replace("−", en ? " minus " : " 減 ").replace("-", en ? " minus " : " 減 ")
                .replace("*", en ? " times " : " 乘 ").replace("/", en ? " divided by " : " 除以 ")
                .replace("<", en ? " less than " : " 小於 ").replace(">", en ? " greater than " : " 大於 ");
        return t.replaceAll("\\s+", " ").trim();
    }

    /** 整段文字給朗讀：數學式換成口語，文字裡零星的 ² ³ √ × ÷ 也念出來。 */
    static String speakText(String text, boolean en) {
        if (text == null) return "";
        Matcher m = MATH.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String in = inner(m);
            String rep = looksMath(in) ? " " + spoken(in, en) + " " : m.group();
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        String t = sb.toString();
        t = t.replace("²", en ? " squared " : " 的二次方 ").replace("³", en ? " cubed " : " 的三次方 ")
                .replace("√", en ? " square root of " : " 根號 ").replace("×", en ? " times " : " 乘 ")
                .replace("÷", en ? " divided by " : " 除以 ").replace("≤", en ? " less than or equal to " : " 小於等於 ")
                .replace("≥", en ? " greater than or equal to " : " 大於等於 ").replace("≠", en ? " not equal to " : " 不等於 ")
                .replace("≈", en ? " approximately " : " 約等於 ");
        return t;
    }

    /** 通知與畫面之外的純文字版：把整段文字裡的數學式換成 flat。 */
    static String flatText(String text) {
        if (text == null) return "";
        Matcher m = MATH.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String in = inner(m);
            m.appendReplacement(sb, Matcher.quoteReplacement(looksMath(in) ? flat(in) : m.group()));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
