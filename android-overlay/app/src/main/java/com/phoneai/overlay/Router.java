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
    static volatile String lastNote = "";   // 這一次路由的結果（顯示在任務過程，看便宜模型到底判成什麼）
    static final String CMD = "cmd", APP = "app", CHAT = "chat", AGENT = "agent";

    static final class Decision {
        final String kind;
        final int cmdIndex;     // kind == cmd
        final String pkg;       // kind == app
        final int steps;        // kind == agent
        String openPkg;         // kind == agent：任務一開始要先開的 App（沒有就是 null）
        String macroTask;       // kind == agent：路由認為「和這個記住的任務是同一件事（說法不同）」，AgentLoop 改用它的巨集重播；沒有就是 null
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

    /** 問一次路由，回最後一個 JSON 物件的文字；連不上或沒有 JSON 回 null。 */
    private static String askRoute(String model, String prompt, String key) throws Exception {
        JSONObject body = new JSONObject().put("model", model).put("max_tokens", 400)
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
        return last;
    }

    private static final Pattern ACTION_HINT = Pattern.compile(
            "開|關|打開|關閉|開啟|音量|大聲|小聲|亮度|截圖|截屏|鎖|返回|上一頁|桌面|點|按|滑|傳訊息|傳給|傳送|發訊息|打電話|進入|前往|切換|設定|飛航|勿擾|藍牙|藍芽|wifi|wi-fi|定位|數據|流量|nfc|深色|省電|旋轉|"
                    + "\\b(open|turn|go to|goto|tap|click|launch|switch|send|call|volume|brightness|screenshot|enable|disable)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SCHED_HINT = Pattern.compile("每天|每隔|每[0-9一二三四五六七八九十兩]+天|每週|每周|定時|排程|(?i:\\b(every day|every \\d+ days|daily|schedule)\\b)");

    /** 這句話看起來可能是要操作手機：先問路由（一次判斷固定指令、開 App、操作畫面）。不像的就直接聊天，聊天助理真的需要操作時會自己呼叫 phone_action 交出去。 */
    static boolean likelyAction(String text) {
        if (text != null && SCHED_HINT.matcher(text).find()) return false;   // 要排定時查詢：交給聊天助理（它有 schedule_web_query）
        return text != null && ACTION_HINT.matcher(text).find();
    }

    private static final Pattern SMALL_TALK = Pattern.compile(
            "^(你好|您好|哈囉|嗨|嘿|hi|hello|hey|早安|早|午安|晚安|謝謝|謝啦|感謝|多謝|掰掰|拜拜|再見|好的|好喔|好|ok|okay|嗯|哈哈|在嗎|在不在|你是誰|你叫什麼|你叫什麼名字|你會什麼|你能做什麼)[!！。.~～？?，, ]*$",
            Pattern.CASE_INSENSITIVE);

    /** 純寒暄（你好、謝謝…）：不用問路由，直接聊天。路由的提示詞很長（App 清單、筆記），一句「你好」也要等好幾秒（使用者回報）。 */
    static boolean isSmallTalk(String text) {
        return text != null && SMALL_TALK.matcher(text.trim()).matches();
    }

    /** 回傳判斷結果；Key 讀不到、網路失敗、回答看不懂回 null（呼叫端改用聊天）。 */
    static Decision decide(Context c, String text) {
        if (isSmallTalk(text)) { lastNote = L.t("路由：寒暄，直接聊天（沒有問路由）"); return new Decision(CHAT, -1, null, 0); }
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
                + "記憶與聊天：使用者要記住東西、問記過的事、改或刪待辦/筆記、問知識、閒聊，都回 chat（聊天助理有記憶工具可以自己處理）。但如果要記的內容得先去某個App或相簿裡看畫面、找圖片或檔案、讀畫面上的字才拿得到，聊天助理看不到畫面也不能點畫面，要回 agent（自動操作看完畫面後可以自己存成筆記）。例子：「去相簿的我的最愛找到課表幫我記起來」「打開蝦皮看我的訂單狀態再告訴我」→ agent；「幫我記住明天要開會」「我剛剛記了什麼」→ chat。要在別的App裡找東西、看東西、點東西的，就算還要記或回報，也一律是 agent。"
                + "導航：使用者要「打開地圖並導航／帶我去／開車去某個地方」，回 chat（聊天助理有導航工具，會用 Google 地圖直接開始導航），不要只回 app。route 是 app 只限於使用者「單純想打開某個 App、沒有別的要求」。鬧鐘：使用者要設鬧鐘、幾點叫我，都回 chat（聊天助理有設鬧鐘的工具）。行事曆：使用者要新增行事曆、提醒某天某時的事、在地圖上找地方、打電話給某個號碼，也都回 chat（聊天助理有行事曆、地圖、撥號的工具）。使用者明確說要用「三星提醒」（Samsung Reminder）App 新增提醒，也回 chat（聊天助理有 samsung_reminder 工具）。"
                + "傳檔案：使用者要在自己的裝置之間傳檔案、找檔案、從另一台手機或電腦拿檔案、傳到另一台裝置，都回 chat（聊天助理有傳檔案工具）。"
                + "使用者的筆記（記憶，只列標題與摘要）：" + Memory.noteIndex(Memory.load(c)) + "\n"
                + "如果使用者問的事，答案很可能已經在上面的筆記裡（例如課表、地址、號碼、他說過要記的事），回 chat：聊天助理看得到筆記的完整內容，不要再去開App或操作畫面查。\n"
                + "如果你不太確定（例如聊天和自動操作都說得通），在JSON裡多加 \"conf\":\"low\"；很確定就不用寫這個欄位。\n"
                + "只輸出一行JSON，不要解釋：\n"
                + "{\"route\":\"cmd\",\"i\":指令編號} 或 {\"route\":\"app\",\"name\":\"App名稱\"} 或 {\"route\":\"chat\"} 或 {\"route\":\"agent\",\"steps\":整數,\"app\":\"要先開的App名稱，沒有就省略\"}\n"
                + "chat = 純聊天或問答：問知識、解釋、翻譯、建議、閒聊、寫文字、算數，答案用你的常識就能直接說，完全不需要操作手機或查即時資料。使用者問他自己的東西或他說過的事（車停哪、東西放哪、之前告訴你的資訊），也回 chat，因為要先查記憶。"
                + "使用者要看、整理通知欄的通知（「我有什麼通知」「整理通知」），回 chat（聊天助理有讀通知的工具）。問這支手機本身的資訊（電量、儲存空間、記憶體、裝了哪些／幾個 App、系統設定的值、版本、型號）也回 chat（聊天助理有 run_shell 工具可以查，不用操作畫面）。使用者明說「幫我查／搜尋一下／上網查」某個知識或即時資訊（今天天氣、新聞、股價、營業時間、某個東西是什麼），而且不是要在手機的某個 App 裡操作，也回 chat（聊天助理有網頁搜尋，只有他這樣明說時才會搜）。要操作手機（開 App、點畫面、在某個 App 裡查）的，回 agent。\n"
                + "不是上述任何一項，或需要看畫面、點擊、輸入文字、搜尋、多步驟操作，一律回 agent。"
                + "回 agent 時 steps 是預估完成需要的畫面操作次數（每次點擊、滑動、輸入算一步；能直接用指令開App或開關Wi-Fi/藍牙的不算步數），再多留2步緩衝，範圍3到15。"
                + "回 agent 時，如果任務一開始要先開某個已安裝的App（任務有指名，或明顯就是那個App，例如「去設定的Wi-Fi頁面」要先開設定），多回 app，名稱要用上面App名稱清單裡的；任務是對目前畫面操作、或沒有指名App，就不要寫 app。";
        // 記住的步驟（巨集）清單：讓便宜模型順便判斷這句話是不是其中一個的另一種說法（語音輸入常常說法不同），不用多呼叫一次
        final List<String> macros = Macros.enabled(c) && !AgentLoop.isQuestion(text) && !AgentLoop.isMessageTask(text) ? Macros.topTasks(c, 30) : new ArrayList<>();
        if (!macros.isEmpty()) {
            StringBuilder ms = new StringBuilder("\n已記住的任務（編號: 任務）：\n");
            for (int i = 0; i < macros.size(); i++) ms.append(i).append(": ").append(macros.get(i)).append('\n');
            ms.append("回 agent 時，如果這句話和上面某一個任務是「同一件事」（目的、對象、App、頁面都一樣，只是說法不同），多回 \"macro\":編號；只要有一點不確定、或對象或內容不一樣（例如不同的人、不同的數字、不同的頁面），就不要回 macro。傳訊息和問問題的不要回 macro。");
            prompt = prompt + ms;
        }
        try {
            long t0 = android.os.SystemClock.uptimeMillis();
            String last = askRoute(MODEL, prompt, key);
            long routeMs = android.os.SystemClock.uptimeMillis() - t0;
            if (last == null) { lastNote = L.t("路由：看不懂便宜模型的回答，當成聊天"); return null; }
            JSONObject d = new JSONObject(last);
            String note = "";
            // 便宜模型自己說沒把握：換較強的模型再判斷一次（只有沒把握才多花一次）。
            // 但它判成「聊天」就不再問：聊天助理什麼都能先試（工具、記憶），真的需要操作畫面時聊天回答「我不能操作」會自動補救改走自動操作（Brain），
            // 多問一次較強的模型要多等好幾秒（使用者回報聊天太慢）
            if ("low".equals(d.optString("conf")) && !CHAT.equals(d.optString("route"))) {
                String l2 = askRoute(AgentLoop.MODEL, prompt, key);
                if (l2 != null) { last = l2; d = new JSONObject(l2); note = L.t("（便宜模型沒把握，改問較強的模型）"); }
            }
            lastNote = L.t("路由：") + last + note + " " + routeMs + " ms";
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
                    int mi = d.optInt("macro", -1);
                    if (mi >= 0 && mi < macros.size()) dec.macroTask = macros.get(mi);
                    return dec;
                }
                default:
                    return null;
            }
        } catch (Exception e) {
            android.util.Log.w("Router", "路由失敗: " + e);
            lastNote = L.t("路由失敗，當成聊天");
            return null;
        }
    }
}
