package com.phoneai.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Assume;
import org.junit.Test;

public class ProcRunTest {
    private static Process sh(String script) throws Exception {
        Assume.assumeTrue(new java.io.File("/bin/sh").exists());
        return new ProcessBuilder("sh", "-c", script).redirectErrorStream(true).start();
    }

    @Test
    public void normalOutputAndExitCode() throws Exception {
        ProcRun.Text r = ProcRun.drain(sh("echo hello; exit 3"), 5000, 1024);
        assertEquals(3, r.code);
        assertEquals("hello\n", r.text);
    }

    @Test
    public void timeoutKillsTheProcessAndReturnsPromptly() throws Exception {
        long t0 = System.currentTimeMillis();
        ProcRun.Text r = ProcRun.drain(sh("echo start; sleep 30"), 400, 1024);
        long took = System.currentTimeMillis() - t0;
        assertEquals(ProcRun.TIMED_OUT, r.code);
        assertTrue(r.text.contains("start"));
        assertTrue(r.text.contains("逾時"));
        assertTrue("took " + took, took < 6000);
    }

    @Test
    public void outputIsCappedButProcessStillFinishes() throws Exception {
        ProcRun.Text r = ProcRun.drain(sh("yes abcdefghij | head -c 3000000"), 20000, 100000);
        assertEquals(0, r.code);
        assertEquals(100000, r.text.length());
    }

    @Test
    public void grandchildHoldingThePipeDoesNotHangTheCaller() throws Exception {
        long t0 = System.currentTimeMillis();
        ProcRun.Text r = ProcRun.drain(sh("sleep 30 & echo hi"), 5000, 1024);   // sh 立刻結束，但背景的 sleep 還握著輸出管道
        long took = System.currentTimeMillis() - t0;
        assertEquals(0, r.code);
        assertTrue(r.text.contains("hi"));
        assertTrue("took " + took, took < 6000);
    }

    @Test
    public void onlySuStartupProblemsAllowAnotherSuVariant() {
        assertFalse(ProcRun.suNotLaunched(0, ""));
        assertFalse(ProcRun.suNotLaunched(ProcRun.TIMED_OUT, ""));
        assertFalse("script failed but ran: must not re-run", ProcRun.suNotLaunched(1, "pm: unknown package"));
        assertFalse(ProcRun.suNotLaunched(2, "grep: no match"));
        assertTrue(ProcRun.suNotLaunched(127, "su: not found"));
        assertTrue(ProcRun.suNotLaunched(126, ""));
        assertTrue(ProcRun.suNotLaunched(1, "su: invalid option -- 'm'"));
        assertTrue(ProcRun.suNotLaunched(2, "Unknown option: -mm\nusage: su [options]"));
    }

    @Test
    public void timeoutAlsoKillsChildProcesses() throws Exception {
        Assume.assumeTrue(new java.io.File("/proc/self").exists());
        ProcRun.Text r = ProcRun.drain(sh("sleep 30 & echo $!; wait"), 500, 1024);
        assertEquals(ProcRun.TIMED_OUT, r.code);
        String pid = r.text.trim().split("\\s+")[0];
        assertTrue("pid line: " + r.text, pid.matches("\\d+"));
        java.io.File proc = new java.io.File("/proc/" + pid);
        boolean gone = false;
        for (int k = 0; k < 40 && !gone; k++) {   // 最多等 4 秒讓系統收掉
            try {
                gone = !proc.exists() || new String(java.nio.file.Files.readAllBytes(new java.io.File(proc, "stat").toPath())).contains(") Z");
            } catch (java.io.IOException e) {
                gone = true;   // 檢查與讀取之間行程剛好被收掉 = 已結束
            }
            if (!gone) Thread.sleep(100);
        }
        assertTrue("the child sleep process must be killed with its parent", gone);
    }

    @Test
    public void binaryDrainReturnsBytesAndCaps() throws Exception {
        Assume.assumeTrue(new java.io.File("/bin/sh").exists());
        ProcRun.Bytes ok = ProcRun.drainBytes(new ProcessBuilder("sh", "-c", "head -c 5000 /dev/zero; echo warn >&2").start(), 5000, 100000);
        assertTrue(ok.ok());
        assertEquals(5000, ok.data.length);
        assertTrue(ok.err.contains("warn"));
        ProcRun.Bytes big = ProcRun.drainBytes(new ProcessBuilder("sh", "-c", "head -c 300000 /dev/zero").start(), 5000, 100000);
        assertTrue(big.tooBig);
        assertFalse(big.ok());
        long t0 = System.currentTimeMillis();
        ProcRun.Bytes hung = ProcRun.drainBytes(new ProcessBuilder("sh", "-c", "sleep 30").start(), 400, 1000);
        assertTrue(hung.timedOut);
        assertFalse(hung.ok());
        assertTrue(System.currentTimeMillis() - t0 < 6000);
    }
}
