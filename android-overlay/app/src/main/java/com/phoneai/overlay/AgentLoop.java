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
 * 自動操作迴圈（第一段）：截圖 → 交給 Claude 看圖決定下一步 → 用 root 執行 → 重複，直到 done／fail／ask_user。
 * 照搬 scripts/agent2.py 的核心：同一份系統提示、同一份動作格式、同一份敏感字規則。
 * 還沒搬的（之後的段落）：背景虛擬螢幕、分段拖曳、多指觸控、批次動作、經驗記錄、搜尋、模型升級。遇到這些動作會回報「尚未支援」並繼續。
 */
final class AgentLoop {
    static final String MODEL = "claude-sonnet-5-5";
    static final int WIDTH = 432;          // 截圖寬度（省 token，跟 agent2.py 預設一樣）
    private static final int MAX_STEPS = 8;
    private static final String[] TASK_BLOCK = {"付款", "轉帳", "匯款", "購買", "支付", "刪除", "密碼", "下單"};
    private static final String[] READ_BINS = {"dumpsys", "getprop", "date", "settings", "cmd", "pm", "am", "ls", "cat", "df", "uptime", "free", "ps", "top", "wm", "uname", "cmd"};
    private static final String[] PIPE_OK = {"grep", "head", "tail", "sort", "cut", "wc", "uniq", "tr"};
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    interface Ui {
        void hide();                    // 收起助理面板，讓畫面可以操作
        void status(String text);       // 通知列上的進度文字
        boolean confirm(String msg);    // 敏感操作的確認框（在背景執行緒呼叫，會等使用者按）
    }

    private AgentLoop() { }

    /** 執行任務；回傳要告訴使用者的文字。 */
    static String run(Context c, String task, Ui ui) {
        for (String w : TASK_BLOCK)
            if (task.contains(w) && !ui.confirm("任務含「" + w + "」這類敏感操作，要繼續嗎？")) return "已取消，沒有執行。";
        String key = ChatEngine.key(c);
        if (key == null || key.isEmpty()) return "還沒有 Claude API Key：請到「功能 → 維護 → Claude API Key」設定。";
        int[] wh = screenSize();
        ui.hide();
        sleep(600);
        List<String> history = new ArrayList<>();
        for (int step = 1; step <= MAX_STEPS; step++) {
            ui.status("第 " + step + "／" + MAX_STEPS + " 步");
            String img = screenshot();
            if (img == null) return "截圖失敗：請確認 root 已允許 PhoneAI。";
            JSONObject action;
            try {
                action = askModel(key, task, history, img);
            } catch (Exception e) {
                return "連不上 AI：" + e.getMessage();
            }
            if (action == null) return "AI 的回答看不懂，已停止。";
            String name = action.optString("action");
            String think = action.optString("think");
            switch (name) {
                case "done":
                    return action.optString("say", "完成了。");
                case "fail":
                    return "做不到：" + action.optString("say", "（沒有原因）");
                case "ask_user":
                    return action.optString("question", "請告訴我你要怎麼做。");
                case "ask": {
                    boolean manual = action.optBoolean("manual");
                    String why = action.optString("why", "這一步需要你確認");
                    if (!ui.confirm(manual ? why + "\n請自己動手，完成後按確定。" : why + "\n要繼續嗎？")) return "已取消。";
                    history.add(step + ". ask 使用者同意（" + why + "）");
                    break;
                }
                default: {
                    String result = execute(action, wh, c);
                    history.add(step + ". " + action + (think.isEmpty() ? "" : "（" + think + "）") + " → " + result);
                    break;
                }
            }
            double wait = action.optDouble("wait", 1.2);
            sleep((long) (Math.min(Math.max(wait, 0.5), 6) * 1000));
        }
        return "已到最大步數（" + MAX_STEPS + "），還沒完成。";
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

    private static JSONObject askModel(String key, String task, List<String> history, String img) throws Exception {
        JSONArray content = new JSONArray();
        content.put(new JSONObject().put("type", "image").put("source",
                new JSONObject().put("type", "base64").put("media_type", "image/jpeg").put("data", img)));
        StringBuilder text = new StringBuilder("任務：").append(task);
        if (!history.isEmpty()) {
            text.append("\n已執行的動作（最近的在後面）：\n");
            for (int i = Math.max(0, history.size() - 8); i < history.size(); i++) text.append(history.get(i)).append('\n');
        }
        text.append("\n請決定下一步，只輸出一行 JSON。");
        content.put(new JSONObject().put("type", "text").put("text", text.toString()));
        JSONArray messages = new JSONArray().put(new JSONObject().put("role", "user").put("content", content));
        JSONObject body = new JSONObject().put("model", MODEL).put("max_tokens", 800)
                .put("system", AgentPrompt.SYSTEM).put("messages", messages);
        JSONObject r = ChatEngine.post(body, key);
        JSONArray out = r.optJSONArray("content");
        if (out == null) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < out.length(); i++) if ("text".equals(out.getJSONObject(i).optString("type"))) sb.append(out.getJSONObject(i).optString("text"));
        String s = sb.toString();
        int a = s.indexOf('{'), b = s.lastIndexOf('}');
        if (a < 0 || b <= a) return null;
        return new JSONObject(s.substring(a, b + 1));
    }

    // ---------- 動作 ----------

    private static int px(double rel, int total) { return (int) Math.round(rel * total / 1000.0); }

    private static String sh(String cmd) { return Maintenance.suRun(cmd); }

    /** 執行一個動作，回傳給下一步看的結果文字。 */
    static String execute(JSONObject a, int[] wh, Context c) {
        int w = wh[0], h = wh[1];
        String name = a.optString("action");
        try {
            switch (name) {
                case "tap":
                    sh("input tap " + px(a.optDouble("x"), w) + " " + px(a.optDouble("y"), h));
                    return "已點擊";
                case "longpress": {
                    int x = px(a.optDouble("x"), w), y = px(a.optDouble("y"), h);
                    sh("input swipe " + x + " " + y + " " + x + " " + y + " " + a.optInt("ms", 800));
                    return "已長按";
                }
                case "swipe":
                    sh("input swipe " + px(a.optDouble("x1"), w) + " " + px(a.optDouble("y1"), h) + " "
                            + px(a.optDouble("x2"), w) + " " + px(a.optDouble("y2"), h) + " " + a.optInt("ms", 300));
                    return "已滑動";
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
                case "open": {
                    String pkg = a.optString("pkg", "");
                    if (pkg.isEmpty()) return "沒有套件名稱，無法開啟「" + a.optString("name") + "」（之後會支援用名稱查）";
                    sh("monkey -p " + pkg + " -c android.intent.category.LAUNCHER 1");
                    return "已開啟 " + pkg;
                }
                case "cmd":
                    return cmd(a.optString("name"));
                case "shell": {
                    String cmd = a.optString("cmd");
                    if (!readOnly(cmd)) return "拒絕：這不是唯讀指令";
                    String out = sh(cmd);
                    return out.length() > 1500 ? out.substring(0, 1500) + "…" : out;
                }
                default:
                    return "「" + name + "」尚未支援（之後的段落會搬進來）";
            }
        } catch (Exception e) {
            return "執行失敗：" + e.getMessage();
        }
    }

    private static String cmd(String name) {
        switch (name) {
            case "wifi_on": sh("svc wifi enable"); return "已開啟 Wi-Fi";
            case "wifi_off": sh("svc wifi disable"); return "已關閉 Wi-Fi";
            case "bluetooth_on": sh("svc bluetooth enable"); return "已開啟藍牙";
            case "bluetooth_off": sh("svc bluetooth disable"); return "已關閉藍牙";
            case "volume_up": sh("input keyevent 24"); return "已調大音量";
            case "volume_down": sh("input keyevent 25"); return "已調小音量";
            default: return "不認識的命令：" + name;
        }
    }

    /** 文字輸入：ASCII 直接 input text；中文等用剪貼簿貼上。clear = 先全選再刪。 */
    private static String type(String text, boolean clear, boolean enter, Context c) {
        if (clear) { sh("input keycombination 113 29"); sh("input keyevent 67"); }
        if (text == null) text = "";
        if (text.matches("[A-Za-z0-9 ]*")) {
            if (!text.isEmpty()) sh("input text " + text.replace(" ", "%s"));
        } else if (!text.isEmpty()) {
            paste(c, text);
            sh("input keyevent 279");   // KEYCODE_PASTE
        }
        if (enter) sh("input keyevent 66");
        return "已輸入" + (text.length() > 30 ? text.substring(0, 30) + "…" : text);
    }

    /** 把文字放進剪貼簿（需要在主執行緒、App 在前景；背景時可能失敗，之後用 root 的剪貼簿指令改善）。 */
    private static void paste(Context c, String text) {
        MAIN.post(() -> {
            ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("ai", text));
        });
        sleep(400);
    }

    /** 唯讀 shell：第一段的程式在白名單裡，後面只能接過濾程式，不能寫入、跳脫或子命令（照搬 intents.py 的規則，簡化版）。 */
    static boolean readOnly(String cmd) {
        if (cmd == null || cmd.trim().isEmpty() || cmd.contains("\n")) return false;
        if (cmd.matches(".*[;&`$<>*?{}()\\\\].*") && !cmd.contains("|")) return false;
        String[] parts = cmd.split("\\|", -1);
        for (int i = 0; i < parts.length; i++) {
            String[] words = parts[i].trim().split("\\s+");
            String prog = words[0];
            boolean ok = i == 0 ? in(READ_BINS, prog) : in(PIPE_OK, prog);
            if (!ok) return false;
            if (i == 0 && (prog.equals("settings") || prog.equals("cmd") || prog.equals("pm") || prog.equals("am")
                    || prog.equals("wm")) && words.length > 1 && !in(new String[]{"get", "list", "status", "size", "packages", "-h", "print-stats"}, words[1])
                    && !(prog.equals("cmd") && words.length > 1 && words[1].equals("wifi"))) return false;
        }
        return true;
    }

    private static boolean in(String[] arr, String s) {
        for (String x : arr) if (x.equals(s)) return true;
        return false;
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }
}
