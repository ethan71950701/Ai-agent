package com.phoneai.overlay;

import android.content.Context;
import android.content.SharedPreferences;

/** AI 服務設定：Claude（預設）或 OpenAI 相容的 API。Key 另存在 App 私有資料夾（claude_key／openai_key），不放進設定檔。 */
final class LlmConfig {
    private LlmConfig() { }

    static final String ANTHROPIC = "anthropic", OPENAI = "openai";
    static final String DEF_BASE = "https://api.openai.com/v1", DEF_CHEAP = "gpt-4o-mini", DEF_VISION = "gpt-4o";

    private static SharedPreferences p(Context c) { return c.getSharedPreferences("cfg", Context.MODE_PRIVATE); }

    static boolean openai(Context c) { return OPENAI.equals(p(c).getString("llm_provider", ANTHROPIC)); }

    static void setProvider(Context c, String v) { p(c).edit().putString("llm_provider", v).apply(); }

    static String base(Context c) { return p(c).getString("openai_base", DEF_BASE); }

    static String cheap(Context c) { return p(c).getString("openai_cheap", DEF_CHEAP); }

    static String vision(Context c) { return p(c).getString("openai_vision", DEF_VISION); }

    static void save(Context c, String base, String cheap, String vision) {
        p(c).edit().putString("openai_base", base.trim().isEmpty() ? DEF_BASE : base.trim())
                .putString("openai_cheap", cheap.trim().isEmpty() ? DEF_CHEAP : cheap.trim())
                .putString("openai_vision", vision.trim().isEmpty() ? DEF_VISION : vision.trim()).apply();
    }

    static String keyFile(Context c) { return openai(c) ? "openai_key" : "claude_key"; }
}
