package com.phoneai.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.EOFException;
import java.io.IOException;

import org.junit.Test;

public class ResumeTest {
    @Test
    public void fingerprintIsStableAndSensitive() {
        byte[] head = "hello world".getBytes();
        String a = Resume.fingerprint(head, head.length, 100, "a.zip");
        assertEquals(a, Resume.fingerprint(head, head.length, 100, "a.zip"));
        assertTrue(Resume.validFp(a));
        assertNotEquals(a, Resume.fingerprint(head, head.length, 101, "a.zip"));
        assertNotEquals(a, Resume.fingerprint(head, head.length, 100, "b.zip"));
        assertNotEquals(a, Resume.fingerprint("hello worle".getBytes(), head.length, 100, "a.zip"));
        assertEquals(a, Resume.fingerprint(head, head.length, 100, "a.zip"));
    }

    @Test
    public void onlyTheDeclaredLengthOfTheHeadCounts() {
        byte[] buf = new byte[20];
        System.arraycopy("hello".getBytes(), 0, buf, 0, 5);
        assertEquals(Resume.fingerprint("hello".getBytes(), 5, 9, "n"), Resume.fingerprint(buf, 5, 9, "n"));
    }

    @Test
    public void fingerprintAndPartNamesAreSafe() {
        assertFalse(Resume.validFp(null));
        assertFalse(Resume.validFp("../../etc/passwd"));
        assertFalse(Resume.validFp("ABCDEF0123456789ABCDEF0123456789"));   // 只收小寫十六進位
        assertFalse(Resume.validFp("abc"));
        String fp = "0123456789abcdef0123456789abcdef";
        assertTrue(Resume.validFp(fp));
        String n = Resume.partName("peer-1", fp);
        assertTrue(n, n.startsWith(".resume-") && n.endsWith(fp + ".part") && !n.contains("/") && !n.contains(".."));
        assertNotEquals(n, Resume.partName("peer-2", fp));   // 不同裝置的半成品分開
        assertEquals(n, Resume.partName("peer-1", fp));
    }

    @Test
    public void offsetRules() {
        assertTrue(Resume.offsetOk(0, 0, 100));
        assertTrue(Resume.offsetOk(55, 0, 100));        // 要求重頭來：不管有沒有半成品都可以
        assertTrue(Resume.offsetOk(40, 40, 100));
        assertTrue(Resume.offsetOk(100, 100, 100));     // 其實已經傳完，只差收尾
        assertFalse(Resume.offsetOk(40, 50, 100));      // 半成品長度對不上
        assertFalse(Resume.offsetOk(0, 10, 100));       // 沒有半成品卻要接著傳
        assertFalse(Resume.offsetOk(150, 150, 100));    // 超過總大小
        assertFalse(Resume.offsetOk(10, -1, 100));
    }

    @Test
    public void retryableErrors() {
        assertTrue(Resume.retryable(new java.net.SocketException("Connection reset")));
        assertTrue(Resume.retryable(new java.net.SocketTimeoutException("Read timed out")));
        assertTrue(Resume.retryable(new EOFException()));
        assertTrue(Resume.retryable(new IOException("failed to connect to /1.2.3.4 (port 47321)")));
        assertTrue(Resume.retryable(new IOException("wrapped", new java.net.ConnectException("refused"))));
        assertTrue(Resume.retryable(new IOException("Broken pipe")));
        assertFalse(Resume.retryable(new IOException("校驗碼不符：對方存下的檔案和送出的不一樣")));
        assertFalse(Resume.retryable(new IOException("對方儲存空間不夠：現在剩 1 MB")));
        assertFalse(Resume.retryable(new IOException("409 offset mismatch")));
        assertTrue("舊連線還占著半成品：等一下就好，要自動重試", Resume.retryable(new IOException("409 busy: same upload in progress")));
        assertFalse(Resume.retryable(null));
    }

    @Test
    public void backoffGrowsAndIsCapped() {
        assertEquals(2000, Resume.backoffMs(1));
        assertEquals(4000, Resume.backoffMs(2));
        assertEquals(10000, Resume.backoffMs(9));
        assertEquals(2000, Resume.backoffMs(0));
    }
}
