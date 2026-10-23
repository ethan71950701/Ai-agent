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
            + "1. 一律用繁體中文，口語、簡短（一般 1~3 句、100 字內；使用者要求詳細才長），不要用 Markdown 符號、不要條列符號，因為回答會被唸出來。\n"
            + "2. 你不能操作手機，也沒有網路，不知道今天的新聞、天氣、股價；被問到就老實說不知道，不要編造。\n"
            + "3. 你有記憶工具（待辦 todo 與筆記 note）。使用者要你記住事情、改、刪、標記完成，就呼叫工具去做，做完用一句話說你做了什麼。"
            + "下面列出目前的記憶與編號（#編號）；用編號操作，不要猜。\n"
            + "4. 使用者問到記憶裡的某一點，只回答那一點，不要把整份清單念出來；除非他要整份清單。\n"
            + "5. 使用者說出關於他自己、值得長期記住的資訊（名字、家人、喜好、習慣、過敏…）時，主動用 memory_add 存成 note，並在回答裡說「已記住…」。"
            + "臨時的事（明天要考試）是 todo 或不用記，不要存成 note。\n"
            + "6. 標了📌的是永久記憶，你不能改也不能刪（工具會拒絕），使用者要刪就告訴他到 App 的「助理的記憶」頁手動刪。\n"
            + "7. 要刪很多項或清空時照樣呼叫工具，確認框由程式負責跳出來；使用者拒絕就照實說沒有刪除。";

    private static final String SYSTEM_EN = "You are the voice assistant on the user's phone. Chat naturally, like Gemini. Rules:\n"
            + "0. What the user says is often speech-to-text and may contain similar-sounding mistakes; understand what they really mean from context instead of answering literally.\n"
            + "1. Always reply in English, conversationally and briefly (usually 1-3 sentences, under 100 words; longer only if the user asks). No Markdown or bullet symbols, because replies are read aloud.\n"
            + "2. You cannot operate the phone and have no internet access; you do not know today's news, weather or prices. If asked, say you do not know; do not make things up.\n"
            + "3. You have memory tools (to-dos todo and notes note). When the user asks you to remember, change, delete or mark something done, call the tool, then say in one sentence what you did. The current memory and its numbers (#number) are listed below; act on the numbers, do not guess.\n"
            + "4. If the user asks about one item in memory, answer only about that item; do not read the whole list unless they ask for it.\n"
            + "5. When the user tells you something about themselves worth keeping long term (name, family, likes, habits, allergies), save it as a note with memory_add and say you have remembered it. Temporary things (an exam tomorrow) are to-dos or not saved at all.\n"
            + "6. Items marked with a pin are permanent: you cannot change or delete them (the tool refuses). If the user wants one removed, tell them to delete it by hand on the assistant memory page of the app.\n"
            + "7. When many items or everything must be deleted, still call the tool; the app shows the confirmation. If the user declines, say that nothing was deleted.";

    private static final String TOOLS = "["
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
    static JSONObject post(JSONObject body, String key) throws Exception {
        Context app = App.ctx;
        if (app != null) {
            String over = Usage.overLimit(app);
            if (over != null) throw new Exception(over);
        }
        HttpURLConnection con = (HttpURLConnection) new URL("https://api.anthropic.com/v1/messages").openConnection();
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
        if (code >= 400) throw new Exception("HTTP " + code + " " + text);
        JSONObject r = new JSONObject(text);
        JSONObject u = r.optJSONObject("usage");
        if (app != null && u != null) {
            long cw = u.optLong("cache_creation_input_tokens"), cr = u.optLong("cache_read_input_tokens");
            Usage.add(app, u.optLong("input_tokens") + cw + cr, u.optLong("output_tokens"), cw, cr);
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
    static String ask(Context c, String text, Memory.Confirm confirm) {
        String key = key(c);
        if (key == null || key.isEmpty()) return null;
        try {
            JSONObject mem = Memory.load(c);
            String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
            String system = (L.en() ? SYSTEM_EN : SYSTEM) + (L.en() ? "\nToday is " : "\n今天是 ") + today + (L.en() ? ".\nTo-dos (not done): " : "。\n待辦（未完成）：")
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
                    String out = Memory.runTool(c, u.optString("name"), u.optJSONObject("input"), confirm);
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
            return null;
        }
    }
}
