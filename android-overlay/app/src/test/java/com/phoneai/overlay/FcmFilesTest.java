package com.phoneai.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FcmFilesTest {
    static final String CONFIG = "{\"project_info\":{\"project_number\":\"123\",\"project_id\":\"vertex-test-8a49c\"},\"client\":[{\"client_info\":{\"mobilesdk_app_id\":\"1:123:android:abc\",\"android_client_info\":{\"package_name\":\"com.phoneai.overlay\"}},\"api_key\":[{\"current_key\":\"AIzaXXXX\"}]}]}";
    static final String KEY = "{\"type\":\"service_account\",\"project_id\":\"vertex-test-8a49c\",\"private_key\":\"-----BEGIN PRIVATE KEY-----\\nabc\\n-----END PRIVATE KEY-----\\n\",\"client_email\":\"x@vertex-test-8a49c.iam.gserviceaccount.com\"}";

    @Test
    public void tellsTheTwoFilesApart() {
        assertEquals(FcmFiles.Kind.CONFIG, FcmFiles.classify(CONFIG));
        assertEquals(FcmFiles.Kind.SERVICE_ACCOUNT, FcmFiles.classify(KEY));
        assertEquals(FcmFiles.Kind.UNKNOWN, FcmFiles.classify("{}"));
        assertEquals(FcmFiles.Kind.UNKNOWN, FcmFiles.classify("not json"));
        assertEquals(FcmFiles.Kind.UNKNOWN, FcmFiles.classify("{\"type\":\"service_account\"}"));   // 缺 private_key
    }

    @Test
    public void configProblems() {
        assertNull(FcmFiles.configProblem(CONFIG));
        assertNotNull(FcmFiles.configProblem("{}"));
        assertTrue(FcmFiles.configProblem(CONFIG.replace("com.phoneai.overlay", "com.other.app")).contains("套件名稱"));
        assertTrue(FcmFiles.configProblem(CONFIG.replace("AIzaXXXX", "")).contains("API"));
    }

    @Test
    public void projectsMustMatch() {
        assertNull(FcmFiles.mismatch(CONFIG, KEY));
        assertTrue(FcmFiles.mismatch(CONFIG, KEY.replace("vertex-test-8a49c\",\"private", "other-project\",\"private")).contains("同一個"));
        assertNull(FcmFiles.mismatch(null, KEY));
        assertNull(FcmFiles.mismatch(CONFIG, null));
        assertEquals("vertex-test-8a49c", FcmFiles.projectOfConfig(CONFIG));
        assertEquals("vertex-test-8a49c", FcmFiles.projectOfKey(KEY));
    }
}
