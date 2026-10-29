package com.phoneai.overlay;

/**
 * 常見的 OpenAI 相容服務預設值與申請 Key 的步驟。模型名稱是預設值，服務商改名時使用者可以在下一步修改；
 * 步驟文字都用 L.t 顯示，英文在 LEn。「其他」讓使用者自己填。
 */
final class LlmPresets {
    private LlmPresets() { }

    static final class Preset {
        final String name, base, cheap, vision, keyUrl;
        final String[] steps;
        Preset(String name, String base, String cheap, String vision, String keyUrl, String... steps) {
            this.name = name; this.base = base; this.cheap = cheap; this.vision = vision; this.keyUrl = keyUrl; this.steps = steps;
        }
    }

    static final Preset[] ALL = {
            new Preset("Google Gemini（有免費額度）", "https://generativelanguage.googleapis.com/v1beta/openai/", "gemini-flash-lite-latest", "gemini-flash-latest",
                    "https://aistudio.google.com/apikey",
                    "用 Google 帳號登入 Google AI Studio（按下面「開啟申請頁面」）。",
                    "按「Create API key」（建立 API 金鑰），選一個專案。",
                    "複製產生的 Key（AIza 開頭），回到這裡按「下一步」，再貼上。",
                    "免費方案有每分鐘與每天的次數上限，用完會顯示 429，等一下再試。"),
            new Preset("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini", "gpt-4o",
                    "https://platform.openai.com/api-keys",
                    "登入 OpenAI Platform（按下面「開啟申請頁面」），到 Billing 先儲值（預付制）。",
                    "到 API keys 按「Create new secret key」。",
                    "複製 Key（sk- 開頭，只會顯示一次），回到這裡按「下一步」，再貼上。"),
            new Preset("Groq（有免費額度）", "https://api.groq.com/openai/v1", "llama-3.1-8b-instant", "meta-llama/llama-4-scout-17b-16e-instruct",
                    "https://console.groq.com/keys",
                    "登入 Groq Console（按下面「開啟申請頁面」）。",
                    "到 API Keys 按「Create API Key」。",
                    "複製 Key（gsk_ 開頭，只會顯示一次），回到這裡按「下一步」，再貼上。",
                    "免費方案每個模型有次數上限；看圖模型可能已改名，到下一步可以改。"),
            new Preset("OpenRouter（多家模型、有免費模型）", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini", "openai/gpt-4o",
                    "https://openrouter.ai/keys",
                    "登入 OpenRouter（按下面「開啟申請頁面」）。",
                    "到 Keys 按「Create Key」。",
                    "複製 Key（sk-or- 開頭），回到這裡按「下一步」，再貼上。",
                    "想用免費模型：到下一步把模型改成名稱結尾是 :free 的（要自己確認它支援看圖與工具）。"),
            new Preset("DeepSeek（便宜，但沒有看圖）", "https://api.deepseek.com/v1", "deepseek-chat", "deepseek-chat",
                    "https://platform.deepseek.com/api_keys",
                    "登入 DeepSeek Platform（按下面「開啟申請頁面」），先儲值。",
                    "到 API keys 按「Create new API key」。",
                    "複製 Key，回到這裡按「下一步」，再貼上。",
                    "DeepSeek 不能看圖，只能用元件清單模式（設定裡保持開啟），截圖模式會失敗。"),
            new Preset("其他（自己填網址）", "", "", "", "",
                    "任何 OpenAI 相容的服務都可以（例如電腦上跑的 Ollama、LM Studio，或別的雲端服務）。",
                    "到那個服務的網站申請 API Key；本機服務通常不需要 Key，隨便填一串 20 字以上的文字。",
                    "回到這裡按「下一步」，填網址（到 /v1 為止）和兩個模型名稱。")
    };
}
