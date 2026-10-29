package com.phoneai.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 不需要手機的單元測試：模型回覆的解析、元件編號換座標、經驗挑選等純邏輯。執行：cd android-overlay && gradle testDebugUnitTest */
public class LogicTest {

    // ---------- 模型回覆解析 ----------

    @Test
    public void parseLastFindsJsonAfterText() {
        JSONObject a = AgentLoop.parseLast("我先點搜尋。{\"action\":\"tap\",\"pick\":3,\"think\":\"點搜尋\"}");
        assertNotNull(a);
        assertEquals("tap", a.optString("action"));
        assertEquals(3, a.optInt("pick"));
    }

    @Test
    public void parseLastKeepsNestedBatch() {
        JSONObject a = AgentLoop.parseLast("{\"action\":\"batch\",\"actions\":[{\"action\":\"tap\",\"pick\":1},{\"action\":\"tap\",\"pick\":2}]}");
        assertNotNull(a);
        assertEquals(2, a.optJSONArray("actions").length());
    }

    @Test
    public void parseLastReturnsNullWithoutJson() {
        assertNull(AgentLoop.parseLast("我沒辦法用指令調亮度"));
    }

    @Test
    public void parseInvokeConvertsXmlToolCall() {
        JSONObject a = AgentLoop.parseInvoke("<invoke name=\"scroll\">\n<parameter name=\"dir\">down</parameter>\n"
                + "<parameter name=\"wait\">1.5</parameter>\n<parameter name=\"enter\">true</parameter>\n"
                + "<parameter name=\"think\">往下找</parameter>\n</invoke>");
        assertNotNull(a);
        assertEquals("scroll", a.optString("action"));
        assertEquals("down", a.optString("dir"));
        assertEquals(1.5, a.optDouble("wait"), 1e-9);
        assertTrue(a.optBoolean("enter"));
        assertEquals("往下找", a.optString("think"));
    }

    @Test
    public void parseInvokeReadsIntegerAndArray() {
        JSONObject a = AgentLoop.parseInvoke("<invoke name=\"batch\"><parameter name=\"actions\">[{\"action\":\"tap\",\"pick\":4}]</parameter>"
                + "<parameter name=\"level\">0</parameter></invoke>");
        assertNotNull(a);
        assertEquals(0, a.optInt("level", -1));
        assertEquals(4, a.optJSONArray("actions").optJSONObject(0).optInt("pick"));
    }

    @Test
    public void parseInvokeNullWhenNoInvoke() {
        assertNull(AgentLoop.parseInvoke("{\"action\":\"done\"}"));
    }

    // ---------- 元件清單 ----------

    private static JSONObject el(int n, String k, String t, int x, int y, boolean focused) throws Exception {
        return new JSONObject().put("n", n).put("k", k).put("t", t).put("x", x).put("y", y).put("f", focused);
    }

    @Test
    public void describeShowsKindFocusAndPosition() throws Exception {
        List<JSONObject> els = Arrays.asList(el(1, "輸入框", "搜尋", 500, 80, true), el(2, "按鈕", "", 900, 80, false));
        String d = ElementList.describe(els);
        assertTrue(d.contains("1. 輸入框（已聚焦）「搜尋」(500,80)"));
        assertTrue(d.contains("2. 按鈕「無文字」(900,80)"));
    }

    @Test
    public void pickAllFillsTopLevelAndBatchCoordinates() throws Exception {
        List<JSONObject> els = Arrays.asList(el(1, "按鈕", "1", 150, 840, false), el(2, "按鈕", "=", 845, 941, false));
        JSONObject tap = new JSONObject().put("action", "tap").put("pick", 2);
        assertTrue(ElementList.pickAll(tap, els));
        assertEquals(845, tap.optInt("x"));
        assertEquals(941, tap.optInt("y"));

        JSONObject batch = new JSONObject().put("action", "batch").put("actions",
                new JSONArray().put(new JSONObject().put("action", "tap").put("pick", 1)).put(new JSONObject().put("action", "tap").put("pick", 2)));
        assertTrue(ElementList.pickAll(batch, els));
        assertEquals(150, batch.getJSONArray("actions").getJSONObject(0).optInt("x"));
        assertEquals(941, batch.getJSONArray("actions").getJSONObject(1).optInt("y"));
    }

    @Test
    public void pickAllFailsOnUnknownNumber() throws Exception {
        List<JSONObject> els = new ArrayList<>(Arrays.asList(el(1, "按鈕", "1", 150, 840, false)));
        assertFalse(ElementList.pickAll(new JSONObject().put("action", "tap").put("pick", 9), els));
        JSONObject batch = new JSONObject().put("action", "batch").put("actions",
                new JSONArray().put(new JSONObject().put("action", "tap").put("pick", 1)).put(new JSONObject().put("action", "tap").put("pick", 7)));
        assertFalse(ElementList.pickAll(batch, els));
    }

    @Test
    public void pickAllIgnoresActionsWithoutPick() throws Exception {
        assertTrue(ElementList.pickAll(new JSONObject().put("action", "scroll").put("dir", "down"), new ArrayList<>()));
    }

    // ---------- 經驗挑選 ----------

    @Test
    public void pickRelevantPrefersOverlapThenNewest() {
        List<String> all = Arrays.asList("拖方塊遊戲：物件跟著手指位移走", "搜尋框輸入後要按 Enter", "調亮度要拖滑桿",
                "計算機要連點數字鍵", "相機快門在下方中間", "通知欄要從上往下滑");
        List<String> got = Memory.pickRelevant(all, "幫我把螢幕亮度拖到最低", 2);
        assertEquals(2, got.size());
        assertTrue(got.contains("調亮度要拖滑桿"));
        assertTrue(got.contains("通知欄要從上往下滑"));   // 沒有其他相關的：用最新的補
        assertEquals("調亮度要拖滑桿", got.get(0));       // 回傳順序維持舊到新
    }

    @Test
    public void pickRelevantReturnsAllWhenFewer() {
        assertEquals(2, Memory.pickRelevant(Arrays.asList("a", "b"), "任務", 6).size());
    }

    // ---------- 要不要顯示回答卡片 ----------

    @Test
    public void questionTasksNeedAVisibleAnswer() {
        assertTrue(AgentLoop.isQuestion("打開 LINE，告訴我最上面一個聊天室的名稱"));
        assertTrue(AgentLoop.isQuestion("打開設定，告訴我電池剩多少"));
        assertTrue(AgentLoop.isQuestion("What is my Android version"));
        assertFalse(AgentLoop.isQuestion("把螢幕亮度調到最低"));
        assertFalse(AgentLoop.isQuestion("打開計算機，算 123 乘 45"));
        assertFalse(AgentLoop.isQuestion(null));
    }

    @Test
    public void systemInfoQuestionsDoNotPreOpenAnApp() {
        assertTrue(Router.isSystemInfoQuestion("打開設定，告訴我 Android 版本"));
        assertTrue(Router.isSystemInfoQuestion("打開設定，告訴我電池剩多少"));
        assertTrue(Router.isSystemInfoQuestion("現在幾點"));
        assertFalse(Router.isSystemInfoQuestion("打開設定，把深色模式打開"));
        assertFalse(Router.isSystemInfoQuestion("打開 LINE，告訴我最上面一個聊天室的名稱"));
        assertFalse(Router.isSystemInfoQuestion("打開設定的 Wi-Fi 頁面"));
    }

    @Test
    public void findAppUsesSynonyms() {
        java.util.Map<String, String> apps = new java.util.HashMap<>();
        apps.put("圖庫", "com.sec.android.gallery3d");
        apps.put("設定", "com.android.settings");
        apps.put("google地圖", "com.google.android.apps.maps");
        assertEquals("com.sec.android.gallery3d", Router.findApp(apps, "相簿"));
        assertEquals("com.sec.android.gallery3d", Router.findApp(apps, "照片"));
        assertEquals("com.android.settings", Router.findApp(apps, "Settings"));
        assertEquals("com.google.android.apps.maps", Router.findApp(apps, "地圖"));
        assertEquals("com.android.settings", Router.findApp(apps, "設定"));
        assertNull(Router.findApp(apps, "不存在的軟體"));
        apps.put("相簿", "x.exact");
        assertEquals("x.exact", Router.findApp(apps, "相簿"));   // 完全相符優先
    }

    @Test
    public void pickKeepsTheLabelForTheSafetyGuard() throws Exception {
        List<JSONObject> els = Arrays.asList(el(1, "按鈕", "確認付款", 500, 900, false));
        JSONObject tap = new JSONObject().put("action", "tap").put("pick", 1);
        assertTrue(ElementList.pickAll(tap, els));
        assertEquals("確認付款", tap.optString("label"));
    }

    @Test
    public void sensitiveButtonsNeedAConfirmationUnlessTheTaskAskedForIt() throws Exception {
        JSONObject pay = new JSONObject().put("action", "tap").put("label", "確認付款");
        assertEquals("確認付款", AgentLoop.sensitiveLabel(pay, "打開蝦皮看我的訂單"));
        assertNull(AgentLoop.sensitiveLabel(pay, "幫我付款"));
        JSONObject send = new JSONObject().put("action", "tap").put("label", "Send");
        assertEquals("Send", AgentLoop.sensitiveLabel(send, "打開 LINE 看最新訊息"));
        JSONObject harmless = new JSONObject().put("action", "tap").put("label", "搜尋");
        assertNull(AgentLoop.sensitiveLabel(harmless, "打開 YouTube 搜尋 周杰倫"));
        // batch 裡面的子動作也要檢查
        JSONObject batch = new JSONObject().put("action", "batch").put("actions", new JSONArray()
                .put(new JSONObject().put("action", "tap").put("label", "1"))
                .put(new JSONObject().put("action", "tap").put("label", "刪除")));
        assertEquals("刪除", AgentLoop.sensitiveLabel(batch, "算 1 加 1"));
        // 英文單字要整個字比對：display 不是 pay
        assertNull(AgentLoop.sensitiveLabel(new JSONObject().put("action", "tap").put("label", "Display"), "調整螢幕"));
    }

    // ---------- 其他小工具 ----------

    @Test
    public void modelNameIsReadable() {
        assertEquals("Haiku 5.5", Usage.modelName("claude-haiku-5-5"));
        assertEquals("Sonnet 5.5", Usage.modelName("claude-sonnet-5-5"));
        assertEquals("", Usage.modelName(""));
    }

    @Test
    public void priceHaikuCheaperThanSonnet() {
        assertTrue(Usage.price("claude-haiku-5-5")[0] < Usage.price("claude-sonnet-5-5")[0]);
    }

    @Test
    public void routerNormStripsSpacesAndPunctuation() {
        assertEquals("藍牙", Router.norm(" 藍 牙，"));
        assertEquals("youtube", Router.norm("YouTube"));
    }

    @Test
    public void readOnlyShellRules() {
        assertTrue(AgentLoop.readOnly("dumpsys battery | grep level"));
        assertFalse(AgentLoop.readOnly("rm -rf /sdcard"));
        assertFalse(AgentLoop.readOnly("dumpsys battery > /sdcard/x"));
        assertFalse(AgentLoop.readOnly("dumpsys battery; reboot"));
    }
}
