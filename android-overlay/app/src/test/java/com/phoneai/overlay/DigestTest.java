package com.phoneai.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

/** 通知整理：模型回答解析與救回摘要（回答被截斷、或前後多了字、程式碼框）。 */
public class DigestTest {
    @Test
    public void parsesCleanJson() {
        JSONObject o = NotifDigest.parse("{\"summary\":\"今天有三則\",\"items\":[{\"title\":\"開會\",\"date\":\"2026-11-07\",\"time\":\"14:00\",\"type\":\"calendar\",\"from\":\"LINE\"}]}");
        assertNotNull(o);
        assertEquals(1, o.optJSONArray("items").length());
    }

    @Test
    public void parsesJsonInsideCodeFence() {
        JSONObject o = NotifDigest.parse("好的：\n```json\n{\"summary\":\"摘要在這裡\",\"items\":[]}\n```");
        assertNotNull(o);
        assertEquals("摘要在這裡", o.optString("summary"));
    }

    @Test
    public void truncatedJsonIsSalvagedAsSummary() {
        String raw = "{\"summary\":\"涵蓋最近 24 小時\\nLINE：小明問你明天要不要吃飯\\n銀行：有一則繳費提醒\",\"items\":[{\"title\":\"繳費\",\"da";
        assertNull(NotifDigest.parse(raw));
        JSONObject o = NotifDigest.salvage(raw);
        assertNotNull(o);
        assertTrue(o.optString("summary").contains("LINE：小明"));
        assertTrue(o.optString("summary").contains("\n"));
        assertEquals(0, o.optJSONArray("items").length());
    }

    @Test
    public void plainTextAnswerIsShownAsSummary() {
        JSONObject o = NotifDigest.salvage("今天沒有重要的通知，只有幾則廣告與系統訊息。");
        assertNotNull(o);
        assertTrue(o.optString("summary").startsWith("今天沒有重要的通知"));
    }

    @Test
    public void brokenJsonWithoutSummaryIsNotShownRaw() {
        assertNull(NotifDigest.salvage("{\"items\":[{\"title\":"));
        assertNull(NotifDigest.salvage("   "));
    }
}
