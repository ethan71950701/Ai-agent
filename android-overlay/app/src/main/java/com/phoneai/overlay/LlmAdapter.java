package com.phoneai.overlay;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 讓 App 也能用 OpenAI 相容的 API（OpenAI、DeepSeek、Groq、OpenRouter、Ollama、LM Studio…）。
 * App 內部一律用 Claude Messages 的格式組請求、讀回應；這裡把請求轉成 chat/completions 的格式、把回應轉回 Claude 的格式，
 * 所以路由、聊天、自動操作的程式都不用改。純邏輯，不碰網路與 Android，可以單元測試。
 */
final class LlmAdapter {
    private LlmAdapter() { }

    /** 模型對應：Claude 的 Haiku 級（便宜、文字）→ cheap；其餘（Sonnet 級，看截圖）→ vision；已經是別家名字就原樣。 */
    static String mapModel(String model, String cheap, String vision) {
        if (model == null) return vision;
        if (model.startsWith("claude-")) return model.contains("haiku") ? cheap : vision;
        return model;
    }

    /** Claude 格式的請求 → chat/completions 請求。官方 OpenAI 的新模型要用 max_completion_tokens，其他相容服務用 max_tokens。 */
    static JSONObject toOpenAi(JSONObject body, String cheap, String vision, boolean officialOpenAi) throws Exception {
        JSONObject out = new JSONObject();
        out.put("model", mapModel(body.optString("model"), cheap, vision));
        out.put(officialOpenAi ? "max_completion_tokens" : "max_tokens", body.optInt("max_tokens", 1000));
        JSONArray msgs = new JSONArray();
        Object sys = body.opt("system");
        String sysText = sys instanceof JSONArray ? textOf((JSONArray) sys) : sys == null ? "" : String.valueOf(sys);
        if (!sysText.isEmpty()) msgs.put(new JSONObject().put("role", "system").put("content", sysText));
        JSONArray src = body.optJSONArray("messages");
        for (int i = 0; src != null && i < src.length(); i++) {
            JSONObject m = src.getJSONObject(i);
            String role = m.optString("role");
            Object c = m.opt("content");
            if (!(c instanceof JSONArray)) {
                msgs.put(new JSONObject().put("role", role).put("content", c == null ? "" : String.valueOf(c)));
                continue;
            }
            JSONArray blocks = (JSONArray) c;
            if ("assistant".equals(role)) {
                JSONObject am = new JSONObject().put("role", "assistant");
                JSONArray calls = new JSONArray();
                StringBuilder text = new StringBuilder();
                for (int k = 0; k < blocks.length(); k++) {
                    JSONObject b = blocks.getJSONObject(k);
                    if ("text".equals(b.optString("type"))) text.append(b.optString("text"));
                    else if ("tool_use".equals(b.optString("type"))) {
                        JSONObject in = b.optJSONObject("input");
                        JSONObject call = new JSONObject().put("id", b.optString("id")).put("type", "function")
                                .put("function", new JSONObject().put("name", b.optString("name")).put("arguments", in == null ? "{}" : in.toString()));
                        if (b.optJSONObject("extra_content") != null) call.put("extra_content", b.getJSONObject("extra_content"));   // Gemini 3 的 thought_signature：回傳工具結果時要原樣帶回，不然會 400
                        calls.put(call);
                    }
                }
                am.put("content", text.length() == 0 && calls.length() > 0 ? JSONObject.NULL : text.toString());
                if (calls.length() > 0) am.put("tool_calls", calls);
                msgs.put(am);
            } else {
                JSONArray parts = new JSONArray();
                for (int k = 0; k < blocks.length(); k++) {
                    JSONObject b = blocks.getJSONObject(k);
                    String t = b.optString("type");
                    if ("tool_result".equals(t)) {   // 工具結果在 OpenAI 是獨立的 tool 訊息
                        msgs.put(new JSONObject().put("role", "tool").put("tool_call_id", b.optString("tool_use_id")).put("content", String.valueOf(b.opt("content"))));
                    } else if ("text".equals(t)) {
                        parts.put(new JSONObject().put("type", "text").put("text", b.optString("text")));
                    } else if ("image".equals(t)) {
                        JSONObject s = b.optJSONObject("source");
                        if (s != null) parts.put(new JSONObject().put("type", "image_url").put("image_url",
                                new JSONObject().put("url", "data:" + s.optString("media_type", "image/jpeg") + ";base64," + s.optString("data"))));
                    }
                }
                if (parts.length() > 0) msgs.put(new JSONObject().put("role", role).put("content", parts));
            }
        }
        out.put("messages", msgs);
        JSONArray tools = body.optJSONArray("tools");
        JSONArray fns = new JSONArray();
        for (int i = 0; tools != null && i < tools.length(); i++) {
            JSONObject t = tools.getJSONObject(i);
            if (!t.has("input_schema")) continue;   // Claude 專屬的伺服器工具（網頁搜尋）別家沒有
            JSONObject schema = t.getJSONObject("input_schema");
            JSONObject fn = new JSONObject().put("name", t.optString("name")).put("description", t.optString("description"));
            JSONObject props = schema.optJSONObject("properties");
            if (props != null && props.length() > 0) fn.put("parameters", schema);   // 沒有參數的工具不送空的 properties（Gemini 會拒絕）
            fns.put(new JSONObject().put("type", "function").put("function", fn));
        }
        if (fns.length() > 0) out.put("tools", fns);
        return out;
    }

    /** chat/completions 回應 → Claude 格式（content、stop_reason、usage），其他程式照舊讀。 */
    static JSONObject fromOpenAi(JSONObject r) throws Exception {
        JSONObject choice = r.optJSONArray("choices") == null || r.getJSONArray("choices").length() == 0 ? new JSONObject() : r.getJSONArray("choices").getJSONObject(0);
        JSONObject msg = choice.optJSONObject("message") == null ? new JSONObject() : choice.getJSONObject("message");
        JSONArray content = new JSONArray();
        String text = msg.isNull("content") ? "" : msg.optString("content");
        if (!text.isEmpty()) content.put(new JSONObject().put("type", "text").put("text", text));
        JSONArray calls = msg.optJSONArray("tool_calls");
        for (int i = 0; calls != null && i < calls.length(); i++) {
            JSONObject f = calls.getJSONObject(i).optJSONObject("function");
            if (f == null) continue;
            JSONObject input;
            try { input = new JSONObject(f.optString("arguments", "{}")); } catch (Exception e) { input = new JSONObject(); }
            JSONObject tu = new JSONObject().put("type", "tool_use").put("id", calls.getJSONObject(i).optString("id")).put("name", f.optString("name")).put("input", input);
            if (calls.getJSONObject(i).optJSONObject("extra_content") != null) tu.put("extra_content", calls.getJSONObject(i).getJSONObject("extra_content"));
            content.put(tu);
        }
        String fr = choice.optString("finish_reason");
        String stop = content.length() > 0 && calls != null && calls.length() > 0 ? "tool_use" : "length".equals(fr) ? "max_tokens" : "end_turn";
        JSONObject u = r.optJSONObject("usage");
        long prompt = u == null ? 0 : u.optLong("prompt_tokens"), out = u == null ? 0 : u.optLong("completion_tokens");
        JSONObject det = u == null ? null : u.optJSONObject("prompt_tokens_details");
        long cached = det == null ? 0 : det.optLong("cached_tokens");
        JSONObject usage = new JSONObject().put("input_tokens", Math.max(0, prompt - cached)).put("output_tokens", out)
                .put("cache_creation_input_tokens", 0).put("cache_read_input_tokens", cached);
        return new JSONObject().put("content", content).put("stop_reason", stop).put("usage", usage);
    }

    private static String textOf(JSONArray blocks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < blocks.length(); i++) {
            JSONObject b = blocks.optJSONObject(i);
            if (b != null && "text".equals(b.optString("type"))) sb.append(b.optString("text"));
        }
        return sb.toString();
    }

    /** chat/completions 的網址：使用者填到 /v1 或整段都行。 */
    static String endpoint(String base) {
        String b = base == null ? "" : base.trim();
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        return b.endsWith("/chat/completions") ? b : b + "/chat/completions";
    }
}
