package com.phoneai.overlay;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自動操作迴圈：截圖 → Claude 看圖決定下一步 → 用 root 執行 → 重複。照搬 scripts/agent2.py 的核心與第二段功能：
 * 分段拖曳（drag_start／move／end／cancel，手指一直按著）、拖曳（drag）、批次動作（batch，中間不截圖）、
 * 經驗記錄（remember，存進記憶）、網路搜尋（search，Claude 的網頁搜尋，每個任務最多 2 次）、等待、唯讀 shell。
 * 還沒搬的：多指觸控（multitouch）、背景虛擬螢幕、模型自動升級、重複動作偵測。
 */
final class AgentLoop {
    static final String MODEL = "claude-sonnet-5-5";
    static final String SEARCH_MODEL = "claude-sonnet-5-5";
    static volatile String lastRaw = "";   // 最近一次模型回覆的開頭，解析不出動作時放進錯誤訊息
    static final String TREE_MODEL = "claude-haiku-5-5";   // 元件清單模式（省錢實驗）用：只看編號清單，不看截圖
    static final int WIDTH = 432;                 // 截圖寬度（省 token，跟 agent2.py 預設一樣）
    private static final int MAX_STEPS = 8;
    private static final int MAX_MEASURES = 4, MAX_REMEMBER = 3, SEARCH_MAX = 2, BATCH_LIMIT = 6;
    private static final String[] TASK_BLOCK = {"付款", "轉帳", "匯款", "購買", "支付", "刪除", "密碼", "下單", "安裝", "訂閱",
            "pay", "payment", "transfer", "purchase", "buy", "delete", "password", "order", "checkout", "install", "subscribe"};   // 中文與英文的敏感詞
    private static final String[] READ_BINS = {"dumpsys", "getprop", "date", "settings", "cmd", "pm", "am", "ls", "cat", "df", "uptime", "free", "ps", "top", "wm", "uname"};
    private static final String[] PIPE_OK = {"grep", "head", "tail", "sort", "cut", "wc", "uniq", "tr"};
    private static final String[] BATCH_OK = {"tap", "swipe", "longpress", "drag", "multitouch", "scroll", "wait", "back", "home", "enter", "type"};
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    interface Ui {
        void hide();                    // 收起助理面板，讓畫面可以操作
        void status(String text);       // 通知列上的進度文字
        boolean confirm(String msg);    // 敏感操作的確認框（在背景執行緒呼叫，會等使用者按）
        /** 問使用者一個問題，回傳他的回答；null = 這個介面沒辦法問；空字串 = 使用者取消。在背景執行緒呼叫。 */
        default String ask(String question) { return null; }
    }

    private static volatile boolean cancelled;
    /** 這一輪自動操作的結果是不是要用回答卡片顯示（搜尋結果或較長的回答）；「完成了」這類短的只通知。 */
    static volatile boolean showCard;

    private static volatile List<String> lastSteps;
    private static volatile List<JSONObject> lastDetails;

    /** 這一輪每一步的細節（思考、動作、結果、用了多少 token）；沒跑過自動操作回 null。取一次就清掉。 */
    static JSONArray takeDetails() {
        List<JSONObject> d = lastDetails;
        lastDetails = null;
        if (d == null || d.isEmpty()) return null;
        JSONArray out = new JSONArray();
        for (JSONObject o : d) out.put(o);
        return out;
    }

    private static JSONObject detail(int step, JSONObject action, long tokens) {
        JSONObject o = new JSONObject();
        try {
            o.put("step", step).put("think", action.optString("think")).put("action", action.optString("action"))
                    .put("result", "").put("tokens", tokens);
        } catch (Exception ignored) { }
        return o;
    }

    /** 這一步的模型、輸入／輸出 token 與估價，在這一步的 token 都算完之後呼叫。 */
    private static void usage(JSONObject det, String model, long[] before, double costBefore) {
        long[] now = Usage.tallyCounts();
        try {
            det.put("model", Usage.modelName(model)).put("tokens", now[0] - before[0])
                    .put("in", now[1] - before[1]).put("out", now[2] - before[2])
                    .put("cw", now[3] - before[3]).put("cr", now[4] - before[4])
                    .put("cost", Usage.tallyCost() - costBefore);
        } catch (Exception ignored) { }
    }

    /** 這一輪自動操作每一步做了什麼（給任務歷史用）；沒跑過自動操作回 null。取一次就清掉。 */
    static List<String> takeSteps() {
        List<String> s = lastSteps;
        lastSteps = null;
        return s == null || s.isEmpty() ? null : new ArrayList<>(s);
    }

    /** 使用者按了通知上的「停止」：立刻中斷正在等的網路請求，下一個檢查點就結束這一輪。 */
    static void cancel() {
        cancelled = true;
        ChatEngine.abortInflight();
    }

    /** 這個動作會不會實際操作手機（點、滑、輸入、開 App…）。思考、提問、搜尋、記憶、確認、等待不算。 */
    private static boolean operatesPhone(String name) {
        switch (name) {
            case "done": case "fail": case "ask_user": case "ask": case "search": case "remember": case "wait": case "": return false;
            default: return true;
        }
    }

    /** 第一次要真的操作手機時才收起面板（之後不再收）；在這之前光球一直留著。 */
    private static void hideBeforeOperate(Ui ui, boolean[] hidden, String name) {
        if (hidden[0] || !operatesPhone(name)) return;
        hidden[0] = true;
        ui.hide();
        sleep(600);
    }

    /** 這一次任務的狀態：開過哪些 App、量測次數、記了幾則經驗、搜尋次數、拖曳手指是否還按著。 */
    private static final class State {
        final List<String> opened = new ArrayList<>();
        final List<String> remembered = new ArrayList<>();
        int measures, remembers, searches;
        boolean dragDown;
        int dx, dy;
    }

    private AgentLoop() { }

    /** 任務文字是否含敏感詞：中文直接比對；英文要整個單字（避免 display 被當成 pay）。 */
    private static boolean hit(String task, String w) {
        if (!w.matches("[a-z]+")) return task.contains(w);
        return java.util.regex.Pattern.compile("\\b" + w + "\\b", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(task).find();
    }

    /** 執行任務；回傳要告訴使用者的文字。結束時收掉 Now Bar。 */
    static String run(Context c, String task, Ui ui) { return run(c, task, ui, null); }

    /** preOpen：任務一開始由程式先開啟的 App 套件名稱（路由判斷出來的）；沒有就是 null。 */
    static String run(Context c, String task, Ui ui, String preOpen) {
        cancelled = false;
        showCard = false;
        lastSteps = null;
        try {
            return runInner(c, task, ui, preOpen);
        } finally {
            OverlayService.endSteps(c);
        }
    }

    private static String runInner(Context c, String task, Ui ui, String preOpen) {
        for (String w : TASK_BLOCK)
            if (hit(task, w) && !ui.confirm(L.t("任務含「") + L.t(w) + L.t("」這類敏感操作，要繼續嗎？"))) return L.t("已取消，沒有執行。");
        String key = ChatEngine.key(c);
        if (key == null || key.isEmpty()) return L.t("還沒有 Claude API Key：請到「功能 → 維護 → Claude API Key」設定。");
        Memory.seedLessons(c);
        int[] wh = screenSize();
        State st = new State();
        boolean[] panelHidden = {false};   // 面板只在真的要操作手機時才收起（只收一次）；思考、提問、確認時光球留著
        List<String> history = new ArrayList<>();
        lastSteps = history;
        List<JSONObject> details = new ArrayList<>();
        lastDetails = details;
        if (preOpen != null && preOpen.matches("[A-Za-z0-9._]+")) {   // 路由已判斷第一步是開 App：不用再花一次模型呼叫
            sh("monkey -p " + preOpen + " -c android.intent.category.LAUNCHER 1");
            st.opened.add(preOpen);
            history.add("0. 任務開始前，程式已先開啟 " + preOpen + "，不用再 open");
            sleep(2500);
        }
        String finalSay = null;
        boolean treeOn = c.getSharedPreferences("cfg", Context.MODE_PRIVATE).getBoolean("tree_on", false);
        boolean forceShot = false;   // 上一步點不到編號：這一步改回截圖＋Sonnet（升級）
        String lastTreeText = null;  // 上一步的元件清單；連續三次一樣＝畫面沒在動，可能卡住
        int sameTree = 0;
        int waits = 0;               // 連續 wait 的次數（相機取景這種本來就不會變的畫面，不能只看清單有沒有變）
        try {
            for (int step = 1; step <= MAX_STEPS && finalSay == null; step++) {
                if (cancelled) return L.t("已停止。");
                ui.status(L.t("第 ") + step + L.t("／") + MAX_STEPS + L.t(" 步"));
                OverlayService.showStep(c, L.t("第 ") + step + L.t("／") + MAX_STEPS + L.t(" 步"), step, MAX_STEPS);
                // 元件清單模式：不截圖，改給 Haiku 看元件編號清單；編號點不到時這一步改回截圖＋Sonnet。
                boolean wasForced = forceShot;
                List<JSONObject> tree = treeOn && !forceShot ? ElementList.collect(wh[0], wh[1]) : null;
                forceShot = false;
                if (treeOn && !wasForced && (tree == null || tree.isEmpty()) && !panelHidden[0]) {   // 助理面板蓋住下面的畫面時讀不到元件：先收起面板再讀
                    hideBeforeOperate(ui, panelHidden, "tap");
                    tree = ElementList.collect(wh[0], wh[1]);
                }
                // App 剛打開或剛換頁、還在載入時讀不到元件：等一下再讀，不要馬上改用 Sonnet＋截圖（桌面第一步沒有預先開 App，不等）
                for (int tries = 0; treeOn && !wasForced && (step > 1 || preOpen != null) && (tree == null || tree.isEmpty()) && tries < 2; tries++) {
                    sleep(1200);
                    tree = ElementList.collect(wh[0], wh[1]);
                }
                boolean useTree = tree != null && !tree.isEmpty();
                int treeNote = !treeOn ? -1 : useTree ? tree.size() : wasForced ? -2 : 0;   // 診斷用，顯示在任務過程
                String treeText = useTree ? ElementList.describe(tree) : null;
                sameTree = treeText != null && treeText.equals(lastTreeText) ? sameTree + 1 : 0;
                lastTreeText = treeText;
                if (useTree && sameTree >= 1 && waits >= 2) { useTree = false; treeNote = -3; sameTree = 0; }   // 連續等了兩次、清單還是沒變：可能卡住，這一步改用截圖＋Sonnet 看看
                String img = useTree ? null : screenshot();
                if (!useTree && img == null) return L.t("截圖失敗：請確認 root 已允許 PhoneAI。");
                List<String> lessonApps = new ArrayList<>(st.opened);   // 只帶「這次用到的 App」的經驗，不是全部
                String fg = ElementList.foregroundPackage();
                if (fg != null && !lessonApps.contains(fg)) lessonApps.add(fg);
                List<String> lessons = Memory.lessonsFor(c, lessonApps, task);
                JSONObject action;
                long tokBefore = Usage.tallyNow();
                long[] cntBefore = Usage.tallyCounts();
                double costBefore = Usage.tallyCost();
                // 元件清單開著、但第一步讀不到元件（桌面）：第一步通常只是開 App，先用 Haiku＋截圖；它回了要座標的動作再升級 Sonnet。
                boolean cheapFirst = treeOn && step == 1 && !useTree;
                String model = useTree || cheapFirst ? TREE_MODEL : MODEL;
                String retryRaw = null;   // Haiku 的回覆看不懂、改用 Sonnet 重問時，留下 Haiku 回覆的開頭給任務過程看
                try {
                    action = askModel(key, task, history, img, lessons, model, useTree ? ElementList.describe(tree) : null);
                    if (cheapFirst && action != null && needsCoords(action.optString("action"))) {
                        model = MODEL;
                        action = askModel(key, task, history, img, lessons, model, null);
                    }
                    if (action == null && model.equals(TREE_MODEL)) {   // Haiku 的回覆解析不出動作：用 Sonnet＋截圖重問一次
                        retryRaw = lastRaw;
                        String shot = img != null ? img : screenshot();
                        if (shot != null) {
                            model = MODEL;
                            action = askModel(key, task, history, shot, lessons, model, null);
                        }
                    }
                } catch (Exception e) {
                    if (cancelled) return L.t("已停止。");
                    return L.t("連不上 AI：") + e.getMessage();
                }
                if (action == null) {
                    String raw = lastRaw == null ? "" : lastRaw;
                    return L.t("AI 的回答看不懂，已停止。") + (raw.isEmpty() ? "" : String.format(L.t("（模型回覆開頭：%s）"), raw));
                }
                if (cancelled) return L.t("已停止。");
                if (useTree && !ElementList.pickAll(action, tree)) {
                    history.add(step + ". 有找不到的元件編號，下一步改看截圖");
                    forceShot = true;
                    continue;
                }
                JSONObject det = detail(step, action, Usage.tallyNow() - tokBefore);
                details.add(det);
                int historyBefore = history.size();
                double wait = action.optDouble("wait", 1.2);
                String name = action.optString("action");
                waits = "wait".equals(name) ? waits + 1 : 0;
                if (st.dragDown && !name.startsWith("drag_") && !name.equals("wait")) {
                    releaseDrag(st);
                    history.add(step + ". 拖曳到一半，因為換了別的動作，已放開手指");
                }
                rememberLesson(c, action, st, history);
                switch (name) {
                    case "done":
                        finalSay = action.optString("say", L.t("完成了。"));
                        break;
                    case "fail":
                        finalSay = L.t("做不到：") + action.optString("say", L.t("（沒有原因）"));
                        break;
                    case "ask_user": {
                        String q = action.optString("question", L.t("請告訴我你要怎麼做。"));
                        OverlayService.showStep(c, L.t("等你回答問題"), step, MAX_STEPS);
                        String ans = ui.ask(q);
                        if (ans == null) { finalSay = q; break; }   // 這個介面沒辦法問：把問題當成回答
                        if (ans.trim().isEmpty()) return L.t("已取消。");
                        history.add(step + ". ask_user 問「" + q + "」，使用者回答：" + ans);
                        break;
                    }
                    case "ask": {
                        boolean manual = action.optBoolean("manual");
                        String why = action.optString("why", L.t("這一步需要你確認"));
                        OverlayService.showStep(c, L.t("等你確認"), step, MAX_STEPS);
                        if (!ui.confirm(manual ? why + L.t("\n請自己動手，完成後按確定。") : why + L.t("\n要繼續嗎？"))) return L.t("已取消。");
                        history.add(step + ". ask 使用者同意（" + why + "）");
                        break;
                    }
                    case "search": {
                        String q = action.optString("q").trim();
                        if (q.isEmpty() || st.searches >= SEARCH_MAX) {
                            history.add(step + ". 搜尋次數已用完或沒有關鍵字，請改用畫面操作");
                            break;
                        }
                        st.searches++;
                        ui.status(L.t("第 ") + step + L.t("／") + MAX_STEPS + L.t(" 步 · 搜尋網路中"));
                        String ans = webSearch(key, q);
                        history.add(step + ". " + (ans != null ? "搜尋「" + q + "」得到：" + (ans.length() > 300 ? ans.substring(0, 300) : ans)
                                : "搜尋「" + q + "」失敗，請改用畫面操作"));
                        break;
                    }
                    case "batch": {
                        List<JSONObject> subs = filterBatch(action.optJSONArray("actions"));
                        if (subs.isEmpty()) { history.add(step + ". batch 裡沒有可執行的動作，請直接給單一動作"); break; }
                        StringBuilder log = new StringBuilder(step + ". 連續執行" + subs.size() + "個動作（中間不看畫面）：");
                        hideBeforeOperate(ui, panelHidden, "batch");
                        for (JSONObject sub : subs) {
                            String r = execute(sub, wh, c, st);
                            log.append(" [").append(sub.optString("action")).append(" → ").append(r).append("]");
                            sleep(400);
                        }
                        history.add(log.toString());
                        break;
                    }
                    default: {
                        hideBeforeOperate(ui, panelHidden, name);
                        String result = execute(action, wh, c, st);
                        String think = action.optString("think");
                        history.add(step + ". " + action + (think.isEmpty() ? "" : "（" + think + "）") + " → " + result);
                        // 指令類動作（亮度、Wi-Fi、藍牙、音量）：模型說「做完任務就完成」(finish) 而且指令成功，就直接結束，不再多問一次模型
                        String fin = action.optString("finish", "").trim();
                        if (!fin.isEmpty() && (name.equals("brightness") || name.equals("cmd")) && result.startsWith("已")) finalSay = fin;
                        break;
                    }
                }
                try {
                    if (retryRaw != null) det.put("retry", retryRaw);
                    if (treeOn) det.put("tree", treeNote);   // 診斷：這一步有沒有用元件清單
                    usage(det, model, cntBefore, costBefore);   // 含搜尋等這一步之後才花的 token
                    det.put("result", finalSay != null ? finalSay : history.size() > historyBefore ? history.get(history.size() - 1) : "");
                } catch (Exception ignored) { }
                if (finalSay == null) sleep((long) (Math.min(Math.max(wait, 0.5), 6) * 1000));
            }
        } finally {
            if (st.dragDown) releaseDrag(st);   // 任務結束一定放開手指
        }
        if (finalSay != null) {
            String mode = c.getSharedPreferences("cfg", Context.MODE_PRIVATE).getString("reply_card", "auto");
            showCard = "always".equals(mode) || (!"never".equals(mode) && (st.searches > 0 || finalSay.length() > 30));
        }   // 搜尋結果或較長的回答才叫出回答卡片，免得「完成了」蓋住剛打開的畫面
        return finalSay != null ? finalSay : L.t("已到最大步數（") + MAX_STEPS + L.t("），還沒完成。");
    }

    // ---------- 螢幕 ----------

    static int[] screenSize() {
        Matcher m = Pattern.compile("(\\d+)x(\\d+)").matcher(Maintenance.suRun("wm size"));
        int w = 1080, h = 2340;
        while (m.find()) { w = Integer.parseInt(m.group(1)); h = Integer.parseInt(m.group(2)); }
        return new int[]{w, h};
    }

    /** root 截圖 → 縮成寬 WIDTH 的 JPEG → base64。失敗回 null。 */
    static String screenshot() {
        try {
            Process p = new ProcessBuilder(Root.su(), "-c", "screencap -p").redirectErrorStream(false).start();
            p.getOutputStream().close();
            byte[] png = readAll(p.getInputStream());
            p.waitFor();
            if (png.length < 100) return null;
            Bitmap bmp = BitmapFactory.decodeByteArray(png, 0, png.length);
            if (bmp == null) return null;
            int h = Math.round(bmp.getHeight() * (WIDTH / (float) bmp.getWidth()));
            Bitmap small = Bitmap.createScaledBitmap(bmp, WIDTH, h, true);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            small.compress(Bitmap.CompressFormat.JPEG, 80, out);
            return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        return bo.toByteArray();
    }

    // ---------- 模型 ----------

    /** img 為 null 且 elements 有值時是元件清單模式：不送截圖，只送編號清單。 */
    private static JSONObject askModel(String key, String task, List<String> history, String img, List<String> lessons,
                                       String model, String elements) throws Exception {
        JSONArray content = new JSONArray();
        if (img != null) {
            content.put(new JSONObject().put("type", "image").put("source",
                    new JSONObject().put("type", "base64").put("media_type", "image/jpeg").put("data", img)));
        }
        StringBuilder text = new StringBuilder("任務：").append(task);
        if (!lessons.isEmpty()) {
            text.append("\n已學到的經驗（之前記下來的規律，照用）：\n");
            for (String l : lessons) text.append("・").append(l).append('\n');
        }
        if (!history.isEmpty()) {
            text.append("\n已執行的動作（最近的在後面）：\n");
            for (int i = Math.max(0, history.size() - 5); i < history.size(); i++) text.append(history.get(i)).append('\n');
        }
        if (elements != null) {
            text.append("\n畫面元件清單（編號. 種類「文字」(大約位置x,y，0到1000)）：\n").append(elements);
            text.append("這一步沒有截圖。要點哪個元件就回 {\"action\":\"tap\",\"pick\":編號}，不要給 x、y；清單裡沒有你要的，就先 scroll 看看。\n");
            text.append("標了「已聚焦」的輸入框已經可以直接輸入（用 type），不用再點它一次。\n");
            text.append("連續點好幾個、而且點完畫面不會換頁的（例如計算機的數字鍵），用 {\"action\":\"batch\",\"actions\":[{\"action\":\"tap\",\"pick\":編號},...]}，一次做完。\n");
            text.append("要確認輸入了什麼或結果是什麼，看種類是「輸入框」或位置在最上方的大顯示區。畫面連續幾步都沒變，就換個方法，不要一直 wait。\n");
        }
        text.append("\n請決定下一步，只輸出一行 JSON。");
        content.put(new JSONObject().put("type", "text").put("text", text.toString()));
        JSONArray messages = new JSONArray().put(new JSONObject().put("role", "user").put("content", content));
        int maxTok = model.equals(TREE_MODEL) ? 2000 : 800;   // Haiku 常把額度花在思考，沒留下答案
        JSONObject body = new JSONObject().put("model", model).put("max_tokens", maxTok)
                .put("system", new JSONArray().put(new JSONObject().put("type", "text")
                        .put("text", L.en() ? AgentPrompt.SYSTEM_EN : AgentPrompt.SYSTEM)
                        .put("cache_control", new JSONObject().put("type", "ephemeral"))))   // 系統提示詞快取：每一步重複送，命中時輸入費用約降到 1/20
                .put("messages", messages);
        JSONObject r = ChatEngine.post(body, key);
        if (emptyAtLimit(r)) {   // 思考用光額度、沒有答案：同一個模型放大額度再問一次，不急著升級 Sonnet
            body.put("max_tokens", 4000);
            r = ChatEngine.post(body, key);
        }
        JSONArray out = r.optJSONArray("content");
        lastRaw = "";
        if (out == null) { lastRaw = "（沒有內容）"; return null; }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < out.length(); i++) if ("text".equals(out.getJSONObject(i).optString("type"))) sb.append(out.getJSONObject(i).optString("text"));
        lastRaw = sb.length() == 0 ? "（空）" + r.optString("stop_reason") : sb.substring(0, Math.min(sb.length(), 80));
        if (sb.indexOf("<invoke") >= 0) {   // 模型有時回成工具呼叫的 XML 格式，不是 JSON
            JSONObject inv = parseInvoke(sb.toString());
            if (inv != null) return inv;
        }
        return parseLast(sb.toString());
    }

    /** 回覆被額度截斷、而且沒有任何文字（思考把額度用光了）。 */
    private static boolean emptyAtLimit(JSONObject r) {
        if (!"max_tokens".equals(r.optString("stop_reason"))) return false;
        JSONArray c = r.optJSONArray("content");
        for (int i = 0; c != null && i < c.length(); i++) {
            JSONObject b = c.optJSONObject(i);
            if (b != null && "text".equals(b.optString("type")) && !b.optString("text").trim().isEmpty()) return false;
        }
        return true;
    }

    /** <invoke name="動作"><parameter name="欄位">值</parameter>…</invoke> 轉成一般的動作 JSON；轉不出來回 null。 */
    static JSONObject parseInvoke(String out) {
        Matcher m = Pattern.compile("<invoke\\s+name=\"([^\"]+)\"\\s*>(.*?)(</invoke>|$)", Pattern.DOTALL).matcher(out);
        if (!m.find()) return null;
        try {
            JSONObject o = new JSONObject().put("action", m.group(1));
            Matcher p = Pattern.compile("<parameter\\s+name=\"([^\"]+)\"\\s*>(.*?)</parameter>", Pattern.DOTALL).matcher(m.group(2));
            while (p.find()) {
                String k = p.group(1), v = p.group(2).trim();
                if (v.matches("-?\\d+")) o.put(k, Long.parseLong(v));
                else if (v.matches("-?\\d*\\.\\d+")) o.put(k, Double.parseDouble(v));
                else if (v.equals("true") || v.equals("false")) o.put(k, Boolean.parseBoolean(v));
                else if (v.startsWith("[")) o.put(k, new JSONArray(v));
                else if (v.startsWith("{")) o.put(k, new JSONObject(v));
                else o.put(k, v);
            }
            return o;
        } catch (Exception e) {
            return null;
        }
    }

    /** 取最後一個 JSON 物件（能處理巢狀，例如 batch 的 actions 陣列）。 */
    static JSONObject parseLast(String out) {
        JSONObject found = null;
        int i = 0;
        while ((i = out.indexOf('{', i)) >= 0) {
            int depth = 0, end = -1;
            boolean inStr = false;
            for (int k = i; k < out.length(); k++) {
                char ch = out.charAt(k);
                if (inStr) { if (ch == '\\') k++; else if (ch == '"') inStr = false; continue; }
                if (ch == '"') inStr = true;
                else if (ch == '{') depth++;
                else if (ch == '}' && --depth == 0) { end = k; break; }
            }
            if (end < 0) break;
            try { found = new JSONObject(out.substring(i, end + 1)); } catch (Exception ignored) { }
            i = end + 1;
        }
        return found;
    }

    /** Claude 的網頁搜尋（server tool），回傳繁體中文摘要；失敗回 null。 */
    static String webSearch(String key, String query) {
        try {
            JSONObject tool = new JSONObject().put("type", "web_search_20250305").put("name", "web_search").put("max_uses", 1)
                    .put("user_location", new JSONObject().put("type", "approximate").put("country", "TW").put("timezone", "Asia/Taipei"));
            JSONArray msgs = new JSONArray().put(new JSONObject().put("role", "user")
                    .put("content", (L.en() ? "Answer in English based on the web search results; facts only, under 100 words: " : "請用繁體中文回答，根據網路搜尋結果，只講事實，不超過120字：") + query));
            StringBuilder text = new StringBuilder();
            for (int turn = 0; turn < 3; turn++) {
                JSONObject body = new JSONObject().put("model", SEARCH_MODEL).put("max_tokens", 700)
                        .put("tools", new JSONArray().put(tool)).put("messages", msgs);
                JSONObject r = ChatEngine.post(body, key);
                JSONArray content = r.optJSONArray("content");
                for (int i = 0; content != null && i < content.length(); i++) {
                    JSONObject b = content.getJSONObject(i);
                    if ("text".equals(b.optString("type"))) text.append(b.optString("text"));
                }
                if (!"pause_turn".equals(r.optString("stop_reason"))) break;
                msgs.put(new JSONObject().put("role", "assistant").put("content", content));
            }
            return text.length() > 0 ? text.toString().trim() : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- 經驗 ----------

    /** 模型說「remember」：馬上記下來（下一步就看得到），一個任務最多 3 則，同樣的話不重複存。 */
    private static void rememberLesson(Context c, JSONObject action, State st, List<String> history) {
        String rem = action.optString("remember").trim();
        if (rem.isEmpty() || st.remembers >= MAX_REMEMBER || rem.length() > 200) return;
        st.remembers++;
        try {
            String app = !st.opened.isEmpty() ? st.opened.get(0) : ElementList.foregroundPackage();   // 都沒有才存成通用
            if (Memory.addLesson(c, rem, app)) {
                st.remembered.add(rem);
                Notify.notifyText(c, L.t("AI 學到的經驗（可在 App 記憶頁刪除）"), rem, 7195);
            }
        } catch (Exception ignored) { }
    }

    // ---------- 動作 ----------

    private static int px(double rel, int total) { return (int) Math.round(rel * total / 1000.0); }

    private static String sh(String cmd) { return Maintenance.suRun(cmd); }

    private static List<JSONObject> filterBatch(JSONArray arr) {
        List<JSONObject> out = new ArrayList<>();
        for (int i = 0; arr != null && i < arr.length() && out.size() < BATCH_LIMIT; i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null && in(BATCH_OK, o.optString("action"))) out.add(o);
        }
        return out;
    }

    /** 點擊或滑動後畫漣漪（無障礙服務開著時才有；在主執行緒畫）。起點等於終點就是點擊。 */
    private static void ripple(int x1, int y1, int x2, int y2) {
        MAIN.post(() -> {
            RippleView v = RippleAccessibilityService.view();
            if (v == null) return;
            if (x1 == x2 && y1 == y2) v.ripple(x1, y1);
            else v.swipe(x1, y1, x2, y2);
        });
    }

    /** 執行一個動作，回傳給下一步看的結果文字。 */
    static String execute(JSONObject a, int[] wh, Context c, State st) {
        int w = wh[0], h = wh[1];
        String name = a.optString("action");
        try {
            switch (name) {
                case "tap": {
                    int x = px(a.optDouble("x"), w), y = px(a.optDouble("y"), h);
                    sh("input tap " + x + " " + y);
                    ripple(x, y, x, y);
                    return "已點擊";
                }
                case "longpress": {
                    int x = px(a.optDouble("x"), w), y = px(a.optDouble("y"), h);
                    sh("input swipe " + x + " " + y + " " + x + " " + y + " " + clamp(a.optInt("ms", 800), 300, 5000));
                    ripple(x, y, x, y);
                    return "已長按";
                }
                case "swipe": {
                    int x1 = px(a.optDouble("x1"), w), y1 = px(a.optDouble("y1"), h), x2 = px(a.optDouble("x2"), w), y2 = px(a.optDouble("y2"), h);
                    sh("input swipe " + x1 + " " + y1 + " " + x2 + " " + y2 + " " + clamp(a.optInt("ms", 300), 50, 5000));
                    ripple(x1, y1, x2, y2);
                    return "已滑動";
                }
                case "drag": {
                    int x1 = px(a.optDouble("x1"), w), y1 = px(a.optDouble("y1"), h), x2 = px(a.optDouble("x2"), w), y2 = px(a.optDouble("y2"), h);
                    int ms = clamp(a.optInt("ms", 500), 50, 5000), hold = clamp(a.optInt("hold_ms", 600), 200, 5000);
                    if (hold > 0 && (x1 != x2 || y1 != y2)) sh("input draganddrop " + x1 + " " + y1 + " " + x2 + " " + y2 + " " + (ms + hold));
                    else sh("input swipe " + x1 + " " + y1 + " " + x2 + " " + y2 + " " + (ms + hold));
                    return "已拖曳";
                }
                case "drag_start": {
                    if (st.measures >= MAX_MEASURES) return "你已經分段拖曳量過" + st.measures + "次了，不要再量；用 remember 記下規律，之後直接用 drag 或 batch";
                    st.measures++;
                    int x = px(a.optDouble("x"), w), y = px(a.optDouble("y"), h);
                    int tx = px(a.optDouble("to_x"), w), ty = px(a.optDouble("to_y"), h);
                    sh(dragScript("start", x, y, tx, ty));
                    st.dragDown = true;
                    st.dx = tx;
                    st.dy = ty;
                    return "手指按住並拖到目的地，還沒放開（看新畫面再決定 drag_move、drag_end 或 drag_cancel）";
                }
                case "drag_move": {
                    if (!st.dragDown) return "現在沒有按著的手指，請先用 drag_start";
                    int tx = a.has("x") ? px(a.optDouble("x"), w) : st.dx, ty = a.has("y") ? px(a.optDouble("y"), h) : st.dy;
                    sh(dragScript("move", st.dx, st.dy, tx, ty));
                    st.dx = tx;
                    st.dy = ty;
                    return "手指移到新位置，還沒放開";
                }
                case "drag_end": {
                    if (!st.dragDown) return "現在沒有按著的手指";
                    if (a.has("x") && a.has("y")) {
                        int tx = px(a.optDouble("x"), w), ty = px(a.optDouble("y"), h);
                        sh(dragScript("end", st.dx, st.dy, tx, ty));
                    } else sh(dragScript("end", st.dx, st.dy, st.dx, st.dy));
                    st.dragDown = false;
                    return "已放開手指";
                }
                case "drag_cancel":
                    if (st.dragDown) releaseDrag(st);
                    return "已取消拖曳";
                case "multitouch":
                    return multitouch(a, w, h, c);
                case "scroll": {
                    boolean down = !"up".equals(a.optString("dir", "down"));
                    int x = w / 2, y1 = (int) (h * 0.7), y2 = (int) (h * 0.3);
                    if (!down) { int t = y1; y1 = y2; y2 = t; }
                    sh("input swipe " + x + " " + y1 + " " + x + " " + y2 + " 300");
                    return "已捲動";
                }
                case "back": sh("input keyevent 4"); return "已按返回";
                case "home": sh("input keyevent 3"); return "已回桌面";
                case "enter": sh("input keyevent 66"); return "已按 Enter";
                case "wait":
                    sleep((long) (Math.min(Math.max(a.optDouble("wait", 1), 0.5), 6) * 1000));
                    return "已等待";
                case "type":
                    return type(a.optString("text"), a.optBoolean("clear", true), a.optBoolean("enter", false), c);
                case "open_url": {
                    String url = a.optString("url", "").trim();
                    if (!url.matches("(?i)^(https?|market)://[^\\s'\"`;|&<>]+$")) return "網址格式不對或不允許：" + url;
                    sh("am start -a android.intent.action.VIEW -d '" + url.replace("'", "") + "'");
                    return "已開啟網址 " + (url.length() > 60 ? url.substring(0, 60) + "…" : url);
                }
                case "open": {
                    String pkg = a.optString("pkg", "");
                    if (pkg.isEmpty()) pkg = findPackage(c, a.optString("name"));   // 提示寫「程式會用 name 查套件」，這裡真的去查
                    if (pkg == null || pkg.isEmpty()) return "找不到名稱叫「" + a.optString("name") + "」的 App";
                    sh("monkey -p " + pkg + " -c android.intent.category.LAUNCHER 1");
                    st.opened.add(pkg);
                    return "已開啟 " + pkg;
                }
                case "brightness": {
                    int pct = clamp(a.optInt("level", 50), 0, 100);
                    sh("settings put system screen_brightness_mode 0");   // 手動亮度；自動亮度開著會把設定蓋掉
                    sh("settings put system screen_brightness " + Math.max(1, pct * 255 / 100));
                    return "已把亮度調到 " + pct + "%（自動亮度已關閉）";
                }
                case "cmd":
                    return cmd(a.optString("name"));
                case "shell": {
                    String cmd = a.optString("cmd");
                    if (!readOnly(cmd)) return "拒絕：這不是唯讀指令";
                    String out = sh(cmd);
                    return out.length() > 600 ? out.substring(0, 600) + "…（太長已截斷，用 grep 只取需要的行）" : out;   // 整段會塞進之後每一步的歷史，太長很花 token
                }
                default:
                    return "「" + name + "」尚未支援（之後會搬進來）";
            }
        } catch (Exception e) {
            return "執行失敗：" + e.getMessage();
        }
    }

    /** 分段拖曳用 `input motionevent`：每個事件各自送出，手指在事件之間一直按著。op: start／move／end／cancel（像素座標）。 */
    static String dragScript(String op, int x0, int y0, int x1, int y1) {
        List<String> parts = new ArrayList<>();
        int ex = x1, ey = y1;
        if (op.equals("start")) {
            parts.add(ev("DOWN", x0, y0));
            parts.add("sleep 0.35");
            parts.addAll(moves(x0, y0, x1, y1));
        } else if (op.equals("move")) {
            parts.addAll(moves(x0, y0, x1, y1));
        } else if (op.equals("end")) {
            if (x0 != x1 || y0 != y1) parts.addAll(moves(x0, y0, x1, y1));
            parts.add(ev("UP", x1, y1));
        } else {
            parts.add(ev("CANCEL", x0, y0));
        }
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) sb.append("; sleep 0.04; ");
            sb.append(p);
        }
        return sb.toString();
    }

    private static String ev(String kind, int x, int y) {
        return "input motionevent " + kind + " " + x + " " + y;
    }

    /** 移動拆成每約 90 像素一格的 MOVE 事件，遊戲才看得到「拖著走」。 */
    private static List<String> moves(int x0, int y0, int x1, int y1) {
        int n = clamp(Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) / 90, 1, 10);
        List<String> out = new ArrayList<>();
        for (int k = 1; k <= n; k++) out.add(ev("MOVE", x0 + (x1 - x0) * k / n, y0 + (y1 - y0) * k / n));
        return out;
    }

    /** 多指觸控：每根手指各自從起點滑到終點（起點=終點 = 按住不動），先全部按下、按住 hold_ms，再一起移動（最多 4 根）。 */
    private static String multitouch(JSONObject a, int w, int h, Context c) {
        JSONArray arr = a.optJSONArray("fingers");
        if (arr == null || arr.length() == 0) return "沒有手指的資料";
        int n = Math.min(arr.length(), 4);
        double[][] from = new double[n][2], to = new double[n][2];
        int[] ms = new int[n];
        for (int i = 0; i < n; i++) {
            JSONObject f = arr.optJSONObject(i);
            if (f == null) return "手指資料格式不對";
            from[i][0] = px(f.optDouble("x1"), w); from[i][1] = px(f.optDouble("y1"), h);
            to[i][0] = px(f.optDouble("x2"), w); to[i][1] = px(f.optDouble("y2"), h);
            ms[i] = clamp(f.optInt("ms", 400), 50, 5000);
        }
        if (!ensureTouch(c)) return "多指觸控啟動失敗（請確認 root 已允許 PhoneAI）";
        double[][] cur = new double[n][];
        for (int i = 0; i < n; i++) cur[i] = from[i].clone();
        LiveInput.send(eLine("DOWN", 0, cur, 1));
        for (int i = 1; i < n; i++) LiveInput.send(eLine("PDOWN", i, cur, i + 1));
        sleep(clamp(a.optInt("hold_ms", 0), 0, 5000));
        int total = 0;
        for (int m : ms) total = Math.max(total, m);
        int steps = clamp(total / 40, 3, 60);
        long stepMs = total / steps;
        for (int s = 1; s <= steps; s++) {
            for (int i = 0; i < n; i++) {
                double frac = Math.min(1.0, s * (double) stepMs / ms[i]);
                cur[i][0] = from[i][0] + (to[i][0] - from[i][0]) * frac;
                cur[i][1] = from[i][1] + (to[i][1] - from[i][1]) * frac;
            }
            LiveInput.send(eLine("MOVE", 0, cur, n));
            sleep(stepMs);
        }
        for (int i = n - 1; i >= 1; i--) LiveInput.send(eLine("PUP", i, cur, i + 1));
        LiveInput.send(eLine("UP", 0, cur, 1));
        return "多指觸控完成（" + n + " 根手指）";
    }

    /** 送給 InputServer 的多指指令：E 動作 指針索引 指針數 x0 y0 ...（座標是像素）。 */
    private static String eLine(String kind, int idx, double[][] cur, int count) {
        StringBuilder sb = new StringBuilder("E ").append(kind).append(' ').append(idx).append(' ').append(count);
        for (int i = 0; i < count; i++) sb.append(' ').append(Math.round(cur[i][0])).append(' ').append(Math.round(cur[i][1]));
        return sb.toString();
    }

    /** 確保多指用的 InputServer（主螢幕，編號 0）已經在跑；最多等 4 秒。 */
    private static boolean ensureTouch(Context c) {
        if (LiveInput.ready()) return true;
        LiveInput.start(c, 0);
        for (int i = 0; i < 40 && !LiveInput.ready(); i++) sleep(100);
        return LiveInput.ready();
    }

    private static void releaseDrag(State st) {
        sh(dragScript("end", st.dx, st.dy, st.dx, st.dy));
        st.dragDown = false;
    }

    /** 這類動作要靠截圖算座標，Haiku 常點錯。 */
    private static boolean needsCoords(String name) {
        switch (name) {
            case "tap": case "longpress": case "swipe": case "drag": case "drag_start": case "multitouch": case "batch": return true;
            default: return false;
        }
    }

    /** 依 App 顯示名稱找已安裝的套件名稱：先完全相符，再找名稱互相包含的；找不到回 null。 */
    private static String findPackage(Context c, String name) {
        String n = Router.norm(name);
        if (n.isEmpty()) return null;
        java.util.Map<String, String> apps = Router.apps(c);
        String hit = apps.get(n);
        if (hit != null) return hit;
        for (java.util.Map.Entry<String, String> e : apps.entrySet()) {
            String k = e.getKey();
            if (k.length() >= 2 && (k.contains(n) || n.contains(k))) return e.getValue();
        }
        return null;
    }

    private static String cmd(String name) {
        switch (name) {
            case "wifi_on": sh("svc wifi enable"); return "已開啟 Wi-Fi";
            case "wifi_off": sh("svc wifi disable"); return "已關閉 Wi-Fi";
            case "bluetooth_on": sh("svc bluetooth enable"); return "已開啟藍牙";
            case "bluetooth_off": sh("svc bluetooth disable"); return "已關閉藍牙";
            case "volume_up": sh("input keyevent 24"); return "已調大音量";
            case "volume_down": sh("input keyevent 25"); return "已調小音量";
            case "dark_mode_on": sh("cmd uimode night yes"); return "已開啟深色模式";
            case "dark_mode_off": sh("cmd uimode night no"); return "已關閉深色模式";
            case "dnd_on": sh("cmd notification set_dnd on"); return "已開啟勿擾模式";
            case "dnd_off": sh("cmd notification set_dnd off"); return "已關閉勿擾模式";
            case "auto_rotate_on": sh("settings put system accelerometer_rotation 1"); return "已開啟自動旋轉";
            case "auto_rotate_off": sh("settings put system accelerometer_rotation 0"); return "已關閉自動旋轉";
            case "location_on": sh("cmd location set-location-enabled true"); return "已開啟定位";
            case "location_off": sh("cmd location set-location-enabled false"); return "已關閉定位";
            case "notifications_open": sh("cmd statusbar expand-notifications"); return "已打開通知欄";
            case "quick_settings_open": sh("cmd statusbar expand-settings"); return "已打開快速設定";
            case "panels_close": sh("cmd statusbar collapse"); return "已收起通知欄";
            default: return "不認識的命令：" + name;
        }
    }

    /** 送出輸入框：先用輸入法的動作鍵（搜尋／前往），沒用才送假的 Enter 鍵。 */
    private static void pressEnter() {
        if (!RippleAccessibilityService.imeEnter()) sh("input keyevent 66");
    }

    /** 文字輸入：ASCII 直接 input text；中文等用剪貼簿貼上。clear = 先全選再刪。 */
    private static String type(String text, boolean clear, boolean enter, Context c) {
        if (text == null) text = "";
        if (!text.isEmpty() && RippleAccessibilityService.setFocusedText(text, !clear)) {
            // 無障礙直接把文字填進焦點欄位：不經過鍵盤，中文鍵盤也不會變成注音，也不碰剪貼簿
            if (enter) { sleep(200); pressEnter(); }
            return "已輸入" + (text.length() > 30 ? text.substring(0, 30) + "…" : text);
        }
        if (clear) { sh("input keycombination 113 29"); sh("input keyevent 67"); }
        if (text.matches("[A-Za-z0-9 ]*")) {
            if (!text.isEmpty()) {
                // 中文鍵盤打英文會變成注音或拼音候選字：先切到英文鍵盤，輸完再切回原本的
                String prev = asciiKeyboard();
                if (prev == null) return "目前是中文鍵盤，沒辦法直接輸入英文，請改用 open_url 開網址";
                try {
                    sh("input text " + text.replace(" ", "%s"));
                    sleep(300);
                } finally {
                    restoreKeyboard(prev);
                }
            }
        } else if (!text.isEmpty()) {
            if (!paste(c, text)) return "沒辦法輸入「" + text + "」：剪貼簿寫不進去（Android 的限制），不要再試貼上，改用 open_url 開網址";
            sh("input keyevent 279");   // KEYCODE_PASTE
        }
        if (enter) pressEnter();
        return "已輸入" + (text.length() > 30 ? text.substring(0, 30) + "…" : text);
    }

    /** 切到英文鍵盤（有的話）；回傳原本的鍵盤 id，之後要切回去。找不到英文鍵盤回傳 null（呼叫端就不輸入）。 */
    private static String asciiKeyboard() {
        String prev = sh("settings get secure default_input_method").trim();
        for (String line : sh("ime list -s").split("\n")) {
            String id = line.trim();
            if (id.isEmpty() || id.equals(prev)) continue;
            String low = id.toLowerCase(java.util.Locale.ROOT);
            if (low.contains("latin") || low.contains("english") || low.contains("qwerty")) {
                sh("ime set " + id);
                sleep(300);
                return prev.isEmpty() ? "" : prev;
            }
        }
        // 目前這個就是英文鍵盤（沒有別的選擇時）：不用切
        String low = prev.toLowerCase(java.util.Locale.ROOT);
        if (!prev.isEmpty() && (low.contains("latin") || low.contains("english") || low.contains("qwerty"))) return prev;
        return null;
    }

    private static void restoreKeyboard(String prev) {
        if (prev != null && !prev.isEmpty()) sh("ime set " + prev);
    }

    /** 把文字放進剪貼簿（Android 10 以後背景 App 可能寫不進去；實測時若失敗，之後改用 root 寫剪貼簿）。 */
    private static boolean paste(Context c, String text) {
        final boolean[] ok = {false};
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        MAIN.post(() -> {
            try {
                ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("ai", text));
                    // 寫不進去時系統會靜默失敗，剪貼簿還是舊的；讀回來比對，對不上就不能貼
                    ClipData cd = cm.getPrimaryClip();
                    ok[0] = cd != null && cd.getItemCount() > 0 && text.contentEquals(String.valueOf(cd.getItemAt(0).coerceToText(c)));
                }
            } catch (Exception ignored) { }
            done.countDown();
        });
        try { done.await(2, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
        sleep(300);
        return ok[0];
    }

    /** 唯讀 shell：白名單程式，後面只能接過濾程式，不能寫入、跳脫或子命令。 */
    static boolean readOnly(String cmd) {
        if (cmd == null || cmd.trim().isEmpty() || cmd.contains("\n")) return false;
        if (cmd.matches(".*[;&`$<>*?{}()\\\\].*") && !cmd.contains("|")) return false;
        String[] parts = cmd.split("\\|", -1);
        for (int i = 0; i < parts.length; i++) {
            String[] words = parts[i].trim().split("\\s+");
            String prog = words[0];
            boolean ok = i == 0 ? in(READ_BINS, prog) : in(PIPE_OK, prog);
            if (!ok) return false;
            if (i == 0 && (prog.equals("settings") || prog.equals("cmd") || prog.equals("pm") || prog.equals("am") || prog.equals("wm"))
                    && words.length > 1 && !in(new String[]{"get", "list", "status", "size", "packages", "-h", "print-stats", "wifi"}, words[1])) return false;
        }
        return true;
    }

    private static boolean in(String[] arr, String s) {
        for (String x : arr) if (x.equals(s)) return true;
        return false;
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }
}
