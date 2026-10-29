package com.phoneai.overlay;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * 助理的記憶（待辦與筆記）：檔案在 App 自己的資料夾（files/memory.json），格式與舊版的 memory.json 相同。
 * 聊天模型透過 runTool 操作它；安全規則照搬 scripts/memory.py：永久記憶不能改刪、刪除是標記、
 * 一次刪超過 3 項或清空都要使用者按確認。
 */
final class Memory {
    interface Confirm { boolean ask(String msg); }

    private Memory() { }

    static File file(Context c) { return new File(c.getFilesDir(), "memory.json"); }

    static synchronized JSONObject load(Context c) {
        try {
            JSONObject d = new JSONObject(new String(Files.readAllBytes(file(c).toPath()), StandardCharsets.UTF_8));
            if (d.optJSONArray("items") != null) {
                if (!d.has("next_id")) d.put("next_id", nextId(d));
                return d;
            }
        } catch (Exception ignored) { }
        try { return new JSONObject().put("next_id", 1).put("items", new JSONArray()); } catch (Exception e) { throw new RuntimeException(e); }
    }

    static synchronized void save(Context c, JSONObject d) throws Exception {
        File f = file(c), tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) { out.write(d.toString(1).getBytes(StandardCharsets.UTF_8)); }
        if (!tmp.renameTo(f)) throw new Exception("寫入記憶失敗");
    }

    private static int nextId(JSONObject d) {
        int max = 0;
        JSONArray a = d.optJSONArray("items");
        for (int i = 0; a != null && i < a.length(); i++) max = Math.max(max, a.optJSONObject(i).optInt("id"));
        return max + 1;
    }

    static List<JSONObject> items(JSONObject d) {
        List<JSONObject> out = new ArrayList<>();
        JSONArray a = d.optJSONArray("items");
        for (int i = 0; a != null && i < a.length(); i++) out.add(a.optJSONObject(i));
        return out;
    }

    /** 還沒完成、沒刪除的項目（照建立順序）。 */
    static List<JSONObject> live(JSONObject d, String kind) {
        List<JSONObject> out = new ArrayList<>();
        for (JSONObject it : items(d))
            if (kind.equals(it.optString("kind")) && !it.optBoolean("done") && !it.optBoolean("deleted")) out.add(it);
        return out;
    }

    /** 記下一則經驗（沒有期限，重複的不再存）。app = 經驗適用的 App 套件，null = 通用。回傳 true = 新增。 */
    static synchronized boolean addLesson(Context c, String text, String app) throws Exception {
        text = text == null ? "" : text.trim();
        if (text.isEmpty()) return false;
        if (text.length() > 200) text = text.substring(0, 200);
        JSONObject d = load(c);
        for (JSONObject it : items(d))
            if ("lesson".equals(it.optString("kind")) && !it.optBoolean("deleted") && text.equals(it.optString("text"))
                    && (app == null ? !it.has("app") : app.equals(it.optString("app")))) return false;
        JSONObject it = new JSONObject().put("id", d.optInt("next_id", 1)).put("kind", "lesson").put("text", text)
                .put("created", now()).put("done", false).put("done_at", JSONObject.NULL)
                .put("deleted", false).put("pinned", false);
        if (app != null) it.put("app", app);
        d.getJSONArray("items").put(it);
        d.put("next_id", it.getInt("id") + 1);
        save(c, d);
        return true;
    }

    /** 內建經驗：只對指定的 App 才會帶進提示；只種一次（用 cfg 旗標記），使用者刪掉就不會再冒出來。 */
    static void seedLessons(Context c) {
        android.content.SharedPreferences sp = c.getSharedPreferences("cfg", Context.MODE_PRIVATE);
        if (sp.getBoolean("seed_lessons_v1", false)) return;
        String calc = "計算機：按鍵上方的小膠囊（例如 123、45）是剪貼簿的建議，不是已輸入的內容；要確認輸入或結果，看最上方的大顯示區。數字和運算鍵一個一個點，用 batch 一次連點，最後點 =。";
        try {
            for (String pkg : new String[]{"com.sec.android.app.popupcalculator", "com.google.android.calculator", "com.android.calculator2"})
                addLesson(c, calc, pkg);
            sp.edit().putBoolean("seed_lessons_v1", true).apply();
        } catch (Exception ignored) { }
    }

    /** 開始任務前要帶進提示的經驗：通用的 6 則（依跟任務文字的相關程度挑，不夠相關的用最新的補）+ 這些 App 的最新 8 則。 */
    static synchronized List<String> lessonsFor(Context c, List<String> apps, String task) {
        JSONObject d = load(c);
        List<String> gen = new ArrayList<>(), mine = new ArrayList<>();
        for (JSONObject it : items(d)) {
            if (!"lesson".equals(it.optString("kind")) || it.optBoolean("deleted")) continue;
            if (!it.has("app")) gen.add(it.optString("text"));
            else if (apps.contains(it.optString("app"))) mine.add(it.optString("text"));
        }
        List<String> out = pickRelevant(gen, task, 6);
        out.addAll(mine.subList(Math.max(0, mine.size() - 8), mine.size()));
        return out;
    }

    /** 從 all（舊到新）挑 n 則：跟任務文字重疊的詞越多越優先，同分的新的優先；回傳順序是舊到新。 */
    private static List<String> pickRelevant(List<String> all, String task, int n) {
        if (all.size() <= n) return new ArrayList<>(all);
        final java.util.Set<String> tb = bigrams(task);
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) idx.add(i);
        idx.sort((a, b) -> {
            int sa = overlap(tb, all.get(a)), sb = overlap(tb, all.get(b));
            return sa != sb ? sb - sa : b - a;
        });
        List<Integer> chosen = new ArrayList<>(idx.subList(0, n));
        java.util.Collections.sort(chosen);
        List<String> out = new ArrayList<>();
        for (int i : chosen) out.add(all.get(i));
        return out;
    }

    /** 兩個字一組的片段（中文沒有空格，用相鄰兩字當作「詞」比較）。 */
    private static java.util.Set<String> bigrams(String s) {
        java.util.Set<String> out = new java.util.HashSet<>();
        String n = s == null ? "" : s.toLowerCase(java.util.Locale.ROOT).replaceAll("[\\s\\p{Punct}，。、；：「」（）]", "");
        for (int i = 0; i + 1 < n.length(); i++) out.add(n.substring(i, i + 2));
        return out;
    }

    private static int overlap(java.util.Set<String> taskBigrams, String lesson) {
        int k = 0;
        for (String g : bigrams(lesson)) if (taskBigrams.contains(g)) k++;
        return k;
    }

    private static String now() { return new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm", java.util.Locale.US).format(new java.util.Date()); }

    private static JSONObject byId(JSONObject d, int id) {
        for (JSONObject it : items(d)) if (it.optInt("id") == id && !it.optBoolean("deleted")) return it;
        return null;
    }

    /** 給聊天提示用：「#編號 📌文字」列表。 */
    static String promptRows(JSONObject d, String kind) {
        StringBuilder sb = new StringBuilder();
        for (JSONObject it : live(d, kind)) {
            if (sb.length() > 0) sb.append("；");
            String t = it.optString("text");
            sb.append("#").append(it.optInt("id")).append(" ").append(it.optBoolean("pinned") ? "📌" : "")
                    .append(t.length() > 100 ? t.substring(0, 100) : t);
        }
        return sb.length() == 0 ? "（沒有）" : sb.toString();
    }

    /** 聊天模型用的記憶工具。回傳給模型看的結果字串（錯誤也用字串回）。 */
    static String runTool(Context c, String name, JSONObject args, Confirm confirm) {
        try {
            JSONObject d = load(c);
            switch (name) {
                case "memory_add": {
                    String kind = args.optString("kind"), text = args.optString("text").trim();
                    if (!(kind.equals("todo") || kind.equals("note")) || text.isEmpty()) return "錯誤：kind 要是 todo 或 note，text 不能空";
                    if (text.length() > 300) text = text.substring(0, 300);
                    boolean pinned = args.optBoolean("permanent", false);
                    JSONObject it = new JSONObject().put("id", d.optInt("next_id", 1)).put("kind", kind).put("text", text)
                            .put("created", now()).put("done", false).put("done_at", JSONObject.NULL)
                            .put("deleted", false).put("pinned", pinned);
                    d.getJSONArray("items").put(it);
                    d.put("next_id", it.getInt("id") + 1);
                    save(c, d);
                    return "已新增 #" + it.getInt("id") + "（" + kind + (pinned ? "，永久" : "") + "）：" + text;
                }
                case "memory_update": {
                    JSONObject it = byId(d, args.optInt("id", -1));
                    if (it == null) return "錯誤：找不到 #" + args.optInt("id");
                    if (it.optBoolean("pinned")) return "錯誤：#" + it.optInt("id") + " 這是永久記憶，只能在 App 的「助理的記憶」頁手動刪除或修改";
                    String text = args.optString("text").trim();
                    if (!text.isEmpty()) it.put("text", text.length() > 300 ? text.substring(0, 300) : text);
                    if (args.has("done") && it.optString("kind").equals("todo")) {
                        boolean done = args.optBoolean("done");
                        it.put("done", done);
                        it.put("done_at", done ? now() : JSONObject.NULL);
                    }
                    save(c, d);
                    return "已更新 #" + it.optInt("id") + "：" + it.optString("text") + (it.optBoolean("done") ? "（已完成）" : "");
                }
                case "memory_delete": {
                    List<JSONObject> hit = new ArrayList<>(), locked = new ArrayList<>();
                    JSONArray ids = args.optJSONArray("ids");
                    for (int i = 0; ids != null && i < ids.length(); i++) {
                        JSONObject it = byId(d, ids.optInt(i, -1));
                        if (it == null) continue;
                        if (it.optBoolean("pinned")) locked.add(it); else hit.add(it);
                    }
                    if (hit.isEmpty()) return locked.isEmpty() ? "錯誤：找不到這些 id" : "錯誤：永久記憶只能在 App 的「助理的記憶」頁手動刪除";
                    if (hit.size() > 3) {
                        StringBuilder m = new StringBuilder(L.t("要刪除這 ") + hit.size() + L.t(" 項嗎？\n"));
                        for (int i = 0; i < hit.size() && i < 8; i++) m.append("・").append(hit.get(i).optString("text")).append('\n');
                        if (confirm == null || !confirm.ask(m.toString())) return "使用者沒有同意，沒有刪除";
                    }
                    StringBuilder names = new StringBuilder();
                    for (JSONObject it : hit) { it.put("deleted", true); names.append(names.length() > 0 ? "、" : "").append(it.optString("text")); }
                    save(c, d);
                    return "已刪除 " + hit.size() + " 項：" + names + (locked.isEmpty() ? "" : "（永久記憶沒有刪）");
                }
                case "memory_clear": {
                    String scope = args.optString("scope");
                    if (!scope.equals("todo") && !scope.equals("note")) scope = "all";
                    int n = 0;
                    for (JSONObject it : items(d))
                        if (!it.optBoolean("deleted") && !it.optBoolean("pinned") && (scope.equals("all") || scope.equals(it.optString("kind")))) n++;
                    if (confirm == null || !confirm.ask(L.t("要刪除") + (scope.equals("all") ? L.t("全部") : scope.equals("todo") ? L.t("全部待辦") : L.t("全部筆記"))
                            + L.t("（") + n + L.t(" 項，永久記憶不會刪）嗎？"))) return "使用者沒有同意，沒有刪除";
                    for (JSONObject it : items(d))
                        if (!it.optBoolean("pinned") && (scope.equals("all") || scope.equals(it.optString("kind")))) it.put("deleted", true);
                    save(c, d);
                    return "已刪除 " + n + " 項";
                }
                case "memory_list": {
                    boolean includeDone = args.optBoolean("include_done", false);
                    StringBuilder sb = new StringBuilder();
                    for (JSONObject it : items(d)) {
                        if (it.optBoolean("deleted") || (!includeDone && it.optBoolean("done"))) continue;
                        sb.append("#").append(it.optInt("id")).append(" ").append(it.optString("kind"))
                                .append(it.optBoolean("done") ? "✓" : "").append(" ").append(it.optString("text"))
                                .append("（").append(it.optString("created").length() >= 10 ? it.optString("created").substring(0, 10) : "").append("）\n");
                    }
                    return sb.length() == 0 ? "（空的）" : sb.toString().trim();
                }
                default:
                    return "錯誤：沒有這個工具 " + name;
            }
        } catch (Exception e) {
            return "錯誤：" + e.getMessage();
        }
    }
}
