package com.phoneai.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

public class ChecksumTest {
    @Test
    public void knownVectors() throws Exception {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                Checksum.hex(Checksum.newDigest().digest("abc".getBytes(StandardCharsets.UTF_8))));
        File f = File.createTempFile("sha", ".bin");
        try {
            Files.write(f.toPath(), "abc".getBytes(StandardCharsets.UTF_8));
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Checksum.sha256(f));
            Files.write(f.toPath(), new byte[0]);
            assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Checksum.sha256(f));
        } finally { f.delete(); }
    }

    @Test
    public void streamingMatchesWholeFile() throws Exception {
        byte[] data = new byte[300_000];
        new java.util.Random(7).nextBytes(data);
        File f = File.createTempFile("sha", ".bin");
        try {
            Files.write(f.toPath(), data);
            java.security.MessageDigest md = Checksum.newDigest();
            for (int off = 0; off < data.length; off += 32 * 1024) md.update(data, off, Math.min(32 * 1024, data.length - off));
            assertEquals(Checksum.sha256(f), Checksum.hex(md.digest()));
        } finally { f.delete(); }
    }

    @Test
    public void comparisonRules() {
        assertTrue(Checksum.same("ABCD", "abcd"));
        assertFalse(Checksum.same("abcd", "abce"));
        assertFalse(Checksum.same("", ""));
        assertFalse(Checksum.same(null, "abcd"));
        assertEquals("ba7816bf", Checksum.shortOf("ba7816bf8f01cfea"));
        assertEquals("ab", Checksum.shortOf("ab"));
        assertEquals("", Checksum.shortOf(null));
    }

    @Test
    public void cacheIsInvalidatedWhenTheFileChanges() throws Exception {
        Checksum.Cache c = new Checksum.Cache(2);
        File f = File.createTempFile("sha", ".bin");
        try {
            Files.write(f.toPath(), "one".getBytes(StandardCharsets.UTF_8));
            c.put(f, "H1");
            assertEquals("H1", c.get(f));
            Files.write(f.toPath(), "longer content".getBytes(StandardCharsets.UTF_8));   // 大小變了：舊的值不能用
            assertNull(c.get(f));
        } finally { f.delete(); }
    }

    @Test
    public void cacheKeepsOnlyTheMostRecent() throws Exception {
        Checksum.Cache c = new Checksum.Cache(2);
        File a = File.createTempFile("aaa", ".bin"), b = File.createTempFile("bbb", ".bin"), d = File.createTempFile("ddd", ".bin");
        try {
            c.put(a, "A"); c.put(b, "B"); c.put(d, "D");
            assertNull(c.get(a));
            assertEquals("B", c.get(b));
            assertEquals("D", c.get(d));
        } finally { a.delete(); b.delete(); d.delete(); }
    }
}
