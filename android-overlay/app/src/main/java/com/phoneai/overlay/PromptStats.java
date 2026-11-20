package com.phoneai.overlay;

import android.content.Context;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 量每一次送給 AI 的請求有多大（只量、不改行為）：系統提示、工具定義、對話各佔多少字元，
 * 以及服務回報的實際輸入 token 與花的時間，按來源（聊天、自動操作、路由…）平均。
 * 給「要不要把工具分成專家、只載入需要的」這個決定用。最多留最近 100 筆。
 */
final class PromptStats {
    private PromptStats() { }

    static final int MAX = 100;

    static final class Entry {
        long t;
        String src = "", model = "";
        long sys, tools, msgs, in = -1, out = -1, ms;
    }

    /** 這個請求內容的各部分大小（字元數）。 */
    static Entry measure(String src, JSONObject body, JSONObject usage, long ms, long now) {
        Entry e = new Entry();
        e.t = now;
        e.src = src;
        e.model = body.optString("model");
        Object sys = body.opt("system");
        e.sys = sys == null ? 0 : String.valueOf(sys).length();
        JSONArray tools = body.optJSONArray("tools");
        e.tools = tools == null || tools.length() == 0 ? 0 : tools.toString().length();
        JSONArray msgs = body.optJSONArray("messages");
        e.msgs = msgs == null ? 0 : msgs.toString().length();
        if (usage != null) {
            long in = usage.optLong("input_tokens", -1), pt = usage.optLong("prompt_tokens", -1);
            long cache = usage.optLong("cache_creation_input_tokens", 0) + usage.optLong("cache_read_input_tokens", 0);
            e.in = in >= 0 ? in + cache : pt;
            e.out = usage.optLong("output_tokens", usage.optLong("completion_tokens", -1));
        }
        e.ms = ms;
        return e;
    }

    /** 從呼叫端的類別名決定來源：ChatEngine 自己呼叫的是聊天，其他用它們的類別名。 */
    static String sourceOf(StackTraceElement[] stack) {
        for (StackTraceElement f : stack) {
            String c = f.getClassName();
            if (!c.startsWith("com.phoneai.overlay.")) continue;
            String simple = c.substring("com.phoneai.overlay.".length());
            int dollar = simple.indexOf('$');
            if (dollar > 0) simple = simple.substring(0, dollar);
            if (simple.equals("PromptStats")) continue;
            if (simple.equals("ChatEngine")) {
                if (f.getMethodName().equals("post") || f.getMethodName().equals("postOnce") || f.getMethodName().equals("postOpenAi")) continue;   // 內部的送出函式不算呼叫端
                return "聊天";
            }
            switch (simple) {
                case "AgentLoop": return "自動操作";
                case "Router": return "路由";
                case "Schedule": return "排程";
                case "NotifDigest": return "通知整理";
                case "TavilySearch": return "網頁搜尋";
                default: return simple;
            }
        }
        return "其他";
    }

    private static File file(Context c) { return new File(c.getFilesDir(), "prompt_stats.json"); }

    static synchronized void record(Context c, Entry e) {
        record(file(c), e);
    }

    static synchronized void record(File f, Entry e) {
        try {
            List<Entry> all = load(f);
            all.add(e);
            while (all.size() > MAX) all.remove(0);
            JSONArray a = new JSONArray();
            for (Entry x : all) {
                a.put(new JSONObject().put("t", x.t).put("src", x.src).put("model", x.model).put("sys", x.sys).put("tools", x.tools)
                        .put("msgs", x.msgs).put("in", x.in).put("out", x.out).put("ms", x.ms));
            }
            File tmp = new File(f.getPath() + ".tmp");
            Files.write(tmp.toPath(), a.toString().getBytes(StandardCharsets.UTF_8));
            if (!tmp.renameTo(f)) Files.write(f.toPath(), a.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) { }
    }

    static List<Entry> load(File f) {
        List<Entry> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                Entry e = new Entry();
                e.t = o.optLong("t");
                e.src = o.optString("src");
                e.model = o.optString("model");
                e.sys = o.optLong("sys");
                e.tools = o.optLong("tools");
                e.msgs = o.optLong("msgs");
                e.in = o.optLong("in", -1);
                e.out = o.optLong("out", -1);
                e.ms = o.optLong("ms");
                out.add(e);
            }
        } catch (Exception ignored) { }
        return out;
    }

    static String summary(Context c) { return format(load(file(c))); }

    /** 按來源平均的一段文字。 */
    static String format(List<Entry> all) {
        if (all.isEmpty()) return "（還沒有紀錄）";
        Map<String, List<Entry>> by = new LinkedHashMap<>();
        for (Entry e : all) { if (!by.containsKey(e.src)) by.put(e.src, new ArrayList<>()); by.get(e.src).add(e); }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<Entry>> g : by.entrySet()) {
            List<Entry> l = g.getValue();
            long sys = 0, tools = 0, msgs = 0, ms = 0, in = 0, out = 0;
            int nIn = 0, nOut = 0;
            for (Entry e : l) {
                sys += e.sys; tools += e.tools; msgs += e.msgs; ms += e.ms;
                if (e.in >= 0) { in += e.in; nIn++; }
                if (e.out >= 0) { out += e.out; nOut++; }
            }
            int n = l.size();
            sb.append(g.getKey()).append("：").append(n).append(" 次｜系統提示 ").append(k(sys / n)).append("｜工具定義 ").append(k(tools / n))
                    .append("｜對話 ").append(k(msgs / n)).append("（字元，平均）｜實際輸入 ")
                    .append(nIn == 0 ? "?" : String.valueOf(in / nIn)).append(" token｜輸出 ").append(nOut == 0 ? "?" : String.valueOf(out / nOut))
                    .append(" token｜").append(String.format(java.util.Locale.US, "%.1f", ms / (double) n / 1000.0)).append(" 秒\\n".replace("\\n", "\n"));
        }
        return sb.toString().trim();
    }

    private static String k(long chars) {
        return chars >= 1000 ? String.format(java.util.Locale.US, "%.1fk", chars / 1000.0) : String.valueOf(chars);
    }
}
