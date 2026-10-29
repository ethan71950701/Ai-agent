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
            new Cmd(new String[]{"開定位", "打開定位", "開啟定位", "开定位", "打开定位", "开启定位", "turnonlocation", "locationon", "enablelocation"}, "cmd location set-location-enabled true", "已開啟定位", false),
            new Cmd(new String[]{"關定位", "關閉定位", "关定位", "关闭定位", "turnofflocation", "locationoff", "disablelocation"}, "cmd location set-location-enabled false", "已關閉定位", false),
            new Cmd(new String[]{"開nfc", "打開nfc", "開啟nfc", "开nfc", "打开nfc", "开启nfc", "turnonnfc", "nfcon", "enablenfc"}, "svc nfc enable", "已開啟 NFC", false),
            new Cmd(new String[]{"關nfc", "關閉nfc", "关nfc", "关闭nfc", "turnoffnfc", "nfcoff", "disablenfc"}, "svc nfc disable", "已關閉 NFC", false),
            new Cmd(new String[]{"開行動數據", "打開行動數據", "開啟行動數據", "開數據", "開流量", "开行动数据", "打开行动数据", "开启行动数据", "开流量", "turnonmobiledata", "mobiledataon", "enablemobiledata"}, "svc data enable", "已開啟行動數據", false),
            new Cmd(new String[]{"關行動數據", "關閉行動數據", "關數據", "關流量", "关行动数据", "关闭行动数据", "关流量", "turnoffmobiledata", "mobiledataoff", "disablemobiledata"}, "svc data disable", "已關閉行動數據", true),
            new Cmd(new String[]{"開自動亮度", "打開自動亮度", "開啟自動亮度", "开自动亮度", "打开自动亮度", "turnonautobrightness", "autobrightnesson"}, "settings put system screen_brightness_mode 1", "已開啟自動亮度", false),
            new Cmd(new String[]{"關自動亮度", "關閉自動亮度", "关自动亮度", "关闭自动亮度", "turnoffautobrightness", "autobrightnessoff"}, "settings put system screen_brightness_mode 0", "已關閉自動亮度", false),
            new Cmd(new String[]{"亮度最低", "亮度調到最低", "亮度调到最低", "調到最暗", "调到最暗", "螢幕最暗", "屏幕最暗", "brightnessminimum", "minimumbrightness", "dimscreenfully"}, "settings put system screen_brightness_mode 0; settings put system screen_brightness 1", "已把亮度調到最低（自動亮度已關閉）", false),
            new Cmd(new String[]{"亮度最高", "亮度調到最高", "亮度调到最高", "調到最亮", "调到最亮", "螢幕最亮", "屏幕最亮", "brightnessmaximum", "maximumbrightness", "maxbrightness"}, "settings put system screen_brightness_mode 0; settings put system screen_brightness 255", "已把亮度調到最高（自動亮度已關閉）", false),
            new Cmd(new String[]{"打開通知欄", "下拉通知欄", "打開通知列", "下拉通知列", "打开通知栏", "下拉通知栏", "opennotificationshade", "pulldownnotifications"}, "cmd statusbar expand-notifications", "已打開通知欄", false),
            new Cmd(new String[]{"收起通知欄", "關閉通知欄", "收起通知列", "收起通知栏", "关闭通知栏", "closenotificationshade"}, "cmd statusbar collapse", "已收起通知欄", false),
            new Cmd(new String[]{"打開快速設定", "下拉快速設定", "打開控制中心", "打开快速设定", "打开控制中心", "openquicksettings"}, "cmd statusbar expand-settings", "已打開快速設定", false),
            new Cmd(new String[]{"截圖", "螢幕截圖", "截屏", "截图", "屏幕截图", "screenshot", "takeascreenshot"}, "input keyevent 120", "已送出截圖", false),
            new Cmd(new String[]{"鎖螢幕", "鎖定螢幕", "關螢幕", "熄屏", "锁屏", "锁定屏幕", "lockthescreen", "lockscreen", "turnoffthescreen"}, "input keyevent 223", "已鎖定螢幕", false),
            new Cmd(new String[]{"最近使用的app", "最近的app", "多工畫面", "多任務", "多任务", "recentapps", "appswitcher"}, "input keyevent 187", "已打開最近使用的 App", false),
    };


    private Assist() { }

    private static String norm(String t) {
        return (t == null ? "" : t).replaceAll("[\\s\\-_，。,.]", "").toLowerCase(java.util.Locale.ROOT);
    }

    /** 指令一覽用：每個固定指令的第一個說法、回覆文字、是否要確認。 */
    static java.util.List<String[]> describeFixed() {
        java.util.List<String[]> out = new java.util.ArrayList<>();
        for (Cmd c : COMMANDS) out.add(new String[]{c.keys[0], L.t(c.reply), c.confirm ? L.t("要確認") : ""});
        return out;
    }

    static Route fixed(int i) { return new Route(FIXED, COMMANDS[i].cmd, L.t(COMMANDS[i].reply), COMMANDS[i].confirm); }
    static int commandCount() { return COMMANDS.length; }

    private static final java.util.regex.Pattern ACTION_VERB = java.util.regex.Pattern.compile(
            "打開|打开|開啟|开启|進入|进入|前往|切換|切换|點擊|点击|點開|点开|滑動|滑动|回到|返回|去|"
                    + "\\b(open|go to|goto|tap|click|turn on|turn off|switch to|launch|enter)\\b");
    private static final java.util.regex.Pattern ACTION_OBJECT = java.util.regex.Pattern.compile(
            "頁面|页面|界面|畫面|画面|選單|菜單|菜单|設定|设定|設置|设置|螢幕|屏幕|按鈕|按钮|桌面|首頁|首页|通知列|"
                    + "wi-?fi|藍牙|蓝牙|\\b(settings?|screen|page|menu|home|bluetooth|app)\\b");

    /** 這句話明顯是要操作手機（有「打開、進入…」這類動作詞，又提到設定、頁面、Wi-Fi 這類畫面或項目）。路由說是聊天時用它補救。 */
    static boolean looksLikeAction(String t) {
        if (t == null) return false;
        String s = t.toLowerCase(java.util.Locale.ROOT);
        return ACTION_VERB.matcher(s).find() && ACTION_OBJECT.matcher(s).find();
    }

    /** 聊天助理的回答是在說「我不能操作手機」：使用者其實是要操作手機。 */
    static boolean refusesToOperate(String reply) {
        if (reply == null) return false;
        String s = reply.toLowerCase(java.util.Locale.ROOT);
        return s.contains("不能操作手機") || s.contains("不能操作手机") || s.contains("無法操作手機") || s.contains("无法操作手机")
                || s.contains("cannot operate") || s.contains("can't operate") || s.contains("unable to operate");
    }
    private static final String[] EN_LABELS = {"turn Wi-Fi on", "turn Wi-Fi off", "turn Bluetooth on", "turn Bluetooth off", "go to the home screen",
            "go back", "volume up", "volume down", "Do Not Disturb on", "Do Not Disturb off", "auto-rotate on", "auto-rotate off",
            "dark mode on", "dark mode off", "battery saver on", "battery saver off", "airplane mode on", "airplane mode off",
            "turn location on", "turn location off", "turn NFC on", "turn NFC off", "turn mobile data on", "turn mobile data off", "auto-brightness on", "auto-brightness off", "brightness to minimum", "brightness to maximum", "open the notification shade", "close the notification shade", "open quick settings", "take a screenshot", "lock the screen", "recent apps"};

    static String commandKey(int i) { return L.en() && i < EN_LABELS.length ? EN_LABELS[i] : COMMANDS[i].keys[0]; }
    static String commandReply(int i) { return L.t(COMMANDS[i].reply); }

    /** 句子裡除了關鍵字，最多還能有幾個字（「請幫我…」「…好嗎」）才算固定指令；更長的像「關閉定位，然後打開地圖」是多件事，交給 AI。 */
    static final int EXTRA_MAX = 8;

    /** 有這些連接詞就是多件事（比對的是已經去掉空白與標點的句子）。 */
    private static final java.util.regex.Pattern MULTI = java.util.regex.Pattern.compile("然後|然后|接著|接着|之後|之后|並且|并且|同時|同时|andthen|afterthat");

    /** 關鍵字命中固定指令（最長的那個關鍵字優先）回 FIXED；沒命中回 NONE，交給 Haiku 路由判斷。 */
    static Route route(String text) {
        String t = norm(text);
        if (MULTI.matcher(t).find()) return new Route(NONE, null, null, false);   // 多件事：不能只做前半就結束，交給 AI
        Cmd best = null;
        int bestLen = 0;
        for (Cmd c : COMMANDS)
            for (String k : c.keys)
                if (!t.isEmpty() && t.contains(k) && t.length() <= k.length() + EXTRA_MAX && k.length() > bestLen) { best = c; bestLen = k.length(); }
        if (best != null) return new Route(FIXED, best.cmd, L.t(best.reply), best.confirm);
        return new Route(NONE, null, null, false);
    }
}
