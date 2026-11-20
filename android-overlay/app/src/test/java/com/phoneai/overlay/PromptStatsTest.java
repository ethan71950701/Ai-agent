package com.phoneai.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class PromptStatsTest {
    private JSONObject body(int sysLen, int toolCount, int msgLen) throws Exception {
        JSONArray tools = new JSONArray();
        for (int i = 0; i < toolCount; i++) tools.put(new JSONObject().put("name", "t" + i));
        return new JSONObject().put("model", "claude-x").put("system", new String(new char[sysLen]).replace('\0', 's'))
                .put("tools", tools).put("messages", new JSONArray().put(new JSONObject().put("role", "user").put("content", new String(new char[msgLen]).replace('\0', 'm'))));
    }

    @Test
    public void measuresEachPartAndReadsUsage() throws Exception {
        PromptStats.Entry e = PromptStats.measure("聊天", body(3000, 20, 100), new JSONObject().put("input_tokens", 4000).put("cache_read_input_tokens", 500).put("output_tokens", 80), 2500, 1);
        assertEquals(3000, e.sys);
        assertTrue(e.tools > 100);
        assertTrue(e.msgs > 100);
        assertEquals(4500, e.in);   // 輸入 token 含快取
        assertEquals(80, e.out);
        PromptStats.Entry o = PromptStats.measure("路由", body(10, 0, 10), new JSONObject().put("prompt_tokens", 77).put("completion_tokens", 5), 1, 1);
        assertEquals(77, o.in);   // OpenAI 相容服務的欄位名
        assertEquals(0, o.tools);
        PromptStats.Entry none = PromptStats.measure("x", new JSONObject().put("model", "m"), null, 1, 1);
        assertEquals(-1, none.in);
        assertEquals(0, none.sys);
    }

    @Test
    public void sourceIsNamedByTheCaller() {
        StackTraceElement chatCall = new StackTraceElement("com.phoneai.overlay.ChatEngine", "call", "ChatEngine.java", 1);
        StackTraceElement post = new StackTraceElement("com.phoneai.overlay.ChatEngine", "post", "ChatEngine.java", 1);
        StackTraceElement agent = new StackTraceElement("com.phoneai.overlay.AgentLoop", "run", "AgentLoop.java", 1);
        StackTraceElement lambda = new StackTraceElement("com.phoneai.overlay.Router$1", "run", "Router.java", 1);
        StackTraceElement jdk = new StackTraceElement("java.lang.Thread", "getStackTrace", "Thread.java", 1);
        assertEquals("聊天", PromptStats.sourceOf(new StackTraceElement[]{jdk, post, chatCall}));
        assertEquals("自動操作", PromptStats.sourceOf(new StackTraceElement[]{jdk, post, agent}));
        assertEquals("路由", PromptStats.sourceOf(new StackTraceElement[]{jdk, post, lambda}));
        assertEquals("其他", PromptStats.sourceOf(new StackTraceElement[]{jdk}));
    }

    @Test
    public void keepsOnlyTheLatestEntriesAndSummarisesBySource() throws Exception {
        File f = File.createTempFile("pstats", ".json");
        f.delete();
        for (int i = 0; i < PromptStats.MAX + 20; i++) {
            PromptStats.record(f, PromptStats.measure(i % 2 == 0 ? "聊天" : "自動操作", body(1000 + i, 2, 50), new JSONObject().put("input_tokens", 1000), 2000, i));
        }
        List<PromptStats.Entry> all = PromptStats.load(f);
        assertEquals(PromptStats.MAX, all.size());
        assertEquals(20, all.get(0).t);   // 最舊的 20 筆被丟掉
        String s = PromptStats.format(all);
        assertTrue(s, s.contains("聊天：50 次") && s.contains("自動操作：50 次"));
        assertTrue(s, s.contains("實際輸入 1000 token") && s.contains("2.0 秒"));
        f.delete();
    }

    @Test
    public void emptyAndCorruptFilesAreHarmless() throws Exception {
        assertEquals("（還沒有紀錄）", PromptStats.format(new java.util.ArrayList<>()));
        File f = File.createTempFile("pstats", ".json");
        java.nio.file.Files.write(f.toPath(), "not json".getBytes());
        assertTrue(PromptStats.load(f).isEmpty());
        f.delete();
    }
}
