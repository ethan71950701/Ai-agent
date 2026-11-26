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
}
