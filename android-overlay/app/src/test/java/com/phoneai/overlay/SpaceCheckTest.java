package com.phoneai.overlay;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SpaceCheckTest {
    private static final long MB = 1024L * 1024, GB = 1024 * MB;

    @Test
    public void fitsWhenThereIsRoom() {
        assertNull(SpaceCheck.problem(120 * MB, 5 * GB, 200 * MB, 2 * GB));
        assertNull(SpaceCheck.problem(0, 1, 0, 2 * GB));
        assertNull(SpaceCheck.problem(-1, 0, 200 * MB, 2 * GB));   // 不知道大小：不擋
    }

    @Test
    public void rejectsTooBigAndExplains() {
        String r = SpaceCheck.problem(3 * GB, 100 * GB, 200 * MB, 2 * GB);
        assertNotNull(r);
        assertTrue(r, r.contains("太大") && r.contains("2.00 GB") && r.contains("3.00 GB"));
    }

    @Test
    public void rejectsWhenSpaceIsTooLowIncludingTheReserve() {
        String r = SpaceCheck.problem(120 * MB, 250 * MB, 200 * MB, 2 * GB);   // 250 − 120 = 130 < 200
        assertNotNull(r);
        assertTrue(r, r.contains("空間不夠") && r.contains("清出至少 70.0 MB"));
        assertNull(SpaceCheck.problem(50 * MB, 250 * MB, 200 * MB, 2 * GB));   // 剛好留得住
        assertNull(SpaceCheck.problem(50 * MB, 250 * MB, 200 * MB, 2 * GB));
    }

    @Test
    public void parsesThePeerReply() {
        assertArrayEquals(new long[]{1000, 200, 3000}, SpaceCheck.parse("1000\t200\t3000"));
        assertArrayEquals(new long[]{1, 2, 3}, SpaceCheck.parse(" 1\t2\t3\t99\n"));
        assertNull(SpaceCheck.parse("unknown"));
        assertNull(SpaceCheck.parse(""));
        assertNull(SpaceCheck.parse("a\tb\tc"));
    }
}
