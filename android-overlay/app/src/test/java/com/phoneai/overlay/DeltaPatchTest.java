package com.phoneai.overlay;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;

import org.junit.Test;

public class DeltaPatchTest {
    private static byte[] res(String n) throws Exception {
        try (InputStream in = DeltaPatchTest.class.getClassLoader().getResourceAsStream(n)) {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int k;
            while ((k = in.read(b)) > 0) o.write(b, 0, k);
            return o.toByteArray();
        }
    }

    @Test public void rebuildsNewFileFromOldPlusPatch() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(DeltaPatch.apply(res("delta_old.bin"), res("delta.patch"), out));
        assertArrayEquals(res("delta_new.bin"), out.toByteArray());
    }

    @Test public void filesVariantChecksSha256() throws Exception {
        java.io.File d = java.nio.file.Files.createTempDirectory("dp").toFile();
        java.io.File o = new java.io.File(d, "old"), p = new java.io.File(d, "p"), n = new java.io.File(d, "n");
        java.nio.file.Files.write(o.toPath(), res("delta_old.bin"));
        java.nio.file.Files.write(p.toPath(), res("delta.patch"));
        StringBuilder sb = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(res("delta_new.bin"))) sb.append(String.format("%02x", b));
        assertTrue(DeltaPatch.applyFiles(o, p, n, sb.toString()));
        assertFalse(DeltaPatch.applyFiles(o, p, n, "00" + sb.substring(2)));   // 雜湊不符：不能用
        assertFalse(n.exists());                                                // 而且輸出檔被刪掉
    }

    @Test public void wrongOldFileOrBrokenPatchFails() throws Exception {
        byte[] patch = res("delta.patch");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] wrongOld = res("delta_old.bin").clone();
        for (int i = 0; i < wrongOld.length; i += 3) wrongOld[i]++;
        DeltaPatch.apply(wrongOld, patch, out);   // 舊檔不對：不丟例外；結果跟新檔不同（呼叫端靠 SHA-256 擋下）
        assertFalse(java.util.Arrays.equals(res("delta_new.bin"), out.toByteArray()));
        assertFalse(DeltaPatch.apply(res("delta_old.bin"), new byte[]{1, 2, 3}, new ByteArrayOutputStream()));
        byte[] cut = java.util.Arrays.copyOf(patch, patch.length - 40);
        assertFalse(DeltaPatch.apply(res("delta_old.bin"), cut, new ByteArrayOutputStream()));
    }
}
