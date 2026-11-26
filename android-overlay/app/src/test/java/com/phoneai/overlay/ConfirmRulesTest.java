package com.phoneai.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 確認框與「讀過外人寫的內容之後」的規則。 */
public class ConfirmRulesTest {
    @Test public void eachConfirmRequestOnlyAnswersItself() throws Exception {
        ConfirmActivity.Req a = ConfirmActivity.newRequest();
        ConfirmActivity.Req b = ConfirmActivity.newRequest();
        assertNotEquals(a.id, b.id);
        a.answer.complete(1);                 // 使用者在 A 按了「是」
        assertFalse(b.answer.isDone());       // B 不受影響
        ConfirmActivity.close(a);             // A 結束（逾時或答完）
        assertEquals(Integer.valueOf(1), a.answer.get());   // 已經答的不會被改成「否」
        ConfirmActivity.close(b);             // B 逾時沒答
        assertEquals(Integer.valueOf(0), b.answer.get());   // 沒答＝不同意
    }

    @Test public void afterUntrustedContentOnlyReadOnlyTools() {
        // 讀過網頁（沒讀過通知）：唯讀工具、繼續讀網頁可以；會改東西的不行
        assertTrue(ChatEngine.allowedAfterUntrusted("get_time", false));
        assertTrue(ChatEngine.allowedAfterUntrusted("read_url", false));
        assertTrue(ChatEngine.allowedAfterUntrusted("read_calendar", false));
        assertFalse(ChatEngine.allowedAfterUntrusted("file_send", false));
        assertFalse(ChatEngine.allowedAfterUntrusted("file_delete", false));
        assertFalse(ChatEngine.allowedAfterUntrusted("add_calendar_event", false));
        assertFalse(ChatEngine.allowedAfterUntrusted("memory_add", false));
        assertFalse(ChatEngine.allowedAfterUntrusted("phone_task", false));
        // 讀過通知或行事曆：連 read_url 也不行（避免把私人內容帶到外面的網址）
        assertFalse(ChatEngine.allowedAfterUntrusted("read_url", true));
        assertFalse(ChatEngine.allowedAfterUntrusted("current_page", true));
        assertTrue(ChatEngine.allowedAfterUntrusted("read_notifications", true));
        assertTrue(ChatEngine.allowedAfterUntrusted("read_calendar", true));
    }
}
