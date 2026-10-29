package com.phoneai.overlay;

import android.content.Context;

import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;

/** 有公開餘額查詢的服務：OpenRouter（GET /key）、DeepSeek（GET /user/balance）。其他服務（Gemini、OpenAI、Groq、Claude）一般 Key 查不到，要到官網看。 */
final class LlmBalance {
    private LlmBalance() { }

    static String query(Context c) {
        StringBuilder sb = new StringBuilder();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (LlmConfig.Slot s : new LlmConfig.Slot[]{LlmConfig.cheap(c), LlmConfig.vision(c)}) {
            if (!s.openai()) continue;
            String h = LlmConfig.host(s.base);
            if (h == null || !seen.add(h)) continue;
            String key = LlmConfig.readKey(c, s);
            try {
                if (h.contains("openrouter")) sb.append("OpenRouter：").append(openRouter(s.base, key)).append('\n');
                else if (h.contains("deepseek")) sb.append("DeepSeek：").append(deepSeek(s.base, key)).append('\n');
            } catch (Exception e) {
                sb.append(h).append(L.t("：查詢失敗 ")).append(e.getMessage()).append('\n');
            }
        }
        return sb.length() == 0 ? L.t("目前的服務沒有可查的餘額，請到官網用量頁看。") : sb.toString().trim();
    }

    private static String get(String url, String key) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(15000);
        con.setReadTimeout(20000);
        con.setRequestProperty("Authorization", "Bearer " + key);
        int code = con.getResponseCode();
        java.io.InputStream in = code < 400 ? con.getInputStream() : con.getErrorStream();
        String text = in == null ? "" : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        if (code >= 400) throw new Exception("HTTP " + code);
        return text;
    }

    private static String root(String base) {
        String b = base.trim();
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        return b;
    }

    static String openRouter(String base, String key) throws Exception {
        return parseOpenRouter(get(root(base) + "/key", key));
    }

    static String parseOpenRouter(String text) throws Exception {
        JSONObject d = new JSONObject(text).getJSONObject("data");
        String used = String.format(java.util.Locale.US, "%.4f", d.optDouble("usage"));
        if (d.isNull("limit") || !d.has("limit")) return L.t("已用 $") + used + L.t("（這把 Key 沒有設上限）");
        return L.t("已用 $") + used + L.t("，剩 $") + String.format(java.util.Locale.US, "%.4f", d.optDouble("limit_remaining", d.optDouble("limit") - d.optDouble("usage")));
    }

    static String deepSeek(String base, String key) throws Exception {
        String r = root(base);
        if (r.endsWith("/v1")) r = r.substring(0, r.length() - 3);
        return parseDeepSeek(get(r + "/user/balance", key));
    }

    static String parseDeepSeek(String text) throws Exception {
        org.json.JSONArray infos = new JSONObject(text).optJSONArray("balance_infos");
        if (infos == null || infos.length() == 0) return L.t("沒有餘額資料");
        StringBuilder sb = new StringBuilder(L.t("餘額 "));
        for (int i = 0; i < infos.length(); i++) {
            JSONObject b = infos.getJSONObject(i);
            sb.append(i > 0 ? "、" : "").append(b.optString("total_balance")).append(' ').append(b.optString("currency"));
        }
        return sb.toString();
    }
}
