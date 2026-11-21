package com.phoneai.overlay;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

public class WaitsTest {
    @Test
    public void returnsAsSoonAsTheConditionHolds() {
        long t0 = System.currentTimeMillis();
        assertTrue(Waits.until(() -> true, 5000, 50));
        assertTrue(System.currentTimeMillis() - t0 < 1000);
    }

    @Test
    public void waitsForAConditionThatBecomesTrueLater() {
        AtomicBoolean flag = new AtomicBoolean();
        new Thread(() -> { try { Thread.sleep(300); } catch (InterruptedException ignored) { } flag.set(true); }).start();
        assertTrue(Waits.until(flag::get, 3000, 50));
    }

    @Test
    public void givesUpAfterTheTimeout() {
        long t0 = System.currentTimeMillis();
        assertFalse(Waits.until(() -> false, 300, 50));
        long took = System.currentTimeMillis() - t0;
        assertTrue("took " + took, took >= 250 && took < 2000);
    }
}
