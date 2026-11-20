package com.phoneai.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class FcmHealthTest {
    private FcmHealth.Input good() {
        FcmHealth.Input in = new FcmHealth.Input();
        in.hasConfig = true; in.hasKey = true; in.notificationsOn = true; in.batteryUnrestricted = true;
        in.myToken = "t"; in.lanIp = "192.168.1.5"; in.paired = 2; in.pairedWithToken = 2; in.manufacturer = "Xiaomi";
        return in;
    }

    private FcmHealth.Level level(List<FcmHealth.Item> l, String label) {
        for (FcmHealth.Item i : l) if (i.label.equals(label)) return i.level;
        throw new AssertionError("no item " + label);
    }

    @Test
    public void everythingFineIsAllGood() {
        List<FcmHealth.Item> l = FcmHealth.evaluate(good());
        assertTrue(FcmHealth.allGood(l));
        assertEquals(FcmHealth.Level.OK, level(l, "Firebase 設定檔"));
        assertEquals(FcmHealth.Level.INFO, level(l, "自啟動"));
    }

    @Test
    public void eachProblemIsReportedWithAHint() {
        FcmHealth.Input in = good();
        in.hasConfig = false; in.hasKey = false; in.myToken = null; in.notificationsOn = false; in.batteryUnrestricted = false;
        in.paired = 0; in.pairedWithToken = 0; in.lanIp = null;
        List<FcmHealth.Item> l = FcmHealth.evaluate(in);
        assertFalse(FcmHealth.allGood(l));
        assertEquals(FcmHealth.Level.BAD, level(l, "Firebase 設定檔"));
        assertEquals(FcmHealth.Level.WARN, level(l, "發送金鑰"));
        assertEquals(FcmHealth.Level.BAD, level(l, "這支手機的推播 token"));
        assertEquals(FcmHealth.Level.BAD, level(l, "通知權限"));
        assertEquals(FcmHealth.Level.WARN, level(l, "電池不受限制"));
        assertEquals(FcmHealth.Level.BAD, level(l, "已配對的裝置"));
        assertEquals(FcmHealth.Level.WARN, level(l, "Wi-Fi"));
        for (FcmHealth.Item i : l) if (i.level != FcmHealth.Level.OK) assertFalse(i.label, i.hint.isEmpty());
    }

    @Test
    public void missingKeyAloneIsNotFatal() {
        FcmHealth.Input in = good();
        in.hasKey = false;
        assertTrue(FcmHealth.allGood(FcmHealth.evaluate(in)));
    }

    @Test
    public void initErrorAndPartialTokens() {
        FcmHealth.Input in = good();
        in.initError = "bad options";
        in.pairedWithToken = 1;
        List<FcmHealth.Item> l = FcmHealth.evaluate(in);
        assertEquals(FcmHealth.Level.BAD, level(l, "Firebase 設定檔"));
        assertEquals(FcmHealth.Level.WARN, level(l, "已配對的裝置"));
    }

    @Test
    public void autostartHintFollowsTheBrand() {
        assertTrue(FcmHealth.autostartHint("Xiaomi").contains("自啟動"));
        assertTrue(FcmHealth.autostartHint("samsung").contains("休眠"));
        assertTrue(FcmHealth.autostartHint("OPPO").contains("自動啟動"));
        assertTrue(FcmHealth.autostartHint("vivo").contains("後台"));
        assertTrue(FcmHealth.autostartHint("HUAWEI").contains("應用啟動管理"));
        assertTrue(FcmHealth.autostartHint(null).contains("自啟動"));
        assertTrue(FcmHealth.autostartHint("Google").length() > 10);
    }
}
