package com.phoneai.overlay;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RunStatusTest {
    @Test
    public void stuckOnlyAfterALongSilence() {
        assertFalse(RunStatus.looksStuck(100_000, 90_000));
        assertTrue(RunStatus.looksStuck(300_000, 100_000));
    }

    @Test
    public void theQuestionTellsHowLongAndWhichStep() {
        String t = RunStatus.askStop(200_000, 100_000, 195_000, 4);
        assertTrue(t, t.contains("100") && t.contains("4"));
        assertFalse("才 5 秒沒動靜不用提", t.contains("最後一次動靜"));
        String quiet = RunStatus.askStop(400_000, 100_000, 100_000, 7);
        assertTrue(quiet, quiet.contains("最後一次動靜") && quiet.contains("可能卡住了"));
        assertTrue(RunStatus.askStop(10_000, 9_000, 9_000, 0).contains("還在準備"));
    }
}
