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
    public void parsesBalanceResponses() throws Exception {
        assertEquals("餘額 12.50 USD", LlmBalance.parseDeepSeek("{\"balance_infos\":[{\"currency\":\"USD\",\"total_balance\":\"12.50\"}]}"));
        assertTrue(LlmBalance.parseOpenRouter("{\"data\":{\"usage\":0.25,\"limit\":1.0,\"limit_remaining\":0.75}}").contains("0.7500"));
        assertTrue(LlmBalance.parseOpenRouter("{\"data\":{\"usage\":0.25,\"limit\":null}}").contains("0.2500"));
    }

    @Test
    public void estimatesCostsFromTheOfficialPriceTables() {
        double[] haiku = Usage.guessPrice("claude-haiku-5-5");
        assertEquals(0.10, haiku[0], 1e-9);
        assertEquals(0.01, haiku[3], 1e-9);
        assertEquals(0.10, Usage.guessPrice("claude-sonnet-5-5")[3], 1e-9);   // Sonnet 5.5 的快取讀取是 $0.10（輸入價的 0.05 倍）
        assertEquals(0.30, Usage.guessPrice("deepseek-flash")[0], 1e-9);
        assertEquals(0.30, Usage.guessPrice("gemini-flash-lite-latest")[0], 1e-9);
        assertEquals(2.50, Usage.guessPrice("gemini-3.5-flash-lite")[1], 1e-9);
        assertEquals(0.025, Usage.guessPrice("gemini-3.1-flash-lite")[3], 1e-9);
        assertEquals(0.10, Usage.guessPrice("gemini-2.5-flash-lite")[0], 1e-9);
        assertEquals(0.50, Usage.guessPrice("gemini-3-flash-preview")[0], 1e-9);
        assertEquals(0.15, Usage.guessPrice("gpt-4o-mini")[0], 1e-9);
        assertEquals(1.00, Usage.guessPrice("gpt-6.1-terra")[0], 1e-9);
        assertEquals(0.01, Usage.guessPrice("gpt-6-luna")[3], 1e-9);
        assertEquals(50.00, Usage.guessPrice("gpt-6-astra")[1], 1e-9);
        assertEquals(10.00, Usage.guessPrice("gpt-6.1-sol")[1], 1e-9);
        assertEquals(0.0, Usage.guessPrice("some-unknown-model")[0], 1e-9);
        // Sonnet 5.5：1M 一般輸入 + 1M 輸出 = 2 + 10
        assertEquals(12.0, Usage.costOf("claude-sonnet-5-5", 1_000_000, 1_000_000, 0, 0), 1e-9);
        // 1M 輸入裡有 600K 快取讀取 + 400K 一般：0.4*2 + 0.6*0.10
        assertEquals(0.8 + 0.06, Usage.costOf("claude-sonnet-5-5", 1_000_000, 0, 0, 600_000), 1e-9);
    }

    @Test
    public void rewritesShellCommandsForTheBackgroundDisplay() {
        assertEquals("input -d 7 tap 10 20", BgDisplay.rewrite("input tap 10 20", 7));
        assertEquals("input -d 7 tap 1 2; input -d 7 tap 1 2", BgDisplay.rewrite("input tap 1 2; input tap 1 2", 7));
        assertEquals("input -d 7 keyevent 4", BgDisplay.rewrite("input keyevent 4", 7));
        assertEquals("input keyevent 24", BgDisplay.rewrite("input keyevent 24", 7));   // 音量鍵留在主螢幕
        assertEquals("input -d 7 swipe 1 2 3 4 300", BgDisplay.rewrite("input swipe 1 2 3 4 300", 7));
        assertTrue(BgDisplay.rewrite("monkey -p com.android.settings -c android.intent.category.LAUNCHER 1", 7).contains("am start --display 7 -n"));
        assertTrue(BgDisplay.rewrite("am start -a android.intent.action.VIEW -d 'https://x'", 7).startsWith("am start --display 7 -a"));
        assertEquals("settings put system a 1", BgDisplay.rewrite("settings put system a 1", 7));
    }

    @Test
    public void parsesBackgroundDisplayOutput() {
        assertEquals(12, BgDisplay.parseDisplayLine("display 12"));
        assertEquals(-1, BgDisplay.parseDisplayLine("registered"));
        java.util.Set<String> ids = BgDisplay.sfIds("Display 4619827259835644672 (Physical display)\nDisplay 1234 (Virtual display)\n");
        assertEquals(1, ids.size());
        assertTrue(ids.contains("1234"));
    }

    @Test
    public void explainsHttpErrors() {
        String e429 = LlmErrors.explain(429, "{\"error\":{\"message\":\"quota\"}}", "gemini");
        assertTrue(e429.startsWith("gemini：HTTP 429"));
        assertTrue(e429.contains("額度用完了"));
        assertTrue(e429.contains("quota"));
        assertTrue(LlmErrors.explain(401, "", "").contains("Key"));
        assertTrue(LlmErrors.explain(503, "", "x").contains("伺服器"));
    }

    @Test
    public void summarizesAbTestResults() {
        java.util.List<AbTest.Result> rs = java.util.Arrays.asList(
                new AbTest.Result("A", "t1", true, 3, 10_000, 0.001, ""),
                new AbTest.Result("A", "t2", false, 5, 20_000, 0.003, "why"),
                new AbTest.Result("B", "t1", true, 2, 8_000, 0.0005, ""));
        String s = AbTest.summarize(rs);
        assertTrue(s.contains("■ A"));
        assertTrue(s.contains("成功 1／2"));
        assertTrue(s.contains("✗ t2"));
        assertTrue(s.contains("■ B"));
    }

    @Test
    public void abTestSetsAreWellFormed() {
        for (AbTest.Task[] set : new AbTest.Task[][]{AbTest.EASY, AbTest.HARD})
            for (AbTest.Task k : set) {
                assertTrue(k.task.length() > 5);
                assertTrue("每題都要有判斷成功的依據：" + k.name, k.mustSee != null || k.mustPkg != null || k.mustReply != null);
            }
    }

    @Test
    public void detectsUnsentMessagesAndSensitiveCategories() throws Exception {
        java.util.List<org.json.JSONObject> tree = java.util.Arrays.asList(
                new org.json.JSONObject().put("n", 1).put("k", "輸入框").put("t", "123"),
                new org.json.JSONObject().put("n", 2).put("k", "文字").put("t", "謝小明"));
        assertEquals("輸入框", AgentLoop.pickKind(tree, new org.json.JSONObject().put("action", "tap").put("pick", 1)));
        assertEquals("文字", AgentLoop.pickKind(tree, new org.json.JSONObject().put("action", "tap").put("pick", 2)));
        assertEquals(null, AgentLoop.pickKind(tree, new org.json.JSONObject().put("action", "tap").put("x", 5)));
        assertTrue(!AgentLoop.messageVisible(tree, "123"));   // 只在輸入框裡：還沒送出
        assertTrue(AgentLoop.messageVisible(java.util.Arrays.asList(new org.json.JSONObject().put("n", 3).put("k", "文字").put("t", "123")), "123"));
        assertTrue(AgentLoop.stillInInput(tree, "123"));
        assertTrue(!AgentLoop.stillInInput(tree, "456"));
        assertTrue(!AgentLoop.stillInInput(java.util.Arrays.asList(new org.json.JSONObject().put("n", 2).put("k", "文字").put("t", "123")), "123"));   // 在對話泡泡裡＝已送出
        assertTrue(AgentLoop.isMessageTask("去ig傳訊息給謝小明「123」"));
        assertTrue(AgentLoop.isMessageTask("去ig傳456給謝小明"));
        assertTrue(AgentLoop.isMessageTask("跟小明說我晚點到"));
        assertTrue(!AgentLoop.isMessageTask("打電話給小明"));
        assertTrue(AgentLoop.isStatusLabel("謝小明, 17分鐘前傳送"));
        assertTrue(AgentLoop.isStatusLabel("謝小明, 3分鐘前看過"));
        assertTrue(!AgentLoop.isStatusLabel("傳送"));
        assertEquals(null, AgentLoop.sensitiveLabel(new org.json.JSONObject().put("action", "tap").put("label", "謝小明, 17分鐘前傳送"), "打開設定"));
        assertTrue(!AgentLoop.isMessageTask("打開設定，在搜尋欄輸入「藍牙」"));
        assertEquals("傳送訊息", AgentLoop.sensitiveCategory("傳送訊息屬於敏感操作，需要使用者確認"));
        assertTrue(AgentLoop.sendButtonLesson("Instagram", 930, 960).contains("x=930"));
        org.json.JSONObject sendTap = new org.json.JSONObject().put("action", "tap").put("label", "傳送");
        assertEquals(null, AgentLoop.sensitiveLabel(sendTap, "去ig傳訊息給謝小明「123」"));   // 傳訊息任務點傳送鈕不用再問
        assertEquals("傳送", AgentLoop.sensitiveLabel(sendTap, "打開設定頁面"));
        assertEquals("付款轉帳", AgentLoop.sensitiveCategory("這一步要付款"));
        assertEquals(null, AgentLoop.sensitiveCategory("需要你確認"));
    }

    @Test
    public void macrosOnlyRecordCleanPaths() throws Exception {
        java.util.List<org.json.JSONObject> clean = java.util.Arrays.asList(
                new org.json.JSONObject().put("a", "open"), new org.json.JSONObject().put("a", "tap"), new org.json.JSONObject().put("a", "tap"));
        assertTrue(Macros.isClean(clean));
        java.util.List<org.json.JSONObject> detour = java.util.Arrays.asList(
                new org.json.JSONObject().put("a", "tap"), new org.json.JSONObject().put("a", "back"), new org.json.JSONObject().put("a", "tap"));
        assertTrue(!Macros.isClean(detour));
        assertEquals("中途有返回（走錯路退回來）", Macros.uncleanWhy(detour));
        java.util.List<org.json.JSONObject> scrolly = java.util.Arrays.asList(
                new org.json.JSONObject().put("a", "scroll"), new org.json.JSONObject().put("a", "scroll"), new org.json.JSONObject().put("a", "scroll"));
        assertTrue(!Macros.isClean(scrolly));
        assertEquals(null, Macros.uncleanWhy(clean));
    }

    @Test
    public void buildsTheEndpoint() {
        assertEquals("https://x.com/v1/chat/completions", LlmAdapter.endpoint("https://x.com/v1/"));
        assertEquals("https://x.com/v1/chat/completions", LlmAdapter.endpoint("https://x.com/v1/chat/completions"));
    }
}
