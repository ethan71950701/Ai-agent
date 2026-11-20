package com.phoneai.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TransferProgressTest {
    @Test
    public void speedIsAveragedOverAtLeastOneSecond() {
        TransferProgress p = new TransferProgress();
        assertEquals(0, p.update(1000, 0), 0.001);
        assertEquals(0, p.update(1500, 5_000_000), 0.001);                 // 不到 1 秒：還沒有速度
        assertEquals(10_000_000, p.update(2000, 10_000_000), 1);           // 1 秒 10 MB
        double v = p.update(3000, 15_000_000);                             // 接著 1 秒只有 5 MB：平滑後介於兩者之間
        assertTrue(String.valueOf(v), v > 5_000_000 && v < 10_000_000);
    }

    @Test
    public void restartsWhenProgressGoesBackwards() {
        TransferProgress p = new TransferProgress();
        p.update(0, 0);
        p.update(1000, 1_000_000);
        assertEquals(0, p.update(2000, 100), 0.001);   // 續傳或重傳：進度倒退，重新計速
    }

    @Test
    public void etaAndFormatting() {
        assertEquals(100, TransferProgress.etaSeconds(1_000_000_000L, 0, 10_000_000));
        assertEquals(-1, TransferProgress.etaSeconds(1000, 1000, 5));
        assertEquals(-1, TransferProgress.etaSeconds(1000, 0, 0));
        assertEquals("12.3 MB/s", TransferProgress.speedText(12.3 * 1048576));
        assertEquals("800 KB/s", TransferProgress.speedText(800 * 1024));
        assertEquals("", TransferProgress.speedText(0));
        assertEquals("45 秒", TransferProgress.etaText(45));
        assertEquals("6 分 12 秒", TransferProgress.etaText(372));
        assertEquals("6 分", TransferProgress.etaText(360));
        assertEquals("2 小時 5 分", TransferProgress.etaText(7500));
        assertEquals("2%　12.3 MB/s　剩 6 分 12 秒", TransferProgress.compact(2, 12.3 * 1048576, 372));
        assertEquals("2%", TransferProgress.compact(2, 0, -1));
    }

    @Test
    public void longNamesAreShortenedInTheMiddle() {
        String n = "加密小米13移植18PM_OS4.0.31.0.XEOCNXM.zip";
        String s = TransferProgress.ellipsizeMiddle(n, 24);
        assertEquals(24, s.length());
        assertTrue(s, s.contains("…") && s.endsWith("XEOCNXM.zip") && s.startsWith("加密"));
        assertEquals("short.zip", TransferProgress.ellipsizeMiddle("short.zip", 24));
        assertEquals("", TransferProgress.ellipsizeMiddle(null, 24));
        assertEquals("abcdef", TransferProgress.ellipsizeMiddle("abcdef", 6));
    }
}
