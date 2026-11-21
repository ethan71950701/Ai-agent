package com.phoneai.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PullMathTest {
    @Test
    public void startsNearlyOneToOneThenGetsHarder() {
        float small = PullMath.resist(20, 150);
        assertTrue("一開始幾乎跟著手指：" + small, small > 17 && small <= 20);
        float a = PullMath.resist(100, 150) - PullMath.resist(50, 150);
        float b = PullMath.resist(300, 150) - PullMath.resist(250, 150);
        assertTrue("越往後同樣的手指距離，畫面移動越少", b < a);
    }

    @Test
    public void neverHitsAWallButNeverReachesTheLimit() {
        float prev = 0;
        for (int d = 10; d <= 2000; d += 10) {
            float p = PullMath.resist(d, 150);
            assertTrue("一直往下拉，畫面還是會動（不會突然卡住）：d=" + d, p > prev);
            assertTrue(p < 150);
            prev = p;
        }
    }

    @Test
    public void noPullMeansNoMovement() {
        assertEquals(0f, PullMath.resist(0, 150), 0f);
        assertEquals(0f, PullMath.resist(-30, 150), 0f);
    }

    @Test
    public void progressAndTriggerAroundTheThreshold() {
        assertEquals(0.5f, PullMath.progress(40, 80), 0.001f);
        assertEquals(1f, PullMath.progress(200, 80), 0f);
        assertEquals(0f, PullMath.progress(-5, 80), 0f);
        assertTrue(PullMath.triggers(72, 80));
        assertFalse(PullMath.triggers(71, 80));
    }
}
