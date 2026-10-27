package com.phoneai.overlay;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * 任務歷史：每句話處理完就記一筆（files/tasks.json）。可以加星星；有星的排在最前面，其餘依時間新到舊。
 * 最多留 MAX 筆，超過時先刪最舊的沒加星的。
 */
final class TaskLog {
    private static final int MAX = 200;

    private TaskLog() { }

    private static File file(Context c) { return new File(c.getFilesDir(), "tasks.json"); }

    private static synchronized JSONArray load(Context c) {
        try {
            return new JSONArray(new String(Files.readAllBytes(file(c).toPath()), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private static synchronized void save(Context c, JSONArray a) throws Exception {
        File f = file(c), tmp = new File(f.getPath() + ".tmp");
        Files.write(tmp.toPath(), a.toString().getBytes(StandardCharsets.UTF_8));
        if (!tmp.renameTo(f)) throw new IllegalStateException("tasks.json rename failed");
    }

    /** 記下一筆任務（在背景執行緒呼叫）。 */
    static synchronized void add(Context c, String text, String reply) { add(c, text, reply, null, 0, 0); }

    /** details = 每一步的思考、動作、結果、token（沒有自動操作就是 null）；tokens = 整個任務用了多少 token。 */
    static synchronized void add(Context c, String text, String reply, JSONArray details, long tokens, double cost) {
        if (text == null || text.trim().isEmpty()) return;
        try {
            JSONArray a = load(c);
            JSONObject o = new JSONObject();
            o.put("id", System.currentTimeMillis());
            o.put("text", text.trim());
            o.put("reply", reply == null ? "" : reply);
            o.put("star", false);
            o.put("tokens", tokens);
            o.put("cost", cost);
            if (details != null && details.length() > 0) o.put("detail", details);
            a.put(o);
            save(c, trim(a));
        } catch (Exception ignored) {
            // 歷史紀錄寫不進去不影響任務本身
        }
    }

    private static JSONArray trim(JSONArray a) throws Exception {
        while (a.length() > MAX) {
            int victim = -1;
            for (int k = 0; k < a.length(); k++) {
                if (!a.getJSONObject(k).optBoolean("star")) { victim = k; break; }
            }
            if (victim < 0) break;   // 全部都加了星：不刪
            JSONArray rest = new JSONArray();
            for (int k = 0; k < a.length(); k++) if (k != victim) rest.put(a.get(k));
            a = rest;
        }
        return a;
    }

    /** 排好序的列表：有星的在前，各自依時間新到舊。 */
    static synchronized JSONArray list(Context c) {
        JSONArray a = load(c);
        JSONArray starred = new JSONArray(), rest = new JSONArray();
        try {
            for (int k = a.length() - 1; k >= 0; k--) {
                JSONObject o = a.getJSONObject(k);
                (o.optBoolean("star") ? starred : rest).put(o);
            }
            JSONArray out = new JSONArray();
            for (int k = 0; k < starred.length(); k++) out.put(starred.get(k));
            for (int k = 0; k < rest.length(); k++) out.put(rest.get(k));
            return out;
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    /** 取一筆任務（id 就是記錄時的時間戳）；找不到回 null。 */
    static synchronized JSONObject get(Context c, long id) {
        JSONArray a = load(c);
        for (int k = 0; k < a.length(); k++) {
            JSONObject o = a.optJSONObject(k);
            if (o != null && o.optLong("id") == id) return o;
        }
        return null;
    }

    /** 加星或取消星星（id 就是記錄時的時間戳）。 */
    static synchronized void setStar(Context c, long id, boolean on) {
        try {
            JSONArray a = load(c);
            for (int k = 0; k < a.length(); k++) {
                JSONObject o = a.getJSONObject(k);
                if (o.optLong("id") == id) o.put("star", on);
            }
            save(c, a);
        } catch (Exception ignored) {
            // 寫入失敗就維持原狀
        }
    }
}
