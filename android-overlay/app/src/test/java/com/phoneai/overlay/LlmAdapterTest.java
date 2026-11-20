package com.phoneai.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

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
        java.util.List<org.json.JSONObject> oneScroll = java.util.Arrays.asList(
                new org.json.JSONObject().put("a", "tap"), new org.json.JSONObject().put("a", "scroll"), new org.json.JSONObject().put("a", "tap"));
        assertTrue(!Macros.isClean(oneScroll));   // 連一次捲動都不記
        assertEquals(null, Macros.uncleanWhy(clean));
    }

    @Test
    public void buildsTheEndpoint() {
        assertEquals("https://x.com/v1/chat/completions", LlmAdapter.endpoint("https://x.com/v1/"));
        assertEquals("https://x.com/v1/chat/completions", LlmAdapter.endpoint("https://x.com/v1/chat/completions"));
    }

    @Test
    public void keepsGeminiThoughtSignatureOnToolCalls() throws Exception {
        JSONObject r = new JSONObject("{\"choices\":[{\"finish_reason\":\"tool_calls\",\"message\":{\"content\":null,\"tool_calls\":[{\"id\":\"c1\",\"type\":\"function\","
                + "\"extra_content\":{\"google\":{\"thought_signature\":\"sig\"}},\"function\":{\"name\":\"get_time\",\"arguments\":\"{}\"}}]}}]}");
        JSONObject c = LlmAdapter.fromOpenAi(r);
        JSONObject tu = c.getJSONArray("content").getJSONObject(0);
        assertEquals("sig", tu.getJSONObject("extra_content").getJSONObject("google").getString("thought_signature"));
        JSONObject body = new JSONObject().put("model", "x").put("max_tokens", 10).put("messages", new org.json.JSONArray()
                .put(new JSONObject().put("role", "user").put("content", "hi"))
                .put(new JSONObject().put("role", "assistant").put("content", c.getJSONArray("content"))));
        JSONObject out = LlmAdapter.toOpenAi(body, "mini", "big", false);
        JSONObject call = out.getJSONArray("messages").getJSONObject(1).getJSONArray("tool_calls").getJSONObject(0);
        assertEquals("sig", call.getJSONObject("extra_content").getJSONObject("google").getString("thought_signature"));
    }

    @Test
    public void exportRedactsKeys() {
        String r = LogExport.redact("key AIzaSyA1234567890abcdefghijklmnop and sk-abcdefghijklmnopqrstu and Bearer abcdef1234567890 ok");
        assertFalse(r.contains("AIzaSy"));
        assertFalse(r.contains("sk-abc"));
        assertFalse(r.contains("abcdef1234567890"));
        assertTrue(r.contains("ok"));
    }

    @Test
    public void chatSearchOnlyWhenAsked() {
        assertTrue(ChatEngine.wantsSearch("幫我查一下今天的油價"));
        assertTrue(ChatEngine.wantsSearch("搜尋一下這家餐廳的營業時間"));
        assertTrue(ChatEngine.wantsSearch("Please look up the weather in Taipei"));
        assertTrue(ChatEngine.wantsSearch("幫我查關於獅子的相關知識"));
        assertTrue(ChatEngine.wantsSearch("請你查台積電今天的股價"));
        assertFalse(ChatEngine.wantsSearch("幫我檢查一下我的待辦"));
        assertFalse(ChatEngine.wantsSearch("今天天氣怎麼樣"));
        assertFalse(ChatEngine.wantsSearch("我想看查理的電影"));
        assertFalse(ChatEngine.wantsSearch("幫我設鬧鐘"));
    }

    @Test
    public void tavilyDigestAndRedaction() {
        String json = "{\"results\":[{\"title\":\"天氣\",\"url\":\"https://example.com/a\",\"content\":\"今天台北多雲\",\"score\":0.9},"
                + "{\"title\":\"B\",\"url\":\"https://example.com/b\",\"content\":\"x\"}]}";
        String d = TavilySearch.digest(json);
        assertTrue(d.startsWith("1. 天氣\nhttps://example.com/a\n今天台北多雲"));
        assertTrue(d.contains("2. B"));
        assertEquals(null, TavilySearch.digest("{\"results\":[]}"));
        assertEquals(null, TavilySearch.digest("not json"));
        assertFalse(LogExport.redact("key tvly-dev-abcdefghijklmnop1234").contains("tvly-dev"));
    }

    @Test
    public void tavilyQueryTopicAndDomains() {
        assertEquals("今天台北的天氣", TavilySearch.cleanQuery("幫我查一下今天台北的天氣"));
        assertEquals("台積電股價", TavilySearch.cleanQuery("請你搜尋一下台積電股價"));
        assertEquals("weather in Taipei", TavilySearch.cleanQuery("please look up weather in Taipei"));
        assertEquals("查", TavilySearch.cleanQuery("查"));
        assertEquals("news", TavilySearch.topicFor("今天有什麼新聞"));
        assertEquals("general", TavilySearch.topicFor("台北天氣"));
        assertEquals(2, TavilySearch.sources("{\"results\":[{\"title\":\"a\",\"url\":\"https://a.com\"},{\"title\":\"b\",\"url\":\"https://b.com\"},{\"title\":\"c\",\"url\":\"ftp://x\"}]}").size());
    }

    @Test
    public void tavilyPlanAndTrustedSites() {
        java.util.List<String[]> tr = TavilySearch.parseTrusted("https://www.cwa.gov.tw/V8/C 天氣、颱風\nWikipedia.org 一般知識\nnot a domain\n\nexample.com");
        assertEquals(3, tr.size());
        assertEquals("cwa.gov.tw", tr.get(0)[0]);
        assertEquals("天氣、颱風", tr.get(0)[1]);
        assertEquals("wikipedia.org", tr.get(1)[0]);
        assertEquals("", tr.get(2)[1]);
        TavilySearch.Plan p = TavilySearch.parsePlan("好的：{\"query\":\"台北 2026-10-31 天氣\",\"topic\":\"general\",\"domains\":[\"www.cwa.gov.tw\",\"evil.com\"]}", tr);
        assertEquals("台北 2026-10-31 天氣", p.query);
        assertEquals(1, p.domains.size());
        assertEquals("cwa.gov.tw", p.domains.get(0));
        assertEquals("news", TavilySearch.parsePlan("{\"query\":\"最新 AI 新聞\",\"topic\":\"news\",\"domains\":[]}", tr).topic);
        assertEquals(null, TavilySearch.parsePlan("沒有 JSON", tr));
        assertEquals(null, TavilySearch.parsePlan("{\"query\":\"\"}", tr));
    }

    @Test
    public void notificationCodesAreMasked() {
        assertEquals("您的驗證碼是 [已遮蔽]，5 分鐘內有效", NotifListener.redact("您的驗證碼是 482913，5 分鐘內有效"));
        assertEquals("Your verification code is [已遮蔽]", NotifListener.redact("Your verification code is 1234"));
        assertEquals("明天 1530 開會", NotifListener.redact("明天 1530 開會"));   // 沒提到驗證碼：數字不動
        assertEquals("", NotifListener.redact(null));
    }

    private static void pbVarint(java.io.ByteArrayOutputStream o, long v) {
        while (true) { if ((v & ~0x7fL) == 0) { o.write((int) v); return; } o.write((int) ((v & 0x7f) | 0x80)); v >>>= 7; }
    }

    private static void pbField(java.io.ByteArrayOutputStream o, int f, byte[] v) {
        pbVarint(o, (f << 3) | 2); pbVarint(o, v.length); o.write(v, 0, v.length);
    }

    @Test
    public void parsesSamsungNotificationHistory() throws Exception {
        java.io.ByteArrayOutputStream n = new java.io.ByteArrayOutputStream();
        pbField(n, 1, "com.example.chat".getBytes("UTF-8"));
        pbField(n, 3, "訊息".getBytes("UTF-8"));
        pbVarint(n, (7 << 3)); pbVarint(n, 10412);
        pbVarint(n, (9 << 3)); pbVarint(n, 1793845508374L);
        pbField(n, 10, "小明".getBytes("UTF-8"));
        pbField(n, 11, "明天三點開會".getBytes("UTF-8"));
        java.io.ByteArrayOutputStream top = new java.io.ByteArrayOutputStream();
        pbVarint(top, (2 << 3)); pbVarint(top, 1);
        pbField(top, 3, n.toByteArray());
        pbField(top, 3, new byte[]{8, 1});   // 沒有套件與時間的那一則要略過
        java.util.List<NotifHistory.Entry> es = NotifHistory.parse(top.toByteArray());
        assertEquals(1, es.size());
        assertEquals("com.example.chat", es.get(0).pkg);
        assertEquals("小明", es.get(0).title);
        assertEquals("明天三點開會", es.get(0).text);
        assertEquals(1793845508374L, es.get(0).time);
        assertEquals(0, NotifHistory.parse(new byte[]{(byte) 0xff, (byte) 0xff}).size());   // 壞掉的檔案不會丟例外
    }

    @org.junit.Test
    public void endpointMustBeHttpsUnlessLocal() {
        org.junit.Assert.assertTrue(LlmAdapter.isSafeEndpoint("https://api.openai.com/v1"));
        org.junit.Assert.assertTrue(LlmAdapter.isSafeEndpoint("http://localhost:11434/v1"));
        org.junit.Assert.assertTrue(LlmAdapter.isSafeEndpoint("http://192.168.1.5:8080/v1"));
        org.junit.Assert.assertTrue(LlmAdapter.isSafeEndpoint("http://10.0.0.2/v1"));
        org.junit.Assert.assertTrue(LlmAdapter.isSafeEndpoint("http://172.20.1.1/v1"));
        org.junit.Assert.assertTrue(LlmAdapter.isSafeEndpoint("http://127.0.0.1:1234/v1"));
        org.junit.Assert.assertTrue(LlmAdapter.isSafeEndpoint("http://mybox.local/v1"));
        org.junit.Assert.assertFalse(LlmAdapter.isSafeEndpoint("http://api.example.com/v1"));
        org.junit.Assert.assertFalse(LlmAdapter.isSafeEndpoint("http://172.32.0.1/v1"));
        org.junit.Assert.assertFalse(LlmAdapter.isSafeEndpoint("http://localhost.evil.com/v1"));
        org.junit.Assert.assertFalse(LlmAdapter.isSafeEndpoint("ftp://x"));
        org.junit.Assert.assertFalse(LlmAdapter.isSafeEndpoint(""));
        org.junit.Assert.assertFalse(LlmAdapter.isSafeEndpoint(null));
    }

    @org.junit.Test
    public void errorBodiesNeverEchoKeys() {
        String e = LlmErrors.explain(401, "{\"error\":{\"message\":\"Incorrect API key provided: sk-proj-abcdefghijklmnop1234. Bearer abcdefgh12345678\"}}", "OpenAI");
        org.junit.Assert.assertFalse(e, e.contains("abcdefghijklmnop1234"));
        org.junit.Assert.assertFalse(e, e.contains("abcdefgh12345678"));
        org.junit.Assert.assertTrue(e.contains("HTTP 401"));
        String g = LlmErrors.explain(400, "API key not valid. AIzaSyA1234567890abcdefghijklmnopqrstu", "Gemini");
        org.junit.Assert.assertFalse(g, g.contains("AIzaSyA1234567890"));
        org.junit.Assert.assertEquals("", LlmErrors.redact(null));
    }

    private static java.io.InputStream sse(String... events) {
        StringBuilder sb = new StringBuilder();
        for (String e : events) sb.append("event: x\ndata: ").append(e).append("\n\n");
        return new java.io.ByteArrayInputStream(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @org.junit.Test
    public void streamParsesTextAndToolUse() throws Exception {
        org.json.JSONObject r = ChatEngine.readStream(sse(
                "{\"type\":\"message_start\",\"message\":{\"usage\":{\"input_tokens\":10}}}",
                "{\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}",
                "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"你好\"}}",
                "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"，世界\"}}",
                "{\"type\":\"content_block_stop\",\"index\":0}",
                "{\"type\":\"content_block_start\",\"index\":1,\"content_block\":{\"type\":\"tool_use\",\"id\":\"t1\",\"name\":\"get_time\",\"input\":{}}}",
                "{\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"a\\\":\"}}",
                "{\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"1}\"}}",
                "{\"type\":\"content_block_stop\",\"index\":1}",
                "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"tool_use\"},\"usage\":{\"output_tokens\":7}}"));
        org.junit.Assert.assertEquals("tool_use", r.getString("stop_reason"));
        org.json.JSONArray c = r.getJSONArray("content");
        org.junit.Assert.assertEquals("你好，世界", c.getJSONObject(0).getString("text"));
        org.junit.Assert.assertEquals(1, c.getJSONObject(1).getJSONObject("input").getInt("a"));
        org.junit.Assert.assertEquals(10, r.getJSONObject("usage").getInt("input_tokens"));
        org.junit.Assert.assertEquals(7, r.getJSONObject("usage").getInt("output_tokens"));
    }

    @org.junit.Test
    public void streamKeepsThinkingTextAndSignatureForTheNextTurn() throws Exception {
        org.json.JSONObject r = ChatEngine.readStream(sse(
                "{\"type\":\"message_start\",\"message\":{\"usage\":{\"input_tokens\":10}}}",
                "{\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"thinking\",\"thinking\":\"\",\"signature\":\"\"}}",
                "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"先找檔案，\"}}",
                "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"再回答。\"}}",
                "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"signature_delta\",\"signature\":\"EqQBCgIYAhIM-sig\"}}",
                "{\"type\":\"content_block_stop\",\"index\":0}",
                "{\"type\":\"content_block_start\",\"index\":1,\"content_block\":{\"type\":\"tool_use\",\"id\":\"t1\",\"name\":\"file_find\",\"input\":{}}}",
                "{\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"name\\\":\\\"a\\\"}\"}}",
                "{\"type\":\"content_block_stop\",\"index\":1}",
                "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"tool_use\"},\"usage\":{\"output_tokens\":7}}"));
        org.json.JSONArray c = r.getJSONArray("content");
        org.junit.Assert.assertEquals("thinking", c.getJSONObject(0).getString("type"));
        org.junit.Assert.assertEquals("先找檔案，再回答。", c.getJSONObject(0).getString("thinking"));
        org.junit.Assert.assertEquals("EqQBCgIYAhIM-sig", c.getJSONObject(0).getString("signature"));
        org.junit.Assert.assertEquals("a", c.getJSONObject(1).getJSONObject("input").getString("name"));
    }

    @org.junit.Test
    public void emptyOrOmittedThinkingStillHasTheRequiredFields() throws Exception {
        // 思考內容被省略時串流只有簽章、沒有 thinking_delta：區塊仍然要有 thinking（空字串）和 signature，不然下一輪被 400 拒絕
        org.json.JSONObject r = ChatEngine.readStream(sse(
                "{\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"thinking\"}}",
                "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"signature_delta\",\"signature\":\"sigOnly\"}}",
                "{\"type\":\"content_block_stop\",\"index\":0}",
                "{\"type\":\"content_block_start\",\"index\":1,\"content_block\":{\"type\":\"thinking\"}}",
                "{\"type\":\"content_block_stop\",\"index\":1}",
                "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"}}"));
        org.json.JSONArray c = r.getJSONArray("content");
        org.junit.Assert.assertTrue(c.getJSONObject(0).has("thinking"));
        org.junit.Assert.assertEquals("", c.getJSONObject(0).getString("thinking"));
        org.junit.Assert.assertEquals("sigOnly", c.getJSONObject(0).getString("signature"));
        org.junit.Assert.assertTrue(c.getJSONObject(1).has("thinking"));
        org.junit.Assert.assertTrue(c.getJSONObject(1).has("signature"));
    }

    @org.junit.Test
    public void redactedThinkingAndServerToolInputSurvive() throws Exception {
        org.json.JSONObject r = ChatEngine.readStream(sse(
                "{\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"redacted_thinking\",\"data\":\"opaque\"}}",
                "{\"type\":\"content_block_stop\",\"index\":0}",
                "{\"type\":\"content_block_start\",\"index\":1,\"content_block\":{\"type\":\"server_tool_use\",\"id\":\"s1\",\"name\":\"web_search\",\"input\":{}}}",
                "{\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"query\\\":\\\"天氣\\\"}\"}}",
                "{\"type\":\"content_block_stop\",\"index\":1}",
                "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"}}"));
        org.json.JSONArray c = r.getJSONArray("content");
        org.junit.Assert.assertEquals("opaque", c.getJSONObject(0).getString("data"));
        org.junit.Assert.assertEquals("天氣", c.getJSONObject(1).getJSONObject("input").getString("query"));
    }

    @org.junit.Test
    public void onlyTheChatAnswerIsStreamedToTheScreen() {
        org.junit.Assert.assertTrue(ChatEngine.shouldStream(true, true, false, false));
        org.junit.Assert.assertFalse("路由判斷等內部請求不能顯示", ChatEngine.shouldStream(false, true, false, false));
        org.junit.Assert.assertFalse("沒有顯示回呼", ChatEngine.shouldStream(true, false, false, false));
        org.junit.Assert.assertFalse("有搜尋這類伺服器端工具", ChatEngine.shouldStream(true, true, true, false));
        org.junit.Assert.assertFalse("遠端開關關掉", ChatEngine.shouldStream(true, true, false, true));
    }

    @org.junit.Test(expected = java.io.IOException.class)
    public void streamCutBeforeTheEndIsAnErrorNotAHalfAnswer() throws Exception {
        ChatEngine.readStream(sse(
                "{\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}",
                "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"說到一半\"}}"));
    }

    @org.junit.Test
    public void streamErrorEventBecomesAnException() throws Exception {
        try {
            ChatEngine.readStream(sse("{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"Overloaded\"}}"));
            org.junit.Assert.fail("should throw");
        } catch (Exception e) {
            org.junit.Assert.assertTrue(e.getMessage(), e.getMessage().contains("Overloaded"));
        }
    }

    private static final long NOW = 1_800_000_000_000L;   // 2027-01 左右

    @org.junit.Test
    public void announcementShowsOnceUnlessCriticalAndStillOld() {
        java.util.Set<String> none = new java.util.HashSet<>();
        String j = "{\"id\":\"a1\",\"severity\":\"warn\",\"title\":{\"zh\":\"標題\",\"en\":\"Title\"},\"body\":\"內文\"}";
        Announcements.Result r = Announcements.parse(j, 1000, NOW, true, none);
        org.junit.Assert.assertTrue(r.show);
        org.junit.Assert.assertEquals("標題", r.title);
        org.junit.Assert.assertEquals("Title", Announcements.parse(j, 1000, NOW, false, none).title);
        org.junit.Assert.assertTrue(r.offerUpdate);
        java.util.Set<String> seen = new java.util.HashSet<>(java.util.Arrays.asList("a1"));
        org.junit.Assert.assertFalse(Announcements.parse(j, 1000, NOW, true, seen).show);   // 看過了
        String crit = "{\"id\":\"c1\",\"severity\":\"critical\",\"title\":\"t\",\"min_version_below\":1700}";
        org.junit.Assert.assertTrue(Announcements.parse(crit, 1600, NOW, true, new java.util.HashSet<>(java.util.Arrays.asList("c1"))).show);   // 還沒更新：每次都提醒
        org.junit.Assert.assertFalse(Announcements.parse(crit, 1700, NOW, true, none).show);   // 已經更新：不顯示
        org.junit.Assert.assertFalse(Announcements.parse(crit, 1800, NOW, true, none).show);
    }

    @org.junit.Test
    public void announcementEmptyExpiredAndMalformedNeverShow() {
        java.util.Set<String> none = new java.util.HashSet<>();
        org.junit.Assert.assertFalse(Announcements.parse("{\"id\":\"\",\"title\":{\"zh\":\"\"}}", 1, NOW, true, none).show);   // 空檔案
        org.junit.Assert.assertFalse(Announcements.parse("{\"id\":\"x\"}", 1, NOW, true, none).show);                                 // 沒有文字
        org.junit.Assert.assertFalse(Announcements.parse("{\"id\":\"x\",\"title\":\"t\",\"expires\":\"2020-01-01\"}", 1, NOW, true, none).show);
        org.junit.Assert.assertTrue(Announcements.parse("{\"id\":\"x\",\"title\":\"t\",\"expires\":\"2999-01-01\"}", 1, NOW, true, none).show);
        org.junit.Assert.assertNull(Announcements.parse("not json", 1, NOW, true, none));
        org.junit.Assert.assertNull(Announcements.parse("", 1, NOW, true, none));
    }

    @org.junit.Test
    public void announcementOnlyAllowsWhitelistedFlagsAndPlainText() {
        String j = "{\"id\":\"x\",\"title\":\"t\\u0000\\u001b[31m紅\",\"disable\":[\"background_mode\",\"reboot_phone\",\"run_shell\",123]}";
        Announcements.Result r = Announcements.parse(j, 1, NOW, true, new java.util.HashSet<>());
        org.junit.Assert.assertEquals(new java.util.HashSet<>(java.util.Arrays.asList("background_mode", "run_shell")), r.disable);
        org.junit.Assert.assertFalse(r.title.contains("\u0000"));
        org.junit.Assert.assertFalse(r.title.contains("\u001b"));
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 2000; i++) big.append('字');
        Announcements.Result b = Announcements.parse("{\"id\":\"x\",\"body\":\"" + big + "\"}", 1, NOW, true, new java.util.HashSet<>());
        org.junit.Assert.assertTrue(b.body.length() <= 601);
    }

    @org.junit.Test
    public void announcementHistoryKeepsNewestFirstAndCapsAndReplacesSameId() throws Exception {
        String h = "[]";
        for (int i = 1; i <= 5; i++) h = Announcements.mergeHistory(h, "id" + i, "info", "T" + i, "B" + i, i * 1000L, 3);
        org.json.JSONArray a = new org.json.JSONArray(h);
        org.junit.Assert.assertEquals(3, a.length());
        org.junit.Assert.assertEquals("id5", a.getJSONObject(0).getString("id"));
        org.junit.Assert.assertEquals("id3", a.getJSONObject(2).getString("id"));
        h = Announcements.mergeHistory(h, "id4", "warn", "T4b", "B4b", 9000L, 3);
        a = new org.json.JSONArray(h);
        org.junit.Assert.assertEquals(3, a.length());
        org.junit.Assert.assertEquals("id4", a.getJSONObject(0).getString("id"));
        org.junit.Assert.assertEquals("T4b", a.getJSONObject(0).getString("title"));
        org.junit.Assert.assertEquals("id5", a.getJSONObject(1).getString("id"));   // 舊的 id4 被換掉，沒有重複
        org.junit.Assert.assertEquals(1, new org.json.JSONArray(Announcements.mergeHistory("not json", "x", "info", "t", "b", 1, 3)).length());   // 舊紀錄壞掉：從頭開始
    }

    @org.junit.Test
    public void mutedAnnouncementDoesNotShowButCriticalOutdatedStillDoes() {
        java.util.Set<String> muted = new java.util.HashSet<>(java.util.Arrays.asList("m1", "c1"));
        org.junit.Assert.assertFalse(Announcements.parse("{\"id\":\"m1\",\"title\":\"t\"}", 1, NOW, true, muted).show);
        org.junit.Assert.assertTrue(Announcements.parse("{\"id\":\"c1\",\"severity\":\"critical\",\"title\":\"t\",\"min_version_below\":100}", 1, NOW, true, muted).show);
        org.junit.Assert.assertFalse(Announcements.parse("{\"id\":\"m1\",\"title\":\"t\"}", 1, NOW, true, muted).repeat);
    }
}
