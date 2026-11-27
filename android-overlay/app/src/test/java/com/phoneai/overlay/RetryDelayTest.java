package com.phoneai.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** ChatEngine.retryDelayMs：伺服器忙線（Gemini 503 等）要多試幾次、等久一點；其他錯誤照舊。 */
public class RetryDelayTest {

    private static ChatEngine.HttpStatus http(int code) { return new ChatEngine.HttpStatus(code, "HTTP " + code); }

    @Test public void busy503RetriesThreeTimesWithBackoff() {
        Exception e = http(503);
        assertEquals(2000, ChatEngine.retryDelayMs(e, 1, false));
        assertEquals(5000, ChatEngine.retryDelayMs(e, 2, false));
        assertEquals(10000, ChatEngine.retryDelayMs(e, 3, false));
        assertEquals(-1, ChatEngine.retryDelayMs(e, 4, false));
    }

    @Test public void otherBusyCodesAlsoRetry() {
        for (int c : new int[]{502, 504, 529}) {
            assertTrue(ChatEngine.serverBusy(http(c)));
            assertEquals(2000, ChatEngine.retryDelayMs(http(c), 1, false));
        }
    }

    @Test public void retryAfterHeaderIsHonouredButCapped() {
        assertEquals(3000, ChatEngine.retryDelayMs(new ChatEngine.HttpStatus(503, "x", 3000), 1, false));
        assertEquals(500, ChatEngine.retryDelayMs(new ChatEngine.HttpStatus(503, "x", 0), 1, false));
        assertEquals(-1, ChatEngine.retryDelayMs(new ChatEngine.HttpStatus(503, "x", 60000), 1, false));
    }

    @Test public void parsesRetryAfter() {
        assertEquals(7000, ChatEngine.retryAfterMs(" 7 "));
        assertEquals(-1, ChatEngine.retryAfterMs(null));
        assertEquals(-1, ChatEngine.retryAfterMs("Wed, 21 Oct 2026 07:28:00 GMT"));
    }

    @Test public void permanentErrorsDoNotRetry() {
        for (int c : new int[]{400, 401, 403, 404, 429}) {
            assertFalse(ChatEngine.serverBusy(http(c)));
            assertEquals(-1, ChatEngine.retryDelayMs(http(c), 1, false));
        }
        assertEquals(-1, ChatEngine.retryDelayMs(new Exception("金鑰錯"), 1, false));
    }

    @Test public void networkErrorsKeepOldLimits() {
        Exception io = new java.io.IOException("timeout");
        assertEquals(500, ChatEngine.retryDelayMs(io, 1, false));
        assertEquals(-1, ChatEngine.retryDelayMs(io, 2, false));
        assertEquals(500, ChatEngine.retryDelayMs(io, 1, true));
        assertEquals(1000, ChatEngine.retryDelayMs(io, 2, true));
        assertEquals(-1, ChatEngine.retryDelayMs(io, 3, true));
        assertEquals(500, ChatEngine.retryDelayMs(http(500), 1, false));
        assertEquals(-1, ChatEngine.retryDelayMs(http(500), 2, false));
    }
}
