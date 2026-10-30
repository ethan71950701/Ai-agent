package com.phoneai.overlay;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * 巨集：任務成功做完之後，把每一步（點哪個「有名稱的元件」、輸入什麼、捲動…）記下來；下次同樣的任務直接照著重播，
 * 不用一步步問 AI，做完只讓 AI 看一眼確認結果。點擊記的是元件名稱不是座標，畫面改版或找不到就交回 AI；連續失敗兩次就刪掉。
 * 檔案在 App 私有資料夾的 macros.json。
 */
final class Macros {
    private Macros() { }

    static final int MAX_STEPS = 12, MAX_MACROS = 100;

    static boolean enabled(Context c) {
        return !AbTest.running && c.getSharedPreferences("cfg", Context.MODE_PRIVATE).getBoolean("macro_on", true);   // 比較測試時不重播巨集，不然沒有用到 AI
    }

    private static File file(Context c) { return new File(c.getFilesDir(), "macros.json"); }

    private static synchronized JSONObject load(Context c) {
        try {
            return new JSONObject(new String(Files.readAllBytes(file(c).toPath()), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static synchronized void save(Context c, JSONObject d) {
        try {
            File f = file(c), tmp = new File(f.getPath() + ".tmp");
            Files.write(tmp.toPath(), d.toString().getBytes(StandardCharsets.UTF_8));
            tmp.renameTo(f);
        } catch (Exception ignored) {
            // 記不下來只是少一個巨集，不影響任務
        }
    }

    /** 任務文字去掉空白標點之後當作名稱：同樣的話才會重播。 */
    static String key(String task) { return Router.norm(task); }

    static synchronized JSONArray get(Context c, String task) {
        JSONObject m = load(c).optJSONObject(key(task));
        return m == null ? null : m.optJSONArray("steps");
    }

    static synchronized void put(Context c, String task, JSONArray steps) {
        if (steps == null || steps.length() < 2 || steps.length() > MAX_STEPS) return;
        try {
            JSONObject d = load(c);
            String k = key(task);
            if (k.isEmpty() || (!d.has(k) && d.length() >= MAX_MACROS)) return;
            d.put(k, new JSONObject().put("task", task).put("steps", steps).put("uses", 0).put("fails", 0).put("created", System.currentTimeMillis()));
            save(c, d);
        } catch (Exception ignored) { }
    }

    static synchronized void ok(Context c, String task) {
        try {
            JSONObject d = load(c);
            JSONObject m = d.optJSONObject(key(task));
            if (m == null) return;
            m.put("uses", m.optInt("uses") + 1).put("fails", 0);
            save(c, d);
        } catch (Exception ignored) { }
    }

    /** 重播失敗：連續失敗兩次就刪掉（畫面已經改版，記的步驟沒用了）。 */
    static synchronized void fail(Context c, String task) {
        try {
            JSONObject d = load(c);
            JSONObject m = d.optJSONObject(key(task));
            if (m == null) return;
            int f = m.optInt("fails") + 1;
            if (f >= 2) d.remove(key(task)); else m.put("fails", f);
            save(c, d);
        } catch (Exception ignored) { }
    }

    static synchronized int count(Context c) { return load(c).length(); }

    /** 記住的任務清單：[鍵, 任務文字, 步數, 重播次數]，最常用的在前面。 */
    static synchronized List<String[]> list(Context c) {
        List<String[]> out = new java.util.ArrayList<>();
        JSONObject d = load(c);
        java.util.Iterator<String> it = d.keys();
        while (it.hasNext()) {
            String k = it.next();
            JSONObject m = d.optJSONObject(k);
            if (m == null) continue;
            JSONArray steps = m.optJSONArray("steps");
            out.add(new String[]{k, m.optString("task", k), String.valueOf(steps == null ? 0 : steps.length()), String.valueOf(m.optInt("uses"))});
        }
        out.sort((x, y) -> Integer.parseInt(y[3]) - Integer.parseInt(x[3]));
        return out;
    }

    /** 最常用的 n 個記住的任務文字（給路由比對「說法不同的同一件事」）。 */
    static List<String> topTasks(Context c, int n) {
        List<String[]> all = list(c);   // 已依重播次數排序
        List<String> out = new java.util.ArrayList<>();
        for (int i = 0; i < all.size() && i < n; i++) out.add(all.get(i)[1]);
        return out;
    }

    static synchronized void remove(Context c, String key) {
        JSONObject d = load(c);
        d.remove(key);
        save(c, d);
    }

    static synchronized void clear(Context c) { save(c, new JSONObject()); }

    // ---------- 純邏輯（單元測試用） ----------

    /** 把一個已執行的動作轉成巨集步驟；不能重播（沒有元件名稱、要看畫面內容…）回 null。tree = 當時的元件清單。 */
    static JSONObject stepFrom(JSONObject action, List<JSONObject> tree) {
        String a = action.optString("action");
        try {
            double wait = action.optDouble("wait", 1.2);
            switch (a) {
                case "tap": {
                    JSONObject t = tapPart(action, tree);
                    return t == null ? null : t.put("a", "tap").put("wait", wait);
                }
                case "batch": {
                    JSONArray subs = action.optJSONArray("actions");
                    if (subs == null || subs.length() == 0) return null;
                    JSONArray taps = new JSONArray();
                    for (int i = 0; i < subs.length(); i++) {
                        JSONObject sub = subs.optJSONObject(i);
                        if (sub == null || !"tap".equals(sub.optString("action"))) return null;
                        JSONObject t = tapPart(sub, tree);
                        if (t == null) return null;
                        taps.put(t);
                    }
                    return new JSONObject().put("a", "batch").put("taps", taps).put("wait", wait);
                }
                case "type":
                    return new JSONObject().put("a", "type").put("text", action.optString("text"))
                            .put("clear", action.optBoolean("clear", true)).put("enter", action.optBoolean("enter")).put("wait", wait);
                case "scroll":
                    return new JSONObject().put("a", "scroll").put("dir", action.optString("dir", "down")).put("wait", wait);
                case "back": case "home": case "enter": case "wait":
                    return new JSONObject().put("a", a).put("wait", wait);
                case "open":
                    return new JSONObject().put("a", "open").put("name", action.optString("name")).put("pkg", action.optString("pkg")).put("wait", wait);
                default:
                    return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 這次的做法乾不乾淨：沒有「返回」（返回＝走錯了退回來）、捲動不超過 2 次、不超過 8 步。
     * 繞了路的流程不記成巨集（重播只會重走冤枉路）；先靠經驗讓 AI 走對，走乾淨的那一次才升級成巨集。
     */
    static boolean isClean(List<JSONObject> steps) {
        if (steps == null || steps.size() > 8) return false;
        int scrolls = 0;
        for (JSONObject s : steps) {
            String a = s.optString("a");
            if ("back".equals(a)) return false;
            if ("scroll".equals(a)) scrolls++;
        }
        return scrolls <= 2;
    }

    /** 不乾淨的原因（給任務過程看）；乾淨回 null。 */
    static String uncleanWhy(List<JSONObject> steps) {
        if (steps == null) return null;
        if (steps.size() > 8) return "步驟太多（超過 8 步）";
        int scrolls = 0;
        for (JSONObject s : steps) {
            if ("back".equals(s.optString("a"))) return "中途有返回（走錯路退回來）";
            if ("scroll".equals(s.optString("a"))) scrolls++;
        }
        return scrolls > 2 ? "捲動太多次" : null;
    }

    /** 點擊要記得「點的是哪個有名稱的元件」；沒有名稱（純圖示、用座標點的）下次找不回來，回 null。 */
    private static JSONObject tapPart(JSONObject tap, List<JSONObject> tree) throws Exception {
        String label = tap.optString("label");
        if (label.isEmpty() || !tap.has("pick") || tree == null) return null;
        String kind = "";
        for (JSONObject o : tree) if (o.optInt("n") == tap.optInt("pick")) kind = o.optString("k");
        return new JSONObject().put("label", label).put("kind", kind).put("x", tap.optInt("x")).put("y", tap.optInt("y"));
    }

    /** 在目前的元件清單裡找回記住的元件：名稱相同（種類也相同優先），多個就取位置最近的；找不到回 null。 */
    static JSONObject find(List<JSONObject> els, String label, String kind, int x, int y) {
        String want = Router.norm(label);
        if (want.isEmpty() || els == null) return null;
        JSONObject best = null;
        long bestDist = Long.MAX_VALUE;
        for (int pass = 0; pass < 2 && best == null; pass++) {
            for (JSONObject o : els) {
                if (!Router.norm(o.optString("t")).equals(want)) continue;
                if (pass == 0 && !kind.equals(o.optString("k"))) continue;
                long dx = o.optInt("x") - x, dy = o.optInt("y") - y, dist = dx * dx + dy * dy;
                if (dist < bestDist) { bestDist = dist; best = o; }
            }
        }
        return best;
    }
}
