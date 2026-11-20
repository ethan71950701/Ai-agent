package com.phoneai.overlay;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FcmTokenTest {
    private static final String OLD = "a".repeat(60) + ":APA91bOld_token-1";
    private static final String NEW = "b".repeat(60) + ":APA91bNew_token-2";

    @Test
    public void aChangedValidTokenReplacesTheStoredOne() {
        assertTrue(FcmLab.shouldAdopt(OLD, NEW));
        assertTrue("原本沒有就收下", FcmLab.shouldAdopt(null, NEW));
    }

    @Test
    public void sameEmptyOrGarbageTokensAreIgnored() {
        assertFalse(FcmLab.shouldAdopt(OLD, OLD));
        assertFalse("對方沒有 token（回空的）不能把我們存的洗掉", FcmLab.shouldAdopt(OLD, null));
        assertFalse(FcmLab.shouldAdopt(OLD, ""));
        assertFalse(FcmLab.shouldAdopt(OLD, "short"));
        assertFalse(FcmLab.shouldAdopt(OLD, NEW + "\n; drop"));
    }

    @Test
    public void goneTokenErrorsAreRecognised() {
        assertTrue(FcmLab.tokenGone("HTTP 404：NOT_FOUND — Requested entity was not found."));
        assertTrue(FcmLab.tokenGone("HTTP 404：UNREGISTERED — x"));
        assertFalse(FcmLab.tokenGone("HTTP 403：PERMISSION_DENIED — x"));
        assertFalse(FcmLab.tokenGone("java.net.SocketTimeoutException"));
        assertFalse(FcmLab.tokenGone(null));
    }
}
