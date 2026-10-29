package com.phoneai.overlay;

/**
 * 常見的 OpenAI 相容服務預設值與申請 Key 的步驟。模型名稱是預設值，服務商改名時使用者可以在下一步修改；
 * 步驟文字都用 L.t 顯示，英文在 LEn。「其他」讓使用者自己填。
 */
final class LlmPresets {
    private LlmPresets() { }

    static final class Preset {
        final String name, base, cheap, vision, keyUrl, usageUrl;
        final String[] steps;
        Preset(String name, String base, String cheap, String vision, String keyUrl, String usageUrl, String... steps) {
            this.name = name; this.base = base; this.cheap = cheap; this.vision = vision; this.keyUrl = keyUrl; this.usageUrl = usageUrl; this.steps = steps;
        }

        /** 不含括號說明的短名稱。 */
        String shortName() { int i = name.indexOf('（'); return i < 0 ? name : name.substring(0, i); }
    }

    /** 這個欄位的設定對應到哪個預設服務（依網址主機）；Claude 或自訂網址回 null。 */
    static Preset forSlot(LlmConfig.Slot s) {
        if (!s.openai()) return null;
        String h = LlmConfig.host(s.base);
        for (Preset p : ALL) if (!p.base.isEmpty() && h != null && h.equals(LlmConfig.host(p.base))) return p;
        return null;
    }

    static final Preset[] ALL = {
            new Preset("Google Gemini（有免費額度）", "https://generativelanguage.googleapis.com/v1beta/openai/", "gemini-flash-lite-latest", "gemini-flash-latest",
                    "https://aistudio.google.com/apikey", "https://aistudio.google.com/usage",
                    "用 Google 帳號登入 Google AI Studio（按下面「開啟申請頁面」）。",
                    "按「Create API key」（建立 API 金鑰），選一個專案。",
                    "複製產生的 Key（以 AIza 或 AQ. 開頭），回到這裡按「下一步」，再貼上。",
                    "免費方案有每分鐘與每天的次數上限，用完會顯示 429，等一下再試。"),
            new Preset("OpenAI", "https://api.openai.com/v1", "gpt-6-luna", "gpt-6.1-terra",
                    "https://platform.openai.com/api-keys", "https://platform.openai.com/usage",
                    "登入 OpenAI Platform（按下面「開啟申請頁面」），到 Billing 先儲值（預付制）。",
                    "到 API keys 按「Create new secret key」。",
                    "複製 Key（sk- 開頭，只會顯示一次），回到這裡按「下一步」，再貼上。",
                    "預設便宜模型用 GPT-6 Luna（最省）、看圖模型用 GPT-6.1 Terra（官網寫擅長視覺推理與電腦操作）。模型代號是我猜的，Key 存好後按「查詢」從清單選最準。"),
            new Preset("Groq（有免費額度）", "https://api.groq.com/openai/v1", "llama-3.1-8b-instant", "meta-llama/llama-4-scout-17b-16e-instruct",
                    "https://console.groq.com/keys", "https://console.groq.com/settings/usage",
                    "登入 Groq Console（按下面「開啟申請頁面」）。",
                    "到 API Keys 按「Create API Key」。",
                    "複製 Key（gsk_ 開頭，只會顯示一次），回到這裡按「下一步」，再貼上。",
                    "免費方案每個模型有次數上限。Groq 的看圖模型常改名甚至下架：建議便宜模型用 Groq，看圖模型到「只設定看圖模型」改選 Gemini。"),
            new Preset("OpenRouter（多家模型、有免費模型）", "https://openrouter.ai/api/v1", "openrouter/free", "openrouter/free",
                    "https://openrouter.ai/keys", "https://openrouter.ai/activity",
                    "登入 OpenRouter（按下面「開啟申請頁面」）。",
                    "到 Keys 按「Create Key」。",
                    "複製 Key（sk-or- 開頭），回到這裡按「下一步」，再貼上。",
                    "預設用 openrouter/free：它會自動從免費模型裡挑一個支援你這次請求（看圖、工具）的，但每次可能換不同模型，穩定度不一；想固定用某個，改成名稱結尾是 :free 的模型。"),
            new Preset("DeepSeek（便宜）", "https://api.deepseek.com", "deepseek-flash", "deepseek-flash",
                    "https://platform.deepseek.com/api_keys", "https://platform.deepseek.com/usage",
                    "登入 DeepSeek Platform（按下面「開啟申請頁面」），先儲值。",
                    "到 API keys 按「Create new API key」。",
                    "複製 Key，回到這裡按「下一步」，再貼上。",
                    "官網寫 deepseek-flash 支援看圖（deepseek-v4-pro 不支援），這裡便宜與看圖都預設用它；看圖是否穩定還沒實測，不穩就用「只設定看圖模型」改選 Gemini 或 OpenAI。"),
            new Preset("其他（自己填網址）", "", "", "", "", "",
                    "任何 OpenAI 相容的服務都可以（例如電腦上跑的 Ollama、LM Studio，或別的雲端服務）。",
                    "到那個服務的網站申請 API Key；本機服務通常不需要 Key，隨便填一串 20 字以上的文字。",
                    "回到這裡按「下一步」，填網址（到 /v1 為止）和兩個模型名稱。")
    };
}
