package com.phoneai.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 固定指令的比對：該攔的攔、多件事的不攔。 */
public class AssistTest {

    private static Assist.Route r(String t) { return Assist.route(t); }

    @Test
    public void exactCommandsMatch() {
        assertEquals(Assist.FIXED, r("關閉定位").kind);
        assertTrue(r("關閉定位").cmd.contains("set-location-enabled false"));
        assertTrue(r("開啟 NFC").cmd.contains("svc nfc enable"));
        assertTrue(r("关闭行动数据").cmd.contains("svc data disable"));
        assertTrue(r("截圖").cmd.contains("keyevent 120"));
    }

    @Test
    public void politeFillerStillMatches() {
        assertEquals(Assist.FIXED, r("請幫我開自動旋轉").kind);
        assertTrue(r("請幫我開自動旋轉").cmd.contains("accelerometer_rotation 1"));
        assertEquals(Assist.FIXED, r("把亮度調到最低").kind);
    }

    @Test
    public void multiStepSentencesGoToTheAi() {
        assertEquals(Assist.NONE, r("關閉定位，然後打開 Google 地圖搜尋台北車站").kind);
        assertEquals(Assist.NONE, r("把亮度調到最低，然後打開計算機").kind);
        assertEquals(Assist.NONE, r("打開深色模式，然後打開設定的 Wi-Fi 頁").kind);
    }

    @Test
    public void longestKeyWins() {
        // 「亮度調到最低」比「亮度最低」長，不該被別的短關鍵字搶走
        assertTrue(r("亮度調到最低").cmd.contains("screen_brightness 1"));
        assertTrue(r("亮度調到最高").cmd.contains("screen_brightness 255"));
    }

    @Test
    public void networkCuttingCommandsAskFirst() {
        assertTrue(r("關閉行動數據").confirm);
        assertTrue(r("開飛航").confirm);
        assertEquals(false, r("關閉定位").confirm);
    }

    @Test
    public void unknownTextIsNotFixed() {
        assertEquals(Assist.NONE, r("今天天氣如何").kind);
        assertEquals(Assist.NONE, r("").kind);
    }

    @Test
    public void everyCommandHasAnEnglishLabel() {
        // 指令一覽與路由提示用 commandKey(i)；英文標籤少了會顯示中文關鍵字
        for (int i = 0; i < Assist.commandCount(); i++) assertTrue(Assist.commandKey(i).length() > 0);
    }
}
