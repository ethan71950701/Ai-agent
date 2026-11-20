package com.phoneai.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Set;

import org.junit.Test;

/** 自動操作與聊天助理共用同一套工具：名單、提示詞、讀過通知後的限制。 */
public class AgentToolsTest {
    @Test
    public void agentSharesTheChatToolsExceptTheOnesItHasItsOwnVersionOf() {
        Set<String> n = ChatEngine.agentToolNames();
        for (String must : new String[]{"get_time", "set_alarm", "set_timer", "add_calendar_event", "navigate_map", "show_on_map", "dial",
                "read_notifications", "schedule_web_query", "memory_add", "memory_read", "memory_list", "memory_update", "memory_delete",
                "device_list", "file_find", "file_list", "file_get", "file_send", "file_delete"}) {
            assertTrue(must, n.contains(must));
        }
        for (String not : new String[]{"phone_action", "phone_task", "run_shell", "samsung_reminder", "tap", "type"}) assertFalse(not, n.contains(not));
        assertTrue(ChatEngine.isAgentTool("file_send"));
        assertFalse(ChatEngine.isAgentTool("phone_action"));
        assertFalse(ChatEngine.isAgentTool(""));
        assertFalse(ChatEngine.isAgentTool(null));
        assertFalse(ChatEngine.isAgentTool("tap"));
    }

    @Test
    public void promptListsEveryToolWithItsArguments() {
        String p = ChatEngine.agentToolPrompt();
        assertTrue(p, p.contains("{\"action\":\"set_alarm\""));
        assertTrue(p, p.contains("\"minutes_from_now\":整數"));
        String sendLine = "";
        for (String l : p.split("\n")) if (l.startsWith("{\"action\":\"file_send\"")) sendLine = l;
        assertTrue(sendLine, sendLine.contains("\"device\":\"…\"") && sendLine.contains("\"path\":\"…\""));   // 欄位順序不固定，只看有沒有
        assertTrue(p, p.contains("{\"action\":\"file_delete\""));
        assertFalse(p.contains("phone_action"));
        assertFalse(p.contains("\"action\":\"run_shell\""));
        assertFalse(p.contains("samsung_reminder"));
        int lines = p.split("\n").length;
        assertEquals(ChatEngine.agentToolNames().size(), lines);   // 一個工具一行
    }

    @Test
    public void fullSystemPromptHasNoLeftoverPlaceholder() {
        String s = AgentPrompt.full();
        assertFalse(s.contains("@@TOOLS@@"));
        assertTrue(s.contains("set_alarm"));
        assertTrue(s.contains("ask_user"));
        assertTrue("原本的畫面操作動作還在", s.contains("{\"action\":\"tap\""));
    }

    @Test
    public void afterReadingNotificationsOnlyReadOnlyToolsRemain() {
        assertTrue(ChatEngine.readOnlyTool("get_time"));
        assertTrue(ChatEngine.readOnlyTool("read_notifications"));
        for (String w : new String[]{"file_delete", "file_send", "memory_add", "memory_delete", "set_alarm", "dial", "schedule_web_query"}) {
            assertFalse(w, ChatEngine.readOnlyTool(w));
        }
    }

    @Test
    public void phoneTaskIsGuarded() {
        assertNull(ChatEngine.phoneTaskProblem(true, false, 0, "打開地圖查附近的餐廳"));
        assertNull(ChatEngine.phoneTaskProblem(true, false, 1, "x"));
        assertNotNull("沒有執行器（不在聊天面板裡）不能用", ChatEngine.phoneTaskProblem(false, false, 0, "x"));
        assertNotNull("已經在操作手機時不能再開一層", ChatEngine.phoneTaskProblem(true, true, 0, "x"));
        assertNotNull("空的任務", ChatEngine.phoneTaskProblem(true, false, 0, "  "));
        assertNotNull("一輪最多 " + ChatEngine.MAX_PHONE_TASKS + " 次", ChatEngine.phoneTaskProblem(true, false, ChatEngine.MAX_PHONE_TASKS, "x"));
    }

    @Test
    public void phoneTaskIsAChatToolButNotAnAutomaticOperationAction() {
        assertFalse(ChatEngine.isAgentTool("phone_task"));
        assertFalse(ChatEngine.agentToolPrompt().contains("phone_task"));
    }
}
