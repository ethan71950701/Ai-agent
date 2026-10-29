package com.phoneai.overlay;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * 聊天（問問看）：直接呼叫 Claude API（Haiku 5.5），會用記憶工具。
 * 對話紀錄存在 App 自己的資料夾（files/chat_history.json，最多 20 則、24 小時）。
 */
final class ChatEngine {
    static final String MODEL = "claude-haiku-5-5";
    private static final int MAX_MSGS = 20, MAX_TURNS = 6;
    private static final long MAX_AGE = 24L * 3600 * 1000;

    private static final String SYSTEM = "你是使用者手機上的語音助理，像 Gemini 一樣自然地聊天回答。規則：\n"
            + "0. 使用者的話常是語音輸入轉成的文字，可能有同音或近音的錯字（例如「開籃芽」是「開藍牙」、「西元」寫成「西園」）；請依上下文理解他真正的意思，不要照字面回答，也不要糾正他的字。\n"
            + "1. 一律用繁體中文，口語、簡短（一般 1~3 句、100 字內；使用者要求詳細才長），需要整理重點時可以用簡單的 Markdown（粗體、條列），畫面會排版，朗讀時程式會自動略過符號。\n"
            + "2. 你不能操作手機，也沒有網路，不知道今天的新聞、天氣、股價；被問到就老實說不知道，不要編造。\n"
            + "3. 你有記憶工具（待辦 todo 與筆記 note）。使用者要你記住事情、改、刪、標記完成，就呼叫工具去做，做完用一句話說你做了什麼。一定要先呼叫工具、工具回報成功才能說已完成；沒有呼叫工具就不要宣稱已經做了。"
            + "下面列出目前的記憶與編號（#編號）；用編號操作，不要猜。\n"
            + "4. 使用者問到記憶裡的某一點，只回答那一點，不要把整份清單念出來；除非他要整份清單。問他自己的東西或他說過的事（車停哪、東西放哪、家人、密碼本位置…），先查記憶，記憶裡有就直接回答，不要開 App 或去手機上找。\n"
            + "5. 使用者說出關於他自己、值得長期記住的資訊（名字、家人、喜好、習慣、過敏…）時，主動用 memory_add 存成 note，並在回答裡說「已記住…」。"
            + "臨時的事（明天要考試）是 todo 或不用記，不要存成 note。\n"
            + "6. 標了📌的是永久記憶，你不能改也不能刪（工具會拒絕），使用者要刪就告訴他到 App 的「助理的記憶」頁手動刪。\n"
            + "7. 要刪很多項或清空時照樣呼叫工具，確認框由程式負責跳出來；使用者拒絕就照實說沒有刪除。\n"
            + "8. 數學式請用 LaTeX，行內寫在 $...$ 裡（例如 $x^2 + 3 = 7$、$\\sqrt{5}$）；畫面會排成算式，朗讀時程式會念成口語（「x 的二次方 加 3 等於 7」），所以不要自己再把算式唸一遍或另外解釋符號。\n"
            + "9. 裝置之間傳檔案：使用者說「把 X 傳到我的手機／電腦」「從〇〇手機拿 X」「看看〇〇裡有什麼」就用 device_list、file_find、file_get、file_send、file_list。流程：先 file_find 找到檔案（多個就列出最新幾個問使用者要哪個；只有一個或明顯就直接做），再 file_get 或 file_send；做完用一句話說存在哪裡。裝置名稱用使用者說的，不確定就先 device_list。工具回報錯誤就照實告訴使用者原因，不要假裝成功。這些工具要使用者先在「設定 → 傳檔案」開啟並配對。電腦上的檔案：先用 file_find 搜尋（會找整台電腦），找不到就用 file_list 一層一層看（path 留空先看磁碟，路徑用 C:/ 開頭的完整路徑；使用者資料夾是 C:/Users/使用者名稱，不是 user）。使用者要刪檔案時，先用 file_list 或 file_find 確認路徑，再用 file_delete；每次刪除都會跳確認框，你不用另外問，但要照實告訴使用者結果（使用者按否就說沒有刪除）。\n"
            + "10. 使用者要設鬧鐘：說「幾分鐘後／幾小時後」就用 set_alarm 的 minutes_from_now（一小時後是 60）；說幾點就用 hour（24 小時制，下午 3 點是 15）。工具會打開時鐘，你要告訴使用者：時鐘打開了，要在時鐘裡按儲存才會設定。\n"
            + "11. 使用者要新增行事曆／提醒某天某時的事 → add_calendar_event（start 用 yyyy-MM-dd HH:mm，24 小時制，依提示裡的現在時間算「明天、下週三」）；要在地圖上找地方、查路線 → show_on_map；要打電話（使用者給了號碼）→ dial。行事曆有權限時會直接建立（工具回傳說「已新增」就直接告訴他，不用他按儲存）；沒有權限才打開新增畫面，要他按儲存。地圖和撥號只是打開並填好，要自己按撥出。沒有給號碼就問他號碼，不要自己猜。\n";
    private static final String SYSTEM_EN = "You are the voice assistant on the user's phone. Chat naturally, like Gemini. Rules:\n"
            + "0. What the user says is often speech-to-text and may contain similar-sounding mistakes; understand what they really mean from context instead of answering literally.\n"
            + "1. Always reply in English, conversationally and briefly (usually 1-3 sentences, under 100 words; longer only if the user asks). Simple Markdown (bold, bullet lists) is fine when it helps organize points; the screen formats it and speech skips the symbols.\n"
            + "2. You cannot operate the phone and have no internet access; you do not know today's news, weather or prices. If asked, say you do not know; do not make things up.\n"
            + "3. You have memory tools (to-dos todo and notes note). When the user asks you to remember, change, delete or mark something done, call the tool, then say in one sentence what you did. Always call the tool first and only say it is done after the tool reports success; never claim you did something without calling it. The current memory and its numbers (#number) are listed below; act on the numbers, do not guess.\n"
            + "4. If the user asks about one item in memory, answer only about that item; do not read the whole list unless they ask for it. If the user asks about his own things or something he told you (where his car is, where something is kept, family, a password book location…), check memory first; if memory has it, answer directly and do not open an app or search the phone.\n"
            + "5. When the user tells you something about themselves worth keeping long term (name, family, likes, habits, allergies), save it as a note with memory_add and say you have remembered it. Temporary things (an exam tomorrow) are to-dos or not saved at all.\n"
            + "6. Items marked with a pin are permanent: you cannot change or delete them (the tool refuses). If the user wants one removed, tell them to delete it by hand on the assistant memory page of the app.\n"
            + "7. When many items or everything must be deleted, still call the tool; the app shows the confirmation. If the user declines, say that nothing was deleted.\n"
            + "8. Write math in LaTeX, inline inside $...$ (for example $x^2 + 3 = 7$, $\\sqrt{5}$); the screen typesets it and speech reads it aloud naturally, so do not spell the formula out again or explain the symbols.\n"
            + "9. File transfer between devices: when the user says \"send X to my phone/computer\", \"get X from <device>\", or \"what is in <device>\", use device_list, file_find, file_get, file_send, file_list. Flow: file_find first (if several match, list the newest few and ask which; if one or obvious, just proceed), then file_get or file_send; afterwards say in one sentence where it was saved. Use the device name as the user said it; if unsure call device_list first. If a tool reports an error, tell the user the real reason; never pretend it worked. These tools need the user to turn on and pair devices in Settings → File transfer. For files on a computer: use file_find first (it searches the whole computer); if nothing is found, use file_list step by step (empty path lists the drives; use full paths like C:/Users/...). To delete files, first confirm the path with file_list or file_find, then use file_delete; every delete shows a confirmation dialog, so you do not ask separately, but report the result honestly (if the user declines, say nothing was deleted).\n"
            + "10. To set an alarm (wake me at a time): use set_alarm with hour in 24-hour format (3 pm is 15). The tool opens the clock; tell the user the clock is open and they must press save in the clock to set it.\n"
            + "11. For a calendar event or a reminder at a given date and time use add_calendar_event (start as yyyy-MM-dd HH:mm in 24-hour format, work out \"tomorrow\" or \"next Wednesday\" from the current time in the prompt); to find a place or a route on the map use show_on_map; to call a number the user gave use dial. The calendar event is created directly when permission allows (if the tool says it was added, just say so; no saving needed); otherwise it only opens the new-event screen and the user must press save. Map and dial only open the app prefilled; the user must press call. If no number was given, ask for it instead of guessing.\n";
    private static final String TOOLS = "["
            + "{\"name\":\"get_time\",\"description\":\"查手機現在的日期與時間。問幾點、今天幾號、星期幾、或算「幾小時後」之前，先用這個。\","
            + "\"input_schema\":{\"type\":\"object\",\"properties\":{}}},"
            + "{\"name\":\"set_alarm\",\"description\":\"設鬧鐘（打開手機的時鐘，使用者要在時鐘裡按儲存才會設定）。hour 是 0-23 的小時，minute 是分鐘。\","
            + "\"input_schema\":{\"type\":\"object\",\"properties\":{\"minutes_from_now\":{\"type\":\"integer\",\"description\":\"幾分鐘後響（例如一小時後是 60）；說「幾分鐘後／幾小時後」就用這個，不要自己算時間\"},\"hour\":{\"type\":\"integer\",\"description\":\"指定時刻的小時 0-23\"},\"minute\":{\"type\":\"integer\"},\"label\":{\"type\":\"string\",\"description\":\"鬧鐘的名稱，例如 起床\"}}}},"
            + "{\"name\":\"add_calendar_event\",\"description\":\"新增行事曆事件：有權限時直接建立，沒有權限才打開新增畫面（使用者要按儲存）。\","
            + "\"input_schema\":{\"type\":\"object\",\"properties\":{\"title\":{\"type\":\"string\",\"description\":\"事件標題\"},\"start\":{\"type\":\"string\",\"description\":\"開始時間，yyyy-MM-dd HH:mm（24 小時制）\"},\"end\":{\"type\":\"string\",\"description\":\"結束時間，同樣格式；沒說就不要填（預設一小時）\"},\"location\":{\"type\":\"string\"},\"description\":{\"type\":\"string\"}},\"required\":[\"title\",\"start\"]}},"
            + "{\"name\":\"show_on_map\",\"description\":\"在地圖上搜尋或顯示某個地方（不會自動開始導航）。\","
            + "\"input_schema\":{\"type\":\"object\",\"properties\":{\"place\":{\"type\":\"string\",\"description\":\"地點名稱或地址\"}},\"required\":[\"place\"]}},"
            + "{\"name\":\"dial\",\"description\":\"打開撥號畫面並填好電話號碼（使用者要自己按撥出）。\","
            + "\"input_schema\":{\"type\":\"object\",\"properties\":{\"number\":{\"type\":\"string\",\"description\":\"電話號碼\"}},\"required\":[\"number\"]}},"
            + "{\"name\":\"device_list\",\"description\":\"列出已配對、可以傳檔案的其他裝置（手機、電腦），並看哪些現在在線。\","
            + "\"input_schema\":{\"type\":\"object\",\"properties\":{}}},"
            + "{\"name\":\"file_find\",\"description\":\"依檔名關鍵字找檔案。device 留空是找這支手機，填裝置名稱就是找那台裝置（電腦會搜尋整台電腦的所有磁碟）。回傳完整路徑、大小、日期（最新的在前）。\","
            + "\"input_schema\":{\"type\":\"object\",\"properties\":{\"device\":{\"type\":\"string\"},\"name\":{\"type\":\"string\",\"description\":\"檔名裡的關鍵字\"}},\"required\":[\"name\"]}},"
            + "{\"name\":\"file_list\",\"description\":\"列出某個資料夾裡的內容。手機：path 是相對於儲存空間根目錄（例如 Download）。電腦：path 要用完整路徑，例如 C:/Users/某人/Desktop；path 留空就列出電腦的磁碟。\","
            + "\"input_schema\":{\"type\":\"object\",\"properties\":{\"device\":{\"type\":\"string\"},\"path\":{\"type\":\"string\"}}}},"
            + "{\"name\":\"file_get\",\"description\":\"把另一台裝置上的檔案取到這支手機（存在 下載/PhoneAI）。要先用 file_find 找到路徑。\","
            + "\"input_schema\":{\"type\":\"object\",\"properties\":{\"device\":{\"type\":\"string\"},\"path\":{\"type\":\"string\"}},\"required\":[\"device\",\"path\"]}},"
            + "{\"name\":\"file_send\",\"description\":\"把這支手機上的檔案傳到另一台裝置（存在對方的收件匣）。要先用 file_find 找到路徑。\","
            + "\"input_schema\":{\"type\":\"object\",\"properties\":{\"device\":{\"type\":\"string\"},\"path\":{\"type\":\"string\"}},\"required\":[\"device\",\"path\"]}},"
            + "{\"name\":\"file_delete\",\"description\":\"刪除這支手機儲存空間裡的檔案或資料夾（例如 Download/PhoneAI/a.jpg），不能刪 Android/。每次都會跳確認框，使用者按否就不刪。path 是相對於儲存空間根目錄的路徑。\","
            + "\"input_schema\":{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}},\"required\":[\"path\"]}},"
            + "{\"name\":\"memory_add\",\"description\":\"新增一則待辦（要做的事）或筆記（資訊、事實）。\","
            + "\"input_schema\":{\"type\":\"object\",\"properties\":{\"kind\":{\"type\":\"string\",\"enum\":[\"todo\",\"note\"]},"
            + "\"text\":{\"type\":\"string\",\"description\":\"內容，用使用者的原話或簡短整理\"},"
            + "\"permanent\":{\"type\":\"boolean\",\"description\":\"永久記憶：使用者說「永遠記住／一直記著／不要忘」才設 true；永久記憶你之後不能改或刪，只有使用者在 App 裡手動刪\"}},"
            + "\"required\":[\"kind\",\"text\"]}},"
            + "{\"name\":\"memory_update\",\"description\":\"修改某一項的文字，或把待辦標成完成／改回未完成。\","
            + "\"input_schema\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"},\"text\":{\"type\":\"string\"},\"done\":{\"type\":\"boolean\"}},\"required\":[\"id\"]}},"
            + "{\"name\":\"memory_delete\",\"description\":\"刪除指定編號的項目（一次超過 3 項會跳確認框）。\","
            + "\"input_schema\":{\"type\":\"object\",\"properties\":{\"ids\":{\"type\":\"array\",\"items\":{\"type\":\"integer\"}}},\"required\":[\"ids\"]}},"
            + "{\"name\":\"memory_clear\",\"description\":\"刪除全部待辦／全部筆記／全部（一定會跳確認框，使用者按否就不刪）。\","
            + "\"input_schema\":{\"type\":\"object\",\"properties\":{\"scope\":{\"type\":\"string\",\"enum\":[\"todo\",\"note\",\"all\"]}},\"required\":[\"scope\"]}},"
            + "{\"name\":\"memory_list\",\"description\":\"列出記憶（含編號）。提示裡已有未完成的項目；要看已完成的才需要呼叫。\","
            + "\"input_schema\":{\"type\":\"object\",\"properties\":{\"include_done\":{\"type\":\"boolean\"}}}}"
            + "]";

    private ChatEngine() { }

    private static File history(Context c) { return new File(c.getFilesDir(), "chat_history.json"); }

    private static JSONArray loadHistory(Context c) {
        JSONArray out = new JSONArray();
        try {
            JSONArray h = new JSONArray(new String(Files.readAllBytes(history(c).toPath()), StandardCharsets.UTF_8));
            long now = System.currentTimeMillis();
            for (int i = 0; i < h.length(); i++) {
                JSONObject m = h.optJSONObject(i);
                if (m == null) continue;
                String role = m.optString("role");
                if ((role.equals("user") || role.equals("assistant")) && m.optString("content").length() > 0
                        && now - m.optLong("t") < MAX_AGE) out.put(new JSONObject().put("role", role).put("content", m.optString("content")));
            }
        } catch (Exception ignored) { }
        // 只留最後 MAX_MSGS 則
        JSONArray tail = new JSONArray();
        for (int i = Math.max(0, out.length() - MAX_MSGS); i < out.length(); i++) tail.put(out.opt(i));
        return tail;
    }

    private static void saveHistory(Context c, JSONArray msgs, long t) throws Exception {
        JSONArray h = new JSONArray();
        for (int i = 0; i < msgs.length(); i++) {
            JSONObject m = msgs.getJSONObject(i);
            h.put(new JSONObject().put("role", m.getString("role")).put("content", m.getString("content")).put("t", t));
        }
        File f = history(c), tmp = new File(f.getPath() + ".tmp");
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(tmp)) { out.write(h.toString().getBytes(StandardCharsets.UTF_8)); }
        if (!tmp.renameTo(f)) throw new Exception("寫入對話紀錄失敗");
    }

    /** API 金鑰：只讀 App 自己的檔案（設定時用「功能 → 維護 → Claude API Key」存進來）。 */
    static String key(Context c) {
        File k = new File(c.getFilesDir(), "claude_key");
        try {
            return new String(Files.readAllBytes(k.toPath()), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return null;
        }
    }

    private static JSONObject call(JSONArray messages, String system, String key) throws Exception {
        JSONObject body = new JSONObject().put("model", MODEL).put("max_tokens", 1000).put("system", system)
                .put("messages", messages).put("tools", new JSONArray(TOOLS));
        return post(body, key);
    }

    /** 送出一則 Messages API 的請求（聊天與自動操作共用）。 */
    private static volatile HttpURLConnection inflight;

    /** 立刻中斷正在等的請求（使用者按了停止）。要在別的執行緒做，主執行緒不能碰網路。 */
    static void abortInflight() {
        final HttpURLConnection con = inflight;
        if (con == null) return;
        new Thread(() -> {
            try { con.disconnect(); } catch (Exception ignored) { }
        }, "abort-request").start();
    }

    static JSONObject post(JSONObject body, String key) throws Exception {
        Context app = App.ctx;
        if (app != null) {
            String over = Usage.overLimit(app);
            if (over != null) throw new Exception(over);
        }
        HttpURLConnection con = (HttpURLConnection) new URL("https://api.anthropic.com/v1/messages").openConnection();
        inflight = con;
        con.setRequestMethod("POST");
        con.setConnectTimeout(20000);
        con.setReadTimeout(40000);
        con.setDoOutput(true);
        con.setRequestProperty("x-api-key", key);
        con.setRequestProperty("anthropic-version", "2023-06-01");
        con.setRequestProperty("content-type", "application/json");
        try (OutputStream out = con.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
        int code = con.getResponseCode();
        InputStream in = code < 400 ? con.getInputStream() : con.getErrorStream();
        String text = in == null ? "" : new String(readAll(in), StandardCharsets.UTF_8);
        inflight = null;
        if (code >= 400) throw new Exception("HTTP " + code + " " + text);
        JSONObject r = new JSONObject(text);
        JSONObject u = r.optJSONObject("usage");
        if (app != null && u != null) {
            long cw = u.optLong("cache_creation_input_tokens"), cr = u.optLong("cache_read_input_tokens");
            Usage.add(app, u.optLong("input_tokens") + cw + cr, u.optLong("output_tokens"), cw, cr);
            Usage.tallyAdd(body.optString("model"), u.optLong("input_tokens") + cw + cr, u.optLong("output_tokens"), cw, cr);
        }
        return r;
    }

    private static byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        return bo.toByteArray();
    }

    /** 回傳回答文字；連不上或沒有 Key 回 null。在背景執行緒呼叫。 */
    /** 最近一次聊天失敗的原因（給畫面顯示，讓使用者知道是金鑰、用量還是網路）。 */
    static volatile String lastError = "";

    static String ask(Context c, String text, Memory.Confirm confirm) {
        lastError = "";
        String key = key(c);
        if (key == null || key.isEmpty()) { lastError = L.t("還沒有 Claude API Key（功能 → 維護 → Claude API Key）"); return null; }
        try {
            JSONObject mem = Memory.load(c);
            String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
            String nowTime = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(new java.util.Date());
            String system = (L.en() ? SYSTEM_EN : SYSTEM) + (L.en() ? "\nToday is " : "\n今天是 ") + today
                    + (L.en() ? ", the current time is " : "，現在時間 ") + nowTime + (L.en() ? ".\nTo-dos (not done): " : "。\n待辦（未完成）：")
                    + Memory.promptRows(mem, "todo") + (L.en() ? "\nNotes: " : "\n筆記：") + Memory.promptRows(mem, "note");
            JSONArray msgs = loadHistory(c);
            msgs.put(new JSONObject().put("role", "user").put("content", text));
            String reply = null;
            for (int turn = 0; turn < MAX_TURNS; turn++) {
                JSONObject r = call(msgs, system, key);
                JSONArray content = r.optJSONArray("content");
                if (content == null) return null;
                StringBuilder texts = new StringBuilder();
                JSONArray uses = new JSONArray();
                for (int i = 0; i < content.length(); i++) {
                    JSONObject b = content.getJSONObject(i);
                    if ("text".equals(b.optString("type"))) texts.append(b.optString("text"));
                    if ("tool_use".equals(b.optString("type"))) uses.put(b);
                }
                if (uses.length() == 0) { reply = texts.toString().trim(); break; }
                msgs.put(new JSONObject().put("role", "assistant").put("content", content));
                JSONArray results = new JSONArray();
                for (int i = 0; i < uses.length(); i++) {
                    JSONObject u = uses.getJSONObject(i);
                    String tn = u.optString("name");
                    JSONObject ti = u.optJSONObject("input") == null ? new JSONObject() : u.optJSONObject("input");
                    String out = tn.equals("get_time") ? new java.text.SimpleDateFormat("yyyy-MM-dd (EEEE) HH:mm:ss", java.util.Locale.TAIWAN).format(new java.util.Date())
                            : tn.equals("set_alarm") ? AlarmTool.set(c, ti.optInt("hour", -1), ti.optInt("minute", -1), ti.optInt("minutes_from_now", -1), ti.optString("label"))
                            : tn.equals("add_calendar_event") ? SystemIntents.addEvent(c, ti.optString("title"), ti.optString("start"), ti.optString("end"), ti.optString("location"), ti.optString("description"))
                            : tn.equals("show_on_map") ? SystemIntents.showOnMap(c, ti.optString("place"))
                            : tn.equals("dial") ? SystemIntents.dial(c, ti.optString("number"))
                            : FileTools.handles(tn) ? FileTools.run(c, tn, ti, confirm) : Memory.runTool(c, tn, ti, confirm);
                    results.put(new JSONObject().put("type", "tool_result").put("tool_use_id", u.optString("id")).put("content", out));
                }
                msgs.put(new JSONObject().put("role", "user").put("content", results));
                reply = texts.length() > 0 ? texts.toString().trim() : null;
            }
            if (reply == null || reply.isEmpty()) return null;
            // 只把「使用者問的 + 回答」存進對話紀錄（工具過程不存，跟 chat.py 一樣）
            JSONArray keep = loadHistory(c);
            keep.put(new JSONObject().put("role", "user").put("content", text));
            keep.put(new JSONObject().put("role", "assistant").put("content", reply));
            saveHistory(c, keep, System.currentTimeMillis());
            return reply;
        } catch (Exception e) {
            android.util.Log.w("ChatEngine", L.t("聊天失敗: ") + e);
            lastError = String.valueOf(e.getMessage() == null ? e : e.getMessage());
            return null;
        }
    }
}
