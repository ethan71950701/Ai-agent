package com.phoneai.overlay;

/**
 * 助理面板的路由（App 內）：固定指令用 root 直接執行；操作手機與聊天分別交給 Brain 的流程（AgentLoop、ChatEngine）。
 */
final class Assist {
    static final int FIXED = 1, NONE = 2;

    static final class Route {
        final int kind;
        final String cmd, reply;
        final boolean confirm;
        Route(int kind, String cmd, String reply, boolean confirm) { this.kind = kind; this.cmd = cmd; this.reply = reply; this.confirm = confirm; }
    }

    private static final class Cmd {
        final String[] keys; final String cmd, reply; final boolean confirm;
        Cmd(String[] keys, String cmd, String reply, boolean confirm) { this.keys = keys; this.cmd = cmd; this.reply = reply; this.confirm = confirm; }
    }

    // 以下指令的 shell 寫法是依 Android 一般做法寫的，還沒在這支手機上實測（失敗時回覆仍會說已送出，請把畫面貼回來）
    private static final Cmd[] COMMANDS = {
            new Cmd(new String[]{"開wifi", "打開wifi", "開啟wifi", "turnonwifi", "wifion", "enablewifi", "switchonwifi", "openwifi"}, "svc wifi enable", "已送出開啟 Wi-Fi", false),
            new Cmd(new String[]{"關wifi", "關閉wifi", "turnoffwifi", "wifioff", "disablewifi", "switchoffwifi", "closewifi"}, "svc wifi disable", "已送出關閉 Wi-Fi", false),
            new Cmd(new String[]{"開藍牙", "打開藍牙", "開啟藍牙", "開藍芽", "打開藍芽", "開啟藍芽", "turnonbluetooth", "bluetoothon", "enablebluetooth", "switchonbluetooth"}, "svc bluetooth enable", "已送出開啟藍牙", false),
            new Cmd(new String[]{"關藍牙", "關閉藍牙", "關藍芽", "關閉藍芽", "turnoffbluetooth", "bluetoothoff", "disablebluetooth", "switchoffbluetooth"}, "svc bluetooth disable", "已送出關閉藍牙", false),
            new Cmd(new String[]{"回桌面", "回主畫面", "gohome", "gotothehomescreen", "backtohome"}, "input keyevent 3", "已回到主畫面", false),
            new Cmd(new String[]{"返回", "上一頁", "goback", "pressback", "backbutton"}, "input keyevent 4", "已按返回", false),
            new Cmd(new String[]{"音量大", "大聲", "volumeup", "turnupthevolume", "raisethevolume", "raisevolume"}, "input keyevent 24", "已調大音量", false),
            new Cmd(new String[]{"音量小", "小聲", "volumedown", "turndownthevolume", "lowerthevolume", "lowervolume"}, "input keyevent 25", "已調小音量", false),
            new Cmd(new String[]{"開勿擾", "打開勿擾", "開啟勿擾", "turnondonotdisturb", "donotdisturbon", "dndon", "enabledonotdisturb"}, "cmd notification set_dnd priority", "已開啟勿擾模式", false),
            new Cmd(new String[]{"關勿擾", "關閉勿擾", "turnoffdonotdisturb", "donotdisturboff", "dndoff", "disabledonotdisturb"}, "cmd notification set_dnd off", "已關閉勿擾模式", false),
            new Cmd(new String[]{"開自動旋轉", "打開自動旋轉", "開啟自動旋轉", "turnonautorotate", "autorotateon", "enableautorotate"}, "settings put system accelerometer_rotation 1", "已開啟自動旋轉", false),
            new Cmd(new String[]{"關自動旋轉", "關閉自動旋轉", "鎖定直向", "turnoffautorotate", "autorotateoff", "disableautorotate", "lockportrait"}, "settings put system accelerometer_rotation 0", "已關閉自動旋轉", false),
            new Cmd(new String[]{"開深色模式", "打開深色模式", "開啟深色模式", "turnondarkmode", "darkmodeon", "enabledarkmode"}, "cmd uimode night yes", "已開啟深色模式", false),
            new Cmd(new String[]{"關深色模式", "關閉深色模式", "開淺色模式", "turnoffdarkmode", "darkmodeoff", "disabledarkmode", "turnonlightmode"}, "cmd uimode night no", "已關閉深色模式", false),
            new Cmd(new String[]{"開省電", "打開省電", "開啟省電", "turnonbatterysaver", "batterysaveron", "enablebatterysaver", "turnonpowersaving", "powersavingon"}, "settings put global low_power 1", "已開啟省電模式", false),
            new Cmd(new String[]{"關省電", "關閉省電", "turnoffbatterysaver", "batterysaveroff", "disablebatterysaver", "turnoffpowersaving", "powersavingoff"}, "settings put global low_power 0", "已關閉省電模式", false),
            // 飛航模式會斷掉網路（連 AI 都連不上），所以先跳確認框
            new Cmd(new String[]{"開飛航", "打開飛航", "開啟飛航", "turnonairplanemode", "airplanemodeon", "enableairplanemode", "flightmodeon"}, "cmd connectivity airplane-mode enable", "已開啟飛航模式", true),
            new Cmd(new String[]{"關飛航", "關閉飛航", "turnoffairplanemode", "airplanemodeoff", "disableairplanemode", "flightmodeoff"}, "cmd connectivity airplane-mode disable", "已關閉飛航模式", true),
    };


    private Assist() { }

    private static String norm(String t) {
        return (t == null ? "" : t).replaceAll("[\\s\\-_，。,.]", "").toLowerCase(java.util.Locale.ROOT);
    }

    static Route fixed(int i) { return new Route(FIXED, COMMANDS[i].cmd, L.t(COMMANDS[i].reply), COMMANDS[i].confirm); }
    static int commandCount() { return COMMANDS.length; }
    private static final String[] EN_LABELS = {"turn Wi-Fi on", "turn Wi-Fi off", "turn Bluetooth on", "turn Bluetooth off", "go to the home screen",
            "go back", "volume up", "volume down", "Do Not Disturb on", "Do Not Disturb off", "auto-rotate on", "auto-rotate off",
            "dark mode on", "dark mode off", "battery saver on", "battery saver off", "airplane mode on", "airplane mode off"};

    static String commandKey(int i) { return L.en() && i < EN_LABELS.length ? EN_LABELS[i] : COMMANDS[i].keys[0]; }
    static String commandReply(int i) { return L.t(COMMANDS[i].reply); }

    /** 關鍵字命中固定指令（最長的那個關鍵字優先）回 FIXED；沒命中回 NONE，交給 Haiku 路由判斷。 */
    static Route route(String text) {
        String t = norm(text);
        Cmd best = null;
        int bestLen = 0;
        for (Cmd c : COMMANDS)
            for (String k : c.keys)
                if (!t.isEmpty() && t.contains(k) && k.length() > bestLen) { best = c; bestLen = k.length(); }
        if (best != null) return new Route(FIXED, best.cmd, L.t(best.reply), best.confirm);
        return new Route(NONE, null, null, false);
    }
}
