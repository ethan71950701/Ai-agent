package com.phoneai.overlay;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class BlockSyncTest {
    private static byte[] res(String n) throws Exception {
        try (InputStream in = BlockSyncTest.class.getClassLoader().getResourceAsStream(n)) {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int k;
            while ((k = in.read(b)) > 0) o.write(b, 0, k);
            return o.toByteArray();
        }
    }

    /** 模擬伺服器：回新檔案的指定區間。 */
    private static List<byte[]> fetch(byte[] neu, List<long[]> ranges) {
        List<byte[]> out = new ArrayList<>();
        for (long[] r : ranges) out.add(java.util.Arrays.copyOfRange(neu, (int) r[0], (int) r[1]));
        return out;
    }

    private static byte[] rebuild(byte[] old, byte[] neu, BlockSync.Control c, int gap) throws Exception {
        int[] src = BlockSync.plan(old, c);
        List<long[]> ranges = BlockSync.ranges(c, src, gap);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BlockSync.assemble(old, c, src, ranges, fetch(neu, ranges), out);
        return out.toByteArray();
    }

    @Test public void rebuildsNewFromOldPlusMissingRanges() throws Exception {
        byte[] old = res("delta_old.bin"), neu = res("delta_new.bin");
        BlockSync.Control c = BlockSync.parse(res("blocks.ctl"));
        assertNotNull(c);
        assertEquals(neu.length, c.size);
        assertArrayEquals(neu, rebuild(old, neu, c, 0));
        assertArrayEquals(neu, rebuild(old, neu, c, 1024));   // 合併相鄰區間也一樣
    }

    @Test public void reusesMostBlocksWhenNothingChanged() throws Exception {
        byte[] neu = res("delta_new.bin");
        BlockSync.Control c = BlockSync.parse(res("blocks.ctl"));
        int[] src = BlockSync.plan(neu, c);   // 舊檔就是新檔：所有完整區塊都找得到，只剩尾巴要下載
        for (int s : src) assertTrue(s >= 0);
        List<long[]> r = BlockSync.ranges(c, src, 0);
        assertTrue(BlockSync.totalBytes(r) < c.blockSize);
    }

    @Test public void findsBlocksAtShiftedOffsets() throws Exception {
        byte[] neu = res("delta_new.bin");
        byte[] old = new byte[neu.length + 77];   // 前面多了 77 個位元組：區塊位置全部偏移
        System.arraycopy(neu, 0, old, 77, neu.length);
        BlockSync.Control c = BlockSync.parse(res("blocks.ctl"));
        int[] src = BlockSync.plan(old, c);
        for (int i = 0; i < c.count; i++) assertEquals(i * c.blockSize + 77, src[i]);
    }

    @Test public void rejectsBrokenControlFiles() throws Exception {
        byte[] ctl = res("blocks.ctl");
        assertNull(BlockSync.parse(new byte[]{1, 2, 3}));
        assertNull(BlockSync.parse(java.util.Arrays.copyOf(ctl, ctl.length - 5)));
        byte[] bad = ctl.clone();
        bad[0] = 'X';
        assertNull(BlockSync.parse(bad));
    }
}
