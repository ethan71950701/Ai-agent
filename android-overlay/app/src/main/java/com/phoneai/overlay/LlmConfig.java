package com.phoneai.overlay;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * AI 服務設定。「便宜模型」（判斷、讀元件清單）和「看圖模型」（截圖）各自設定服務、網址、模型，所以可以混搭，
 * 例如便宜的用 DeepSeek、看圖的用 Gemini。服務是 Claude 或 OpenAI 相容的 API。
 * Key 另存在 App 私有資料夾：Claude 是 claude_key，其他服務依網址分檔（key_網址），同一家的兩個欄位共用同一把。
 */
final class LlmConfig {
    private LlmConfig() { }

    static final String ANTHROPIC = "anthropic", OPENAI = "openai";
    static final String DEF_BASE = "https://api.openai.com/v1", DEF_CHEAP = "gpt-4o-mini", DEF_VISION = "gpt-4o";
    static final String CHEAP = "cheap", VISION = "vision";

    static final class Slot {
        final String provider, base, model;
        Slot(String provider, String base, String model) { this.provider = provider; this.base = base; this.model = model; }
        boolean openai() { return OPENAI.equals(provider); }
        boolean official() { return base.contains("api.openai.com"); }
        String label() { return openai() ? model + " @ " + host(base) : "Claude"; }
    }

    private static SharedPreferences p(Context c) { return c.getSharedPreferences("cfg", Context.MODE_PRIVATE); }

    static Slot cheap(Context c) { return slot(c, CHEAP); }

    static Slot vision(Context c) { return slot(c, VISION); }

    /** 依 Claude 模型名稱挑欄位：haiku 級走便宜模型，其餘（sonnet 級、看截圖）走看圖模型。 */
    static Slot forModel(Context c, String model) { return model != null && model.contains("haiku") ? cheap(c) : vision(c); }

    /** AI 比較測試用：暫時指定便宜／看圖兩個欄位（[0]＝便宜、[1]＝看圖），不改使用者存的設定。 */
    static volatile Slot[] override;

    private static Slot slot(Context c, String name) {
        Slot[] ov = override;
        if (ov != null) return CHEAP.equals(name) ? ov[0] : ov[1];
        SharedPreferences p = p(c);
        String prov = p.getString(name + "_provider", null);
        if (prov == null) {   // 舊版只有一組設定：兩個欄位都用同一個
            if (!OPENAI.equals(p.getString("llm_provider", ANTHROPIC))) return new Slot(ANTHROPIC, "", "");
            return new Slot(OPENAI, p.getString("openai_base", DEF_BASE), p.getString("openai_" + name, CHEAP.equals(name) ? DEF_CHEAP : DEF_VISION));
        }
        return new Slot(prov, p.getString(name + "_base", ""), p.getString(name + "_model", ""));
    }

    static void set(Context c, String name, String provider, String base, String model) {
        p(c).edit().putString(name + "_provider", provider).putString(name + "_base", base.trim()).putString(name + "_model", model.trim()).apply();
    }

    static String host(String base) {
        try { return new java.net.URI(base.trim()).getHost(); } catch (Exception e) { return base; }
    }

    static String keyFile(Slot s) {
        if (!s.openai()) return "claude_key";
        String h = host(s.base);
        return "key_" + (h == null ? "custom" : h.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "_"));
    }

    /** 這個服務自己的 Key 檔已經存過了（不算舊版的 openai_key 備援，那可能是別家的）。 */
    static boolean hasOwnKey(Context c, Slot s) {
        try {
            java.io.File f = new java.io.File(c.getFilesDir(), keyFile(s));
            return f.isFile() && new String(java.nio.file.Files.readAllBytes(f.toPath()), java.nio.charset.StandardCharsets.UTF_8).trim().length() > 0;
        } catch (Exception e) { return false; }
    }

    /** 這個欄位用的 Key；沒有回 null。舊版的 openai_key 當備援。 */
    static String readKey(Context c, Slot s) {
        String[] files = s.openai() ? new String[]{keyFile(s), "openai_key"} : new String[]{"claude_key"};
        for (String f : files) {
            try {
                String k = new String(java.nio.file.Files.readAllBytes(new java.io.File(c.getFilesDir(), f).toPath()), java.nio.charset.StandardCharsets.UTF_8).trim();
                if (!k.isEmpty()) return k;
            } catch (Exception ignored) { }
        }
        return null;
    }
}
