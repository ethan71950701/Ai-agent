package com.phoneai.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class SingleRunTest {
    @Test
    public void onlyOneCanEnterUntilItLeaves() {
        SingleRun g = new SingleRun();
        assertTrue(g.tryEnter());
        assertFalse("第二個要被擋下", g.tryEnter());
        g.exit();
        assertTrue("放掉之後又可以進", g.tryEnter());
    }

    @Test
    public void exactlyOneOfManyThreadsGetsIn() throws Exception {
        final SingleRun g = new SingleRun();
        final AtomicInteger in = new AtomicInteger();
        Thread[] ts = new Thread[16];
        for (int i = 0; i < ts.length; i++) { ts[i] = new Thread(() -> { if (g.tryEnter()) in.incrementAndGet(); }); }
        for (Thread t : ts) t.start();
        for (Thread t : ts) t.join();
        assertEquals(1, in.get());
    }
}
