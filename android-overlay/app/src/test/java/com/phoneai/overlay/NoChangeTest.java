package com.phoneai.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class NoChangeTest {
    @Test
    public void onlyActionsThatShouldChangeTheScreenCount() {
        for (String a : new String[]{"tap", "double_tap", "longpress", "swipe", "drag", "scroll", "type", "enter", "back", "batch"})
            assertTrue(a, NoChange.expectsChange(a));
        for (String a : new String[]{"wait", "home", "open", "done", "fail", "ask", "look", "remember", "set_alarm", "read_url", null})
            assertFalse(String.valueOf(a), NoChange.expectsChange(a));
    }

    @Test
    public void levelsGoHintThenScreenshotThenStop() {
        assertEquals(NoChange.NONE, NoChange.level(0));
        assertEquals(NoChange.HINT, NoChange.level(1));
        assertEquals(NoChange.ESCALATE, NoChange.level(2));
        assertEquals(NoChange.STOP, NoChange.level(3));
        assertEquals(NoChange.STOP, NoChange.level(7));
    }

    @Test
    public void describeAndHintsMentionTheActionAndAskForADifferentApproach() throws Exception {
        String d = NoChange.describe(new JSONObject("{\"action\":\"tap\",\"pick\":5}"));
        assertTrue(d, d.contains("tap") && d.contains("5"));
        assertTrue(NoChange.describe(new JSONObject("{\"action\":\"tap\",\"x\":120.4,\"y\":300}")).contains("120,300"));
        assertEquals("back", NoChange.describe(new JSONObject("{\"action\":\"back\"}")));
        String h1 = NoChange.hint(d, NoChange.HINT), h2 = NoChange.hint(d, NoChange.ESCALATE);
        assertTrue(h1.contains(d) && h1.contains("換個做法"));
        assertTrue("第二次要提到會給截圖", h2.contains("截圖") && h2.contains("連續第二次"));
    }
}
