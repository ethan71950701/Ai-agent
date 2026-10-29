package com.phoneai.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class LlmAdapterTest {

    @Test
    public void mapsClaudeModelsToTheConfiguredOnes() {
        assertEquals("mini", LlmAdapter.mapModel("claude-haiku-5-5", "mini", "big"));
        assertEquals("big", LlmAdapter.mapModel("claude-sonnet-5-5", "mini", "big"));
        assertEquals("other", LlmAdapter.mapModel("other", "mini", "big"));
    }

    @Test
    public void convertsSystemImageAndText() throws Exception {
        JSONObject body = new JSONObject("{\"model\":\"claude-sonnet-5-5\",\"max_tokens\":800,"
                + "\"system\":[{\"type\":\"text\",\"text\":\"SYS\",\"cache_control\":{\"type\":\"ephemeral\"}}],"
                + "\"messages\":[{\"role\":\"user\",\"content\":[{\"type\":\"image\",\"source\":{\"type\":\"base64\",\"media_type\":\"image/jpeg\",\"data\":\"AAA\"}},{\"type\":\"text\",\"text\":\"hi\"}]}]}");
        JSONObject out = LlmAdapter.toOpenAi(body, "mini", "big", false);
        assertEquals("big", out.getString("model"));
        assertEquals(800, out.getInt("max_tokens"));
        JSONArray m = out.getJSONArray("messages");
        assertEquals("system", m.getJSONObject(0).getString("role"));
        assertEquals("SYS", m.getJSONObject(0).getString("content"));
        JSONArray parts = m.getJSONObject(1).getJSONArray("content");
        assertEquals("image_url", parts.getJSONObject(0).getString("type"));
        assertEquals("data:image/jpeg;base64,AAA", parts.getJSONObject(0).getJSONObject("image_url").getString("url"));
        assertEquals("hi", parts.getJSONObject(1).getString("text"));
        assertTrue(LlmAdapter.toOpenAi(body, "mini", "big", true).has("max_completion_tokens"));
    }

    @Test
    public void convertsToolsAndToolResults() throws Exception {
        JSONObject body = new JSONObject("{\"model\":\"claude-haiku-5-5\",\"max_tokens\":100,\"system\":\"S\","
                + "\"tools\":[{\"name\":\"get_time\",\"description\":\"d\",\"input_schema\":{\"type\":\"object\",\"properties\":{}}},{\"type\":\"web_search_20250305\",\"name\":\"web_search\"}],"
                + "\"messages\":[{\"role\":\"user\",\"content\":\"q\"},"
                + "{\"role\":\"assistant\",\"content\":[{\"type\":\"tool_use\",\"id\":\"t1\",\"name\":\"get_time\",\"input\":{}}]},"
                + "{\"role\":\"user\",\"content\":[{\"type\":\"tool_result\",\"tool_use_id\":\"t1\",\"content\":\"12:00\"}]}]}");
        JSONObject out = LlmAdapter.toOpenAi(body, "mini", "big", false);
        assertEquals("mini", out.getString("model"));
        assertEquals(1, out.getJSONArray("tools").length());   // 伺服器工具被濾掉
        assertEquals("get_time", out.getJSONArray("tools").getJSONObject(0).getJSONObject("function").getString("name"));
        JSONArray m = out.getJSONArray("messages");
        JSONObject asst = m.getJSONObject(2);
        assertEquals("t1", asst.getJSONArray("tool_calls").getJSONObject(0).getString("id"));
        assertTrue(asst.isNull("content"));
        JSONObject tool = m.getJSONObject(3);
        assertEquals("tool", tool.getString("role"));
        assertEquals("t1", tool.getString("tool_call_id"));
        assertEquals("12:00", tool.getString("content"));
    }

    @Test
    public void convertsResponseBackToClaudeShape() throws Exception {
        JSONObject r = new JSONObject("{\"choices\":[{\"finish_reason\":\"tool_calls\",\"message\":{\"content\":null,\"tool_calls\":"
                + "[{\"id\":\"c1\",\"type\":\"function\",\"function\":{\"name\":\"set_alarm\",\"arguments\":\"{\\\"hour\\\":7}\"}}]}}],"
                + "\"usage\":{\"prompt_tokens\":100,\"completion_tokens\":20,\"prompt_tokens_details\":{\"cached_tokens\":40}}}");
        JSONObject out = LlmAdapter.fromOpenAi(r);
        assertEquals("tool_use", out.getString("stop_reason"));
        JSONObject b = out.getJSONArray("content").getJSONObject(0);
        assertEquals("tool_use", b.getString("type"));
        assertEquals(7, b.getJSONObject("input").getInt("hour"));
        assertEquals(60, out.getJSONObject("usage").getInt("input_tokens"));
        assertEquals(40, out.getJSONObject("usage").getInt("cache_read_input_tokens"));

        JSONObject t = LlmAdapter.fromOpenAi(new JSONObject("{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"abc\"}}]}"));
        assertEquals("max_tokens", t.getString("stop_reason"));
        assertEquals("abc", t.getJSONArray("content").getJSONObject(0).getString("text"));
    }

    @Test
    public void parsesModelListsAndStripsGeminiPrefix() throws Exception {
        java.util.List<String> ids = AiServiceUi.parseModelIds("{\"data\":[{\"id\":\"models/gemini-flash\"},{\"id\":\"b\"},{\"id\":\"b\"}]}");
        assertEquals(java.util.Arrays.asList("b", "gemini-flash"), ids);
    }

    @Test
    public void buildsTheEndpoint() {
        assertEquals("https://x.com/v1/chat/completions", LlmAdapter.endpoint("https://x.com/v1/"));
        assertEquals("https://x.com/v1/chat/completions", LlmAdapter.endpoint("https://x.com/v1/chat/completions"));
    }
}
