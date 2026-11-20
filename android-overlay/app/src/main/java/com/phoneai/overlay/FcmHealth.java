package com.phoneai.overlay;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 推播喚醒的健康檢查（純 Java）：把各項狀態整理成「哪裡好、哪裡要處理、怎麼處理」。 */
final class FcmHealth {
    private FcmHealth() { }

    enum Level { OK, WARN, BAD, INFO }

    static final class Item {
        final String label;
        final Level level;
        final String hint;   // 要處理時怎麼做；OK 時可以是空的
        Item(String label, Level level, String hint) { this.label = label; this.level = level; this.hint = hint; }
    }

    static final class Input {
        boolean hasConfig, hasKey, notificationsOn, batteryUnrestricted;
        String initError;        // 初始化 Firebase 的錯誤；成功是 null
        String myToken;          // 沒有是 null
        String lanIp;            // Wi-Fi 位址；沒有是 null
        int paired, pairedWithToken;
        String missingNames = "";   // 還沒互換 token 的裝置名稱（用「、」分開）
        String manufacturer = "";
    }

    static List<Item> evaluate(Input in) {
        List<Item> out = new ArrayList<>();
        if (!in.hasConfig) out.add(new Item("Firebase 設定檔", Level.BAD, "還沒匯入。按「選檔案」匯入 google-services.json，或請另一台手機傳給你。"));
        else if (in.initError != null) out.add(new Item("Firebase 設定檔", Level.BAD, "已匯入但初始化失敗：" + in.initError));
        else out.add(new Item("Firebase 設定檔", Level.OK, ""));
        out.add(in.hasKey ? new Item("發送金鑰", Level.OK, "")
                : new Item("發送金鑰", Level.WARN, "沒有：這支手機不能主動叫醒別人（還是可以被叫醒）。要主動傳檔就匯入服務帳戶金鑰，或請已設好的手機傳給你。"));
        out.add(in.myToken != null ? new Item("這支手機的推播 token", Level.OK, "")
                : new Item("這支手機的推播 token", Level.BAD, "還沒取得：確認網路正常、設定檔已匯入，重開 Vertex 再看。"));
        out.add(in.notificationsOn ? new Item("通知權限", Level.OK, "")
                : new Item("通知權限", Level.BAD, "沒開：被叫醒時看不到通知，系統也可能擋掉。到系統設定開啟 Vertex 的通知。"));
        out.add(in.batteryUnrestricted ? new Item("電池不受限制", Level.OK, "")
                : new Item("電池不受限制", Level.WARN, "沒有設成不受限制：休眠時可能收不到叫醒。到「電池」把 Vertex 設成不受限制。"));
        if (in.paired == 0) out.add(new Item("已配對的裝置", Level.BAD, "還沒有。先到互傳頁面配對另一台。"));
        else if (in.pairedWithToken < in.paired) out.add(new Item("已配對的裝置", Level.WARN,
                (in.paired - in.pairedWithToken) + " 台還沒互換推播 token" + (in.missingNames.isEmpty() ? "" : "（" + in.missingNames + "）")
                        + "：按「互換 token」（兩邊要連得上），或重新配對。電腦版沒有推播，所以電腦一定會在這裡。"));
        else out.add(new Item("已配對的裝置", Level.OK, ""));
        out.add(in.lanIp != null ? new Item("Wi-Fi", Level.OK, "")
                : new Item("Wi-Fi", Level.WARN, "沒有連上 Wi-Fi：叫醒與傳檔只在同一個 Wi-Fi 有效。"));
        out.add(new Item("自啟動", Level.INFO, autostartHint(in.manufacturer)));
        return out;
    }

    /** 自啟動沒辦法由 App 偵測，只能提醒：依廠牌給路徑。 */
    static String autostartHint(String manufacturer) {
        String m = manufacturer == null ? "" : manufacturer.toLowerCase(Locale.ROOT);
        if (m.contains("xiaomi") || m.contains("redmi") || m.contains("poco"))
            return "系統無法偵測，請確認已開：設定 → 應用設定 → 應用管理 → Vertex → 自啟動（開）；省電策略選「無限制」。";
        if (m.contains("samsung"))
            return "系統無法偵測，請確認：設定 → 電池 → 背景使用限制，Vertex 不在「休眠中的 App」，並加入「永不休眠的 App」。";
        if (m.contains("oppo") || m.contains("realme") || m.contains("oneplus"))
            return "系統無法偵測，請確認：設定 → 電池 → 應用耗電管理 → Vertex → 允許自動啟動、允許背景活動。";
        if (m.contains("vivo") || m.contains("iqoo"))
            return "系統無法偵測，請確認：設定 → 電池 → 後台耗電管理 → Vertex 設為「允許後台高耗電」，並在「自啟動」打開。";
        if (m.contains("huawei") || m.contains("honor"))
            return "系統無法偵測，請確認：設定 → 電池 → 應用啟動管理 → Vertex 關掉「自動管理」，手動允許自啟動、關聯啟動、後台活動。";
        return "系統無法偵測。如果休眠後收不到叫醒，到系統設定找「自啟動」或「背景執行」，允許 Vertex。";
    }

    static boolean allGood(List<Item> items) {
        for (Item i : items) if (i.level == Level.BAD) return false;
        return true;
    }
}
