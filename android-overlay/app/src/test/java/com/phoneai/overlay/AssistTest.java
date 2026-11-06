package com.phoneai.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 固定指令表：路由全由便宜模型決定，這裡只確認每個指令都有沒 root 的做法與英文名稱。 */
public class AssistTest {

    private static Assist.Route r(String t) { return Assist.route(t); }

    @Test
    public void keywordsNeverDecideTheRoute() {
        // 路由全部交給便宜模型：不管句子像不像固定指令，關鍵字這一步都不攔
        assertEquals(Assist.NONE, r("關閉定位").kind);
        assertEquals(Assist.NONE, r("音量調到 30%").kind);
        assertEquals(Assist.NONE, r("今天天氣如何").kind);
        assertEquals(Assist.NONE, r("").kind);
    }

    @Test
    public void everyFixedCommandHasANoRootEquivalent() {
        // 沒有 root 時固定指令靠 Phone.noRootAction 轉成無障礙／系統 API／設定頁；新增固定指令卻忘了加對應，這裡會抓到
        for (int i = 0; i < Assist.commandCount(); i++) {
            String shell = Assist.fixed(i).cmd;
            assertNotNull("固定指令沒有不用 root 的做法：" + shell, Phone.noRootAction(shell));
        }
    }

    @Test
    public void everyAiCmdNameHasANoRootEquivalent() {
        String[] names = {"wifi_on", "wifi_off", "bluetooth_on", "bluetooth_off", "volume_up", "volume_down", "dark_mode_on", "dark_mode_off",
                "dnd_on", "dnd_off", "auto_rotate_on", "auto_rotate_off", "location_on", "location_off", "notifications_open", "quick_settings_open", "panels_close"};
        for (String n : names) {
            assertNotNull("cmd 名稱沒有 shell 對應：" + n, Phone.shellForName(n));
            assertNotNull("cmd 沒有不用 root 的做法：" + n, Phone.noRootAction(Phone.shellForName(n)));
        }
        assertNull(Phone.shellForName("rm_rf"));
    }

    @Test
    public void everyCommandHasAnEnglishLabel() {
        // 指令一覽與路由提示用 commandKey(i)；英文標籤少了會顯示中文關鍵字
        for (int i = 0; i < Assist.commandCount(); i++) assertTrue(Assist.commandKey(i).length() > 0);
    }
}
