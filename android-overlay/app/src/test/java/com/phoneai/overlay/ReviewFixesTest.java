package com.phoneai.overlay;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/** 2026-11-26 第二輪 code review 的修正。 */
public class ReviewFixesTest {
    @Test public void responseKeyDiffersAndMatchesPython() {
        byte[] key = new byte[32];
        for (int i = 0; i < 32; i++) key[i] = (byte) i;
        // 跟 windows-transfer 的 crypto.resp_key 同一組值（Python 測試 test_dk 也檢查）
        assertEquals("aa4ccec66d2984f9cff8f9255c3987778896725b44d6f1455dedc03faa172598", TransferCrypto.hex(TransferCrypto.respKey(key, "0a1b2c")));
        assertFalse(java.util.Arrays.equals(TransferCrypto.respKey(key, "n"), TransferCrypto.encKey(key, "n")));
        assertEquals("/v1/ping?dk=1", TransferCrypto.withDk("/v1/ping"));
        assertEquals("/v1/list?path=a&dk=1", TransferCrypto.withDk("/v1/list?path=a"));
    }

    @Test public void packageNamesCannotCarryShellCommands() {
        assertTrue(AgentLoop.validPkg("com.instagram.android"));
        assertTrue(AgentLoop.validPkg("com.sec.android.app.clockpackage"));
        assertFalse(AgentLoop.validPkg("com.x; rm -rf /sdcard/DCIM; #"));
        assertFalse(AgentLoop.validPkg("com.x && reboot"));
        assertFalse(AgentLoop.validPkg(""));
        assertFalse(AgentLoop.validPkg(null));
    }

    @Test public void typeWithEnterCountsAsSending() throws Exception {
        assertEquals("晚點到", AgentLoop.typedThenSent(new JSONObject().put("action", "type").put("text", "晚點到").put("enter", true)));
        assertNull(AgentLoop.typedThenSent(new JSONObject().put("action", "type").put("text", "晚點到")));
        JSONObject batch = new JSONObject().put("action", "batch").put("actions", new JSONArray()
                .put(new JSONObject().put("action", "tap").put("pick", 3))
                .put(new JSONObject().put("action", "type").put("text", "嗨"))
                .put(new JSONObject().put("action", "enter")));
        assertEquals("嗨", AgentLoop.typedThenSent(batch));
        JSONObject batch2 = new JSONObject().put("action", "batch").put("actions", new JSONArray()
                .put(new JSONObject().put("action", "type").put("text", "嗨").put("enter", true)));
        assertEquals("嗨", AgentLoop.typedThenSent(batch2));
        JSONObject noSend = new JSONObject().put("action", "batch").put("actions", new JSONArray()
                .put(new JSONObject().put("action", "enter"))
                .put(new JSONObject().put("action", "tap").put("pick", 1)));
        assertNull(AgentLoop.typedThenSent(noSend));
        assertNull(AgentLoop.typedThenSent(new JSONObject().put("action", "tap").put("pick", 1)));
    }

    @Test public void grepRecursiveFlagsBlockedEvenWithE() {
        assertFalse(SafetyGuard.readOnly("getprop | grep -ER sk-ant"));
        assertFalse(SafetyGuard.readOnly("getprop | grep -Er key"));
        assertFalse(SafetyGuard.readOnly("getprop | grep -Ef x"));
        assertTrue(SafetyGuard.readOnly("getprop | grep -E wifi"));
        assertTrue(SafetyGuard.readOnly("getprop | grep -i wifi"));
    }

    @Test public void clearAllKeepsLessons() throws Exception {
        assertTrue(Memory.clearable(new JSONObject().put("kind", "todo"), "all"));
        assertTrue(Memory.clearable(new JSONObject().put("kind", "note"), "all"));
        assertFalse(Memory.clearable(new JSONObject().put("kind", "lesson"), "all"));   // AI 學到的經驗不能被「清除全部」一起刪掉
        assertTrue(Memory.clearable(new JSONObject().put("kind", "todo"), "todo"));
        assertFalse(Memory.clearable(new JSONObject().put("kind", "note"), "todo"));
    }

    @Test public void memoryPageKeepsItemsAddedElsewhere() throws Exception {
        JSONObject mine = new JSONObject().put("next_id", 3).put("items", new JSONArray()
                .put(new JSONObject().put("id", 1).put("text", "a")).put(new JSONObject().put("id", 2).put("text", "b")));
        JSONObject disk = new JSONObject().put("next_id", 5).put("items", new JSONArray()
                .put(new JSONObject().put("id", 1).put("text", "a"))
                .put(new JSONObject().put("id", 2).put("text", "old b"))     // 畫面這份的優先
                .put(new JSONObject().put("id", 4).put("text", "added by AI")));
        MemoryActivity.mergeNewer(mine, disk);
        assertEquals(3, mine.getJSONArray("items").length());
        assertEquals("b", mine.getJSONArray("items").getJSONObject(1).getString("text"));
        assertEquals("added by AI", mine.getJSONArray("items").getJSONObject(2).getString("text"));
        assertEquals(5, mine.getInt("next_id"));
    }

    @Test public void endpointWithUserInfoIsNotLocal() {
        assertFalse(LlmAdapter.isSafeEndpoint("http://localhost:80@attacker.example/v1"));
        assertFalse(LlmAdapter.isSafeEndpoint("http://192.168.1.5@evil.com/v1"));
        assertTrue(LlmAdapter.isSafeEndpoint("http://localhost:11434/v1"));
        assertTrue(LlmAdapter.isSafeEndpoint("https://api.openai.com/v1"));
    }

    @Test public void classifierReplyParsing() {
        assertEquals(Boolean.TRUE, Router.parseKind("{\"k\":\"phone\"}"));
        assertEquals(Boolean.FALSE, Router.parseKind("{\"k\":\"chat\"}"));
        assertEquals(Boolean.TRUE, Router.parseKind("{\"k\": \"PHONE\"}"));
        assertNull(Router.parseKind("{\"k\":\"maybe\"}"));
        assertNull(Router.parseKind("not json"));
        assertNull(Router.parseKind(null));
        String p = Router.classifyPrompt("打开设定");
        assertTrue(p.contains("打开设定") && p.length() < 1200);   // 很短：約 100～300 token，不是完整路由那種長提示
    }

    @Test public void updateSizeLine() {
        long mb = 1024 * 1024;
        assertEquals("這次要下載約 1.0 MB（只下載有變的部分），完整檔是 38.0 MB", Updater.sizeText(1 * mb, 0, 38 * mb));   // 補丁
        assertEquals("這次要下載約 6.5 MB（只下載有變的部分），完整檔是 38.0 MB", Updater.sizeText(0, 6 * mb + mb / 2, 38 * mb));   // 區塊比對
        assertEquals("這次要下載約 1.0 MB（只下載有變的部分），完整檔是 38.0 MB", Updater.sizeText(1 * mb, 6 * mb, 38 * mb));   // 補丁優先
        assertEquals("這次要下載約 38.0 MB（完整檔）", Updater.sizeText(0, 0, 38 * mb));
        assertEquals("這次要下載約 38.0 MB（完整檔）", Updater.sizeText(40 * mb, 0, 38 * mb));   // 補丁比完整檔還大：不會這樣說
        assertEquals("下載大小不明", Updater.sizeText(0, 0, 0));
        assertEquals("這次要下載約 300 KB（只下載有變的部分）", Updater.sizeText(300 * 1024, 0, 0));
        assertEquals("12 KB", Updater.fmtBytes(12 * 1024));
        assertEquals("1 KB", Updater.fmtBytes(1));
    }
}
