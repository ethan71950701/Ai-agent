package com.phoneai.overlay;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class IdleWatchTest {
    @Test
    public void closesWhenNobodyEverComes() {
        IdleWatch w = new IdleWatch(0);
        assertFalse(w.tick(30_000, 0));
        assertFalse(w.tick(89_000, 0));
        assertTrue(w.tick(90_000, 0));
    }

    @Test
    public void staysOpenWhileTransferring() {
        IdleWatch w = new IdleWatch(0);
        assertFalse(w.tick(5_000, 1));
        assertFalse(w.tick(200_000, 1));   // 傳很久也不關（沒到 10 分鐘上限）
        assertFalse(w.tick(300_000, 2));
    }

    @Test
    public void closesShortlyAfterTheLastConnectionEnds() {
        IdleWatch w = new IdleWatch(0);
        assertFalse(w.tick(5_000, 1));
        assertFalse(w.tick(8_000, 0));       // 剛結束，開始計時
        assertFalse(w.tick(17_000, 0));
        assertTrue(w.tick(18_000, 0));       // 10 秒後關
    }

    @Test
    public void newConnectionResetsTheIdleTimer() {
        IdleWatch w = new IdleWatch(0);
        w.tick(5_000, 1);
        assertFalse(w.tick(8_000, 0));
        assertFalse(w.tick(15_000, 1));      // 又有人連進來
        assertFalse(w.tick(20_000, 0));
        assertFalse(w.tick(29_000, 0));
        assertTrue(w.tick(30_000, 0));
    }

    @Test
    public void hardLimitStopsEvenIfStuck() {
        IdleWatch w = new IdleWatch(1_000);
        assertFalse(w.tick(500_000, 3));
        assertTrue(w.tick(601_000, 3));
    }
}
