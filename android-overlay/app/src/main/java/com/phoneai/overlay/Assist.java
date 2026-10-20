package com.phoneai.overlay;

/**
 * 助理面板的路由（App 內）：固定指令用 root 直接執行；操作手機的交給原本的 Termux 迴圈（CommandSender）；其餘是聊天（ChatEngine）。
 * 固定指令清單照搬 scripts/assistant.py 的 COMMANDS。
 */
final class Assist {
    static final int FIXED = 1, AGENT = 2, CHAT = 3;

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
            new Cmd(new String[]{"開wifi", "打開wifi", "開啟wifi"}, "svc wifi enable", "已送出開啟 Wi-Fi", false),
            new Cmd(new String[]{"關wifi", "關閉wifi"}, "svc wifi disable", "已送出關閉 Wi-Fi", false),
            new Cmd(new String[]{"開藍牙", "打開藍牙", "開啟藍牙", "開藍芽", "打開藍芽", "開啟藍芽"}, "svc bluetooth enable", "已送出開啟藍牙", false),
            new Cmd(new String[]{"關藍牙", "關閉藍牙", "關藍芽", "關閉藍芽"}, "svc bluetooth disable", "已送出關閉藍牙", false),
            new Cmd(new String[]{"回桌面", "回主畫面"}, "input keyevent 3", "已回到主畫面", false),
            new Cmd(new String[]{"返回", "上一頁"}, "input keyevent 4", "已按返回", false),
            new Cmd(new String[]{"音量大", "大聲"}, "input keyevent 24", "已調大音量", false),
            new Cmd(new String[]{"音量小", "小聲"}, "input keyevent 25", "已調小音量", false),
            new Cmd(new String[]{"開勿擾", "打開勿擾", "開啟勿擾"}, "cmd notification set_dnd priority", "已開啟勿擾模式", false),
            new Cmd(new String[]{"關勿擾", "關閉勿擾"}, "cmd notification set_dnd off", "已關閉勿擾模式", false),
            new Cmd(new String[]{"開自動旋轉", "打開自動旋轉", "開啟自動旋轉"}, "settings put system accelerometer_rotation 1", "已開啟自動旋轉", false),
            new Cmd(new String[]{"關自動旋轉", "關閉自動旋轉", "鎖定直向"}, "settings put system accelerometer_rotation 0", "已關閉自動旋轉", false),
            new Cmd(new String[]{"開深色模式", "打開深色模式", "開啟深色模式"}, "cmd uimode night yes", "已開啟深色模式", false),
            new Cmd(new String[]{"關深色模式", "關閉深色模式", "開淺色模式"}, "cmd uimode night no", "已關閉深色模式", false),
            new Cmd(new String[]{"開省電", "打開省電", "開啟省電"}, "settings put global low_power 1", "已開啟省電模式", false),
            new Cmd(new String[]{"關省電", "關閉省電"}, "settings put global low_power 0", "已關閉省電模式", false),
            // 飛航模式會斷掉網路（連 AI 都連不上），所以先跳確認框
            new Cmd(new String[]{"開飛航", "打開飛航", "開啟飛航"}, "cmd connectivity airplane-mode enable", "已開啟飛航模式", true),
            new Cmd(new String[]{"關飛航", "關閉飛航"}, "cmd connectivity airplane-mode disable", "已關閉飛航模式", true),
    };

    /** 操作手機的動詞：命中就交給 Termux 的迴圈（還沒搬進 App）。 */
    private static final String[] AGENT_WORDS = {"打開", "開啟", "進入", "點", "按一下", "滑", "截圖", "輸入", "切換", "找到", "回到"};

    private Assist() { }

    private static String norm(String t) {
        return (t == null ? "" : t).replaceAll("[\\s\\-_，。,.]", "").toLowerCase(java.util.Locale.ROOT);
    }

    static Route route(String text) {
        String t = norm(text);
        if (t.isEmpty()) return new Route(CHAT, null, null, false);
        Cmd best = null;
        int bestLen = 0;
        for (Cmd c : COMMANDS)
            for (String k : c.keys)
                if (t.contains(k) && k.length() > bestLen) { best = c; bestLen = k.length(); }
        if (best != null) return new Route(FIXED, best.cmd, best.reply, best.confirm);
        for (String w : AGENT_WORDS)
            if (t.contains(w)) return new Route(AGENT, null, null, false);
        return new Route(CHAT, null, null, false);
    }
}
