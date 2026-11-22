package com.phoneai.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.Test;

public class RemoteTuningTest {
    @Test public void keepsOnlyAllowedNumbersAndClamps() {
        Map<String, Float> m = RemoteTuning.parse("{\"g3_blur\": 12, \"g3_dark\": 9, \"g3_rim\": -1, \"evil\": 1, \"g3_elev\": \"8\"}");
        assertEquals(12f, m.get("g3_blur"), 0.001f);
        assertEquals(2f, m.get("g3_dark"), 0.001f);     // 夾到上限
        assertEquals(0f, m.get("g3_rim"), 0.001f);      // 夾到下限
        assertFalse(m.containsKey("evil"));              // 不在白名單
        assertFalse(m.containsKey("g3_elev"));           // 字串不收
    }

    @Test public void brokenOrHugeJsonGivesEmpty() {
        assertTrue(RemoteTuning.parse("not json").isEmpty());
        assertTrue(RemoteTuning.parse(null).isEmpty());
        StringBuilder sb = new StringBuilder("{\"g3_blur\":1,\"x\":\"");
        for (int i = 0; i < 5000; i++) sb.append('a');
        assertTrue(RemoteTuning.parse(sb.append("\"}").toString()).isEmpty());
    }

    @Test public void glassLevelsAndSpringKeysAreAllowedAndClamped() {
        Map<String, Float> m = RemoteTuning.parse("{\"g1_blur\": 99, \"g0_tint\": 0.7, \"card_stiff\": 5, \"card_damp\": 0.35}");
        assertEquals(40f, m.get("g1_blur"), 0.001f);
        assertEquals(0.7f, m.get("g0_tint"), 0.001f);
        assertEquals(100f, m.get("card_stiff"), 0.001f);
        assertEquals(0.35f, m.get("card_damp"), 0.001f);
    }
}
