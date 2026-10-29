package com.phoneai.overlay;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Haiku 5.5 路由（照搬 scripts/assistant.py 的 route）：關鍵字沒命中時，判斷這句話是
 * 固定指令、開 App、純聊天，還是要看畫面操作手機。只能「選」既有的項目，不能自己創造指令。
 */
final class Router {
    static final String MODEL = "claude-haiku-5-5";
    static final String CMD = "cmd", APP = "app", CHAT = "chat", AGENT = "agent";

    static final class Decision {
        final String kind;
        final int cmdIndex;     // kind == cmd
        final String pkg;       // kind == app
        final int steps;        // kind == agent
        String openPkg;         // kind == agent：任務一開始要先開的 App（沒有就是 null）
        Decision(String kind, int cmdIndex, String pkg, int steps) { this.kind = kind; this.cmdIndex = cmdIndex; this.pkg = pkg; this.steps = steps; }
    }

    private Router() { }

    private static final java.util.regex.Pattern SYSTEM_INFO = java.util.regex.Pattern.compile(
            "版本|型號|型号|電量|电量|電池|电池|容量|儲存空間|储存空间|存儲|存储|記憶體|内存|內存|現在幾點|现在几点|現在時間|现在时间|幾點|几点|"
                    + "\\b(android version|battery|storage|model|what time)\\b", java.util.regex.Pattern.CASE_INSENSITIVE);

    /** 查手機本身的資訊（版本、電量、容量、時間…）：用指令就查得到，不必先打開任何 App。 */
    static boolean isSystemInfoQuestion(String task) {
        return AgentLoop.isQuestion(task) && task != null && SYSTEM_INFO.matcher(task).find();
    }

    static String norm(String t) {
        return (t == null ? "" : t).replaceAll("[\\s\\-_，。,.]", "").toLowerCase(java.util.Locale.ROOT);
    }

    /** App 名稱 → 套件名稱（已安裝、有桌面圖示的）。 */
    static Map<String, String> apps(Context c) {
        Map<String, String> out = new TreeMap<>();
        PackageManager pm = c.getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(launcher, 0)) {
            String label = String.valueOf(ri.loadLabel(pm));
            String pkg = ri.activityInfo.packageName;
            out.putIfAbsent(norm(label), pkg);
        }
        return out;
    }

    /** 常見 App 的同義說法：三星的相簿叫「圖庫」，使用者說「相簿」時對不上。 */
    private static final String[][] ALIASES = {
            {"相簿", "相册", "相冊", "圖庫", "图库", "照片", "gallery", "photos"},
            {"設定", "设置", "設置", "settings"},
            {"相機", "相机", "camera"},
            {"時鐘", "时钟", "鬧鐘", "闹钟", "clock"},
            {"計算機", "计算机", "計算器", "计算器", "calculator"},
            {"日曆", "日历", "行事曆", "行事历", "calendar"},
            {"聯絡人", "联系人", "通訊錄", "通讯录", "contacts"},
            {"電話", "电话", "撥號", "拨号", "phone"},
            {"訊息", "信息", "簡訊", "短信", "messages"},
            {"檔案", "我的檔案", "文件管理", "myfiles", "files"},
            {"瀏覽器", "浏览器", "browser", "internet", "chrome"},
            {"商店", "應用商店", "应用商店", "googleplay", "playstore", "play商店"},
            {"地圖", "地图", "maps"},
    };

    /** 依顯示名稱找套件：先完全相符，再找同義的說法（相簿＝圖庫）；找不到回 null。 */
    static String findApp(Map<String, String> apps, String name) {
        String n = norm(name);
        if (n.isEmpty()) return null;
        String hit = apps.get(n);
        if (hit != null) return hit;
        for (String[] g : ALIASES) {
            boolean member = false;
            for (String a : g) if (norm(a).equals(n)) member = true;
            if (!member) continue;
            for (String a : g) {
                String p = apps.get(norm(a));
                if (p != null) return p;
            }
            for (String a : g) {   // 顯示名稱包含這個說法（例如「Google 地圖」）
                String na = norm(a);
                if (na.length() < 2) continue;
                for (Map.Entry<String, String> e : apps.entrySet()) if (e.getKey().contains(na)) return e.getValue();
            }
        }
        return null;
    }

    /** 回傳判斷結果；Key 讀不到、網路失敗、回答看不懂回 null（呼叫端改用聊天）。 */
    static Decision decide(Context c, String text) {
        String key = ChatEngine.key(c);
        if (key == null || key.isEmpty()) return null;
        Map<String, String> apps = apps(c);
        StringBuilder cmds = new StringBuilder();
        for (int i = 0; i < Assist.commandCount(); i++)
            cmds.append(i).append(": ").append(Assist.commandKey(i)).append("（").append(Assist.commandReply(i)).append("）\n");
        List<String> names = new ArrayList<>(apps.keySet());
        Collections.sort(names);
        StringBuilder appNames = new StringBuilder();
        for (int i = 0; i < names.size() && i < 400; i++) appNames.append(i > 0 ? "、" : "").append(names.get(i));
        String prompt = "使用者對手機助手說：「" + text + "」\n"
                + "請判斷這句話是不是下列某個固定指令或App的另一種說法（錯字、同音字、口語都算，例如「藍芽」=藍牙）。\n"
                + "固定指令:\n" + cmds + "\nApp名稱: " + appNames + "\n"
                + "規則：開和關、大聲和小聲是相反的動作，絕對不能搞混；意思不完全一樣就不要硬選。"
                + "記憶與聊天：使用者要記住東西、問記過的事、改或刪待辦/筆記、問知識、閒聊，都回 chat（聊天助理有記憶工具可以自己處理）。"
                + "鬧鐘：使用者要設鬧鐘、幾點叫我，都回 chat（聊天助理有設鬧鐘的工具）。行事曆：使用者要新增行事曆、提醒某天某時的事、在地圖上找地方、打電話給某個號碼，也都回 chat（聊天助理有行事曆、地圖、撥號的工具）。"
                + "傳檔案：使用者要在自己的裝置之間傳檔案、找檔案、從另一台手機或電腦拿檔案、傳到另一台裝置，都回 chat（聊天助理有傳檔案工具）。"
                + "只輸出一行JSON，不要解釋：\n"
                + "{\"route\":\"cmd\",\"i\":指令編號} 或 {\"route\":\"app\",\"name\":\"App名稱\"} 或 {\"route\":\"chat\"} 或 {\"route\":\"agent\",\"steps\":整數,\"app\":\"要先開的App名稱，沒有就省略\"}\n"
                + "chat = 純聊天或問答：問知識、解釋、翻譯、建議、閒聊、寫文字、算數，答案用你的常識就能直接說，完全不需要操作手機或查即時資料。使用者問他自己的東西或他說過的事（車停哪、東西放哪、之前告訴你的資訊），也回 chat，因為要先查記憶。"
                + "需要即時資訊（今天天氣、新聞、股價、營業時間）或要操作手機的，回 agent。\n"
                + "不是上述任何一項，或需要看畫面、點擊、輸入文字、搜尋、多步驟操作，一律回 agent。"
                + "回 agent 時 steps 是預估完成需要的畫面操作次數（每次點擊、滑動、輸入算一步；能直接用指令開App或開關Wi-Fi/藍牙的不算步數），再多留2步緩衝，範圍3到15。"
                + "回 agent 時，如果任務一開始要先開某個已安裝的App（任務有指名，或明顯就是那個App，例如「去設定的Wi-Fi頁面」要先開設定），多回 app，名稱要用上面App名稱清單裡的；任務是對目前畫面操作、或沒有指名App，就不要寫 app。";
        try {
            JSONObject body = new JSONObject().put("model", MODEL).put("max_tokens", 400)
                    .put("messages", new JSONArray().put(new JSONObject().put("role", "user").put("content", prompt)));
            JSONObject r = ChatEngine.post(body, key);
            StringBuilder out = new StringBuilder();
            JSONArray content = r.optJSONArray("content");
            for (int i = 0; content != null && i < content.length(); i++) {
                JSONObject b = content.getJSONObject(i);
                if ("text".equals(b.optString("type"))) out.append(b.optString("text"));
            }
            Matcher m = Pattern.compile("\\{[^{}]*\\}").matcher(out);
            String last = null;
            while (m.find()) last = m.group();
            if (last == null) return null;
            JSONObject d = new JSONObject(last);
            switch (d.optString("route")) {
                case CMD: {
                    int i = d.optInt("i", -1);
                    return i >= 0 && i < Assist.commandCount() ? new Decision(CMD, i, null, 0) : null;
                }
                case APP: {
                    String pkg = findApp(apps, d.optString("name"));
                    return pkg != null ? new Decision(APP, -1, pkg, 0) : null;
                }
                case CHAT:
                    return new Decision(CHAT, -1, null, 0);
                case AGENT: {
                    Decision dec = new Decision(AGENT, -1, null, Math.min(Math.max(d.optInt("steps", 8), 3), 15));
                    String name = d.optString("app", "");
                    if (!name.isEmpty()) dec.openPkg = findApp(apps, name);   // 只認已安裝的 App；對不上就不先開
                    return dec;
                }
                default:
                    return null;
            }
        } catch (Exception e) {
            android.util.Log.w("Router", "路由失敗: " + e);
            return null;
        }
    }
}
