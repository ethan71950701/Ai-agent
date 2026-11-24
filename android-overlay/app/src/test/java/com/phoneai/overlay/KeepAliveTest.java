package com.phoneai.overlay;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class KeepAliveTest {
    @Test public void picksAutostartPageByManufacturer() {
        assertArrayEquals(new String[]{"com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"}, KeepAlive.autostartComponent("Xiaomi"));
        assertNotNull(KeepAlive.autostartComponent("HUAWEI"));
        assertNotNull(KeepAlive.autostartComponent("OPPO"));
        assertNotNull(KeepAlive.autostartComponent("vivo"));
        assertNull(KeepAlive.autostartComponent("samsung"));   // 三星沒有專用的自啟動頁：改開 App 資訊
        assertNull(KeepAlive.autostartComponent(null));
    }
}
