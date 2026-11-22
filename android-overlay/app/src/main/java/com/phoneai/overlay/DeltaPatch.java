package com.phoneai.overlay;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.zip.InflaterInputStream;

/**
 * 增量更新：用「舊 APK ＋ 補丁」拼出新 APK，只需要下載補丁（通常比完整 APK 小很多）。
 * 補丁格式 VXPATCH1（由 android-overlay/tools/make_delta.py 在 CI 產生，bsdiff 演算法、zlib 壓縮）。
 * 拼好的檔案一定要再用發布頁的 SHA-256 核對過才能安裝；任何一步出錯都回 false，由呼叫端改下載完整 APK。
 */
final class DeltaPatch {
    private DeltaPatch() { }

    private static final long MAX_NEW = 200L * 1024 * 1024;   // 新檔上限（防止壞補丁要求巨量記憶體）

    /** 純邏輯：old＋patch → out。成功 true；補丁壞掉、大小不合、舊檔不對（長度越界）都回 false。 */
    static boolean apply(byte[] old, byte[] patch, OutputStream out) {
        try {
            ByteBuffer h = ByteBuffer.wrap(patch).order(ByteOrder.LITTLE_ENDIAN);
            if (patch.length < 8 + 8 + 48 || !"VXPATCH1".equals(new String(patch, 0, 8, StandardCharsets.US_ASCII))) return false;
            h.position(8);
            long newSize = h.getLong();
            long[] raw = new long[3], comp = new long[3];
            for (int i = 0; i < 3; i++) { raw[i] = h.getLong(); comp[i] = h.getLong(); }
            if (newSize < 0 || newSize > MAX_NEW) return false;
            long off = h.position();
            DataInputStream[] s = new DataInputStream[3];
            for (int i = 0; i < 3; i++) {
                if (comp[i] < 0 || raw[i] < 0 || raw[i] > MAX_NEW * 4 || off + comp[i] > patch.length) return false;
                s[i] = new DataInputStream(new InflaterInputStream(new java.io.ByteArrayInputStream(patch, (int) off, (int) comp[i])));
                off += comp[i];
            }
            byte[] ctrl = new byte[24], buf = new byte[65536];
            long oldPos = 0, newPos = 0;
            while (newPos < newSize) {
                s[0].readFully(ctrl);
                ByteBuffer c = ByteBuffer.wrap(ctrl).order(ByteOrder.LITTLE_ENDIAN);
                long x = signMag(c.getLong()), y = signMag(c.getLong()), z = signMag(c.getLong());
                if (x < 0 || y < 0 || newPos + x + y > newSize) return false;
                for (long done = 0; done < x; ) {   // 差異區塊：新 = 舊 + 差異
                    int n = (int) Math.min(buf.length, x - done);
                    s[1].readFully(buf, 0, n);
                    for (int i = 0; i < n; i++) {
                        long p = oldPos + done + i;
                        if (p >= 0 && p < old.length) buf[i] += old[(int) p];
                    }
                    out.write(buf, 0, n);
                    done += n;
                }
                newPos += x;
                oldPos += x;
                for (long done = 0; done < y; ) {   // 額外區塊：原樣複製
                    int n = (int) Math.min(buf.length, y - done);
                    s[2].readFully(buf, 0, n);
                    out.write(buf, 0, n);
                    done += n;
                }
                newPos += y;
                oldPos += z;
            }
            return newPos == newSize;
        } catch (Exception e) {
            return false;
        }
    }

    private static long signMag(long v) { return (v & Long.MIN_VALUE) != 0 ? -(v & Long.MAX_VALUE) : v; }

    /** 檔案版：舊 APK 檔＋補丁檔 → 新檔，並核對 SHA-256（小寫十六進位）；不符就刪掉輸出、回 false。 */
    static boolean applyFiles(File oldApk, File patch, File out, String expectedSha256) {
        try {
            if (oldApk.length() > MAX_NEW) return false;
            byte[] old = readAll(oldApk), p = readAll(patch);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (OutputStream fo = new FileOutputStream(out); java.security.DigestOutputStream dos = new java.security.DigestOutputStream(fo, md)) {
                if (!apply(old, p, dos)) { out.delete(); return false; }
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format(java.util.Locale.US, "%02x", b));
            if (!sb.toString().equalsIgnoreCase(expectedSha256 == null ? "" : expectedSha256.trim())) { out.delete(); return false; }
            return true;
        } catch (Exception e) {
            out.delete();
            return false;
        }
    }

    private static byte[] readAll(File f) throws IOException {
        try (InputStream in = new java.io.FileInputStream(f)) {
            ByteArrayOutputStream o = new ByteArrayOutputStream((int) Math.max(32, f.length()));
            byte[] b = new byte[65536];
            int n;
            while ((n = in.read(b)) > 0) o.write(b, 0, n);
            return o.toByteArray();
        }
    }
}
