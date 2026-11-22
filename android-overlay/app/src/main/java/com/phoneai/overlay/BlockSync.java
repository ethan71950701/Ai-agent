package com.phoneai.overlay;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * 區塊式增量更新（類似 zsync）：發布時附一個「區塊指紋檔」（android-overlay/tools/make_blocks.py），
 * 手機拿它去掃描自己已安裝的 APK（任何舊版本都行），找得到的區塊直接複用，只用 HTTP Range 下載缺的部分。
 * 不像補丁需要「剛好是上一版」，隔幾十版也能用。這個類別只放純邏輯（可單元測試）；網路與檔案在 Updater。
 */
final class BlockSync {
    private BlockSync() { }

    static final class Control {
        int blockSize, count;   // count＝完整區塊數
        long size;              // 新檔案大小
        int[] weak;
        long[] strong;
    }

    private static final long MAX_SIZE = 400L * 1024 * 1024;

    /** 解析指紋檔；格式不對、數字不合理回 null。 */
    static Control parse(byte[] b) {
        try {
            if (b.length < 24 || !"VXBLK1\0\0".equals(new String(b, 0, 8, StandardCharsets.ISO_8859_1))) return null;
            ByteBuffer h = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
            h.position(8);
            Control c = new Control();
            c.blockSize = h.getInt();
            c.size = h.getLong();
            c.count = h.getInt();
            if (c.blockSize < 512 || c.blockSize > 1 << 20 || c.size <= 0 || c.size > MAX_SIZE || c.count != c.size / c.blockSize) return null;
            if (b.length != 24 + (long) c.count * 12) return null;
            c.weak = new int[c.count];
            c.strong = new long[c.count];
            ByteBuffer big = ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN);
            for (int i = 0; i < c.count; i++) {
                int p = 24 + i * 12;
                c.weak[i] = h.getInt(p);
                c.strong[i] = big.getLong(p + 4);
            }
            return c;
        } catch (Exception e) {
            return null;
        }
    }

    /** 新檔案共有幾個區塊（含最後不足一塊的尾巴）。 */
    static int totalBlocks(Control c) { return (int) ((c.size + c.blockSize - 1) / c.blockSize); }

    private static long strong(MessageDigest md, byte[] old, int pos, int len) {
        md.reset();
        md.update(old, pos, len);
        byte[] d = md.digest();
        long v = 0;
        for (int i = 0; i < 8; i++) v = (v << 8) | (d[i] & 255);
        return v;
    }

    /**
     * 掃描舊檔：回傳每個完整區塊在舊檔裡的位置（找不到是 -1）。滾動弱校驗＋MD5 前 8 位元組確認。
     */
    static int[] plan(byte[] old, Control c) {
        int B = c.blockSize;
        int[] src = new int[c.count];
        java.util.Arrays.fill(src, -1);
        if (old.length < B || c.count == 0) return src;
        HashMap<Integer, List<Integer>> map = new HashMap<>();
        long[] bloom = new long[1 << 16];   // 2^22 位元的過濾表：多數位置一眼就知道不是任何區塊，不用查 HashMap
        for (int i = 0; i < c.count; i++) {
            map.computeIfAbsent(c.weak[i], k -> new ArrayList<>()).add(i);
            int k = bloomKey(c.weak[i]);
            bloom[k >>> 6] |= 1L << (k & 63);
        }
        MessageDigest md;
        try { md = MessageDigest.getInstance("MD5"); } catch (Exception e) { return src; }
        int pos = 0, a = 0, b = 0;
        boolean have = false;
        while (pos + B <= old.length) {
            if (!have) {
                a = 0;
                b = 0;
                for (int j = 0; j < B; j++) { int x = old[pos + j] & 255; a += x; b += (B - j) * x; }
                have = true;
            }
            int w = (a & 0xffff) | ((b & 0xffff) << 16);
            boolean matched = false;
            int k = bloomKey(w);
            if ((bloom[k >>> 6] & (1L << (k & 63))) != 0) {
                List<Integer> cand = map.get(w);
                if (cand != null) {
                    long s = strong(md, old, pos, B);
                    for (int idx : cand) {
                        if (c.strong[idx] == s) {
                            matched = true;
                            if (src[idx] < 0) src[idx] = pos;
                        }
                    }
                }
            }
            if (matched) { pos += B; have = false; continue; }
            if (pos + B >= old.length) break;
            int out = old[pos] & 255, in = old[pos + B] & 255;
            a = a - out + in;
            b = b - B * out + a;
            pos++;
        }
        return src;
    }

    private static int bloomKey(int w) { return (w ^ (w >>> 16)) & 0x3FFFFF; }

    /** 要下載的區間（新檔案的位元組範圍 [起, 迄)）。缺的區塊與尾巴合併；相隔不超過 mergeGap 的區間也併在一起（少幾次請求）。 */
    static List<long[]> ranges(Control c, int[] src, int mergeGap) {
        List<long[]> out = new ArrayList<>();
        int total = totalBlocks(c);
        for (int i = 0; i < total; i++) {
            boolean missing = i >= c.count || src[i] < 0;
            if (!missing) continue;
            long s = (long) i * c.blockSize, e = Math.min(c.size, s + c.blockSize);
            long[] last = out.isEmpty() ? null : out.get(out.size() - 1);
            if (last != null && s - last[1] <= mergeGap) last[1] = e;
            else out.add(new long[]{s, e});
        }
        return out;
    }

    static long totalBytes(List<long[]> r) {
        long t = 0;
        for (long[] x : r) t += x[1] - x[0];
        return t;
    }

    /** 拼出新檔案：找得到的區塊從舊檔複製，其他從下載的區間資料取（data.get(i) 對應 ranges.get(i)）。 */
    static void assemble(byte[] old, Control c, int[] src, List<long[]> ranges, List<byte[]> data, OutputStream out) throws IOException {
        int total = totalBlocks(c), r = 0;
        for (int i = 0; i < total; i++) {
            long s = (long) i * c.blockSize;
            int len = (int) Math.min(c.blockSize, c.size - s);
            if (i < c.count && src[i] >= 0) { out.write(old, src[i], len); continue; }
            while (r < ranges.size() && ranges.get(r)[1] <= s) r++;
            if (r >= ranges.size() || ranges.get(r)[0] > s) throw new IOException("缺少區塊 " + i);
            out.write(data.get(r), (int) (s - ranges.get(r)[0]), len);
        }
    }
}
