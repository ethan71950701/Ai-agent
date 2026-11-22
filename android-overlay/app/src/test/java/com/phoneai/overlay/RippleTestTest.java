package com.phoneai.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RippleTestTest {
    @Test
    public void testPointsCoverTheCentreAndTheFourQuadrants() {
        int[][] p = RippleTest.points(720, 1200);
        assertEquals(5, p.length);
        assertEquals(360, p[0][0]);
        assertEquals(600, p[0][1]);
        for (int[] q : p) assertTrue(q[0] > 0 && q[0] < 720 && q[1] > 0 && q[1] < 1200);
    }
}
