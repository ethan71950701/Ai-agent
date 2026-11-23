package com.phoneai.overlay;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * GitHub（MCP）：聊天助理多一批「唯讀」的 GitHub 工具（查 Release、編譯結果、提交、PR、Issue、檔案內容…）。
 * 工具清單是連上 GitHub 官方遠端 MCP 服務抓下來的，但只放行下面白名單裡的唯讀工具（會寫入、會觸發動作的一律不給 AI）。
 * 工具回傳的內容是外人寫的（Issue、PR 內文…），所以跟網頁一樣當成不可信內容處理（見 ChatEngine）。
 * 權杖（GitHub 個人存取權杖）存在 App 私有資料夾，權限 600，不進聊天、不印出來。
 */
final class McpTools {
    private McpTools() { }

    static final String PREFIX = "gh_";
    static final String URL = "https://api.githubcopilot.com/mcp/";
    static final String TOKEN_FILE = "github_mcp_token";
    private static final String CACHE_FILE = "github_mcp_tools.json";
    private static final int MAX_RESULT = 6000;

    /** 放行的唯讀工具（名稱跟 GitHub MCP 服務一致）。不在這裡的一律丟掉。 */
    static final Set<String> ALLOW = new HashSet<>(Arrays.asList(
            "get_file_contents", "list_branches", "list_commits", "get_commit", "list_tags", "get_tag",
            "list_releases", "get_latest_release", "get_release_by_tag",
            "list_pull_requests", "pull_request_read", "list_issues", "issue_read",
            "actions_list", "actions_get", "get_job_logs", "search_code", "search_repositories", "search_issues", "search_pull_requests"));

    /**
     * 純邏輯（可單元測試）：把服務給的工具清單轉成聊天助理用的格式——只留白名單內、而且沒有標示「會寫入」的；
     * 名稱加前綴 gh_；說明縮短；去掉 $schema。
     */
    static JSONArray convert(JSONArray mcpTools, String defaultRepo) {
        JSONArray out = new JSONArray();
        for (int i = 0; mcpTools != null && i < mcpTools.length(); i++) {
            JSONObject t = mcpTools.optJSONObject(i);
            if (t == null) continue;
            String name = t.optString("name");
            if (!ALLOW.contains(name)) continue;
            JSONObject ann = t.optJSONObject("annotations");
            if (ann != null && ann.has("readOnlyHint") && !ann.optBoolean("readOnlyHint", true)) continue;   // 服務自己標示會寫入：不放行
            try {
                String d = t.optString("description", "").replace('\n', ' ');
                if (d.length() > 160) d = d.substring(0, 160) + "…";
                JSONObject schema = t.optJSONObject("inputSchema");
                schema = schema == null ? new JSONObject().put("type", "object") : new JSONObject(schema.toString());
                schema.remove("$schema");
                out.put(new JSONObject().put("name", PREFIX + name)
                        .put("description", "[GitHub 唯讀] " + d + (defaultRepo == null || defaultRepo.isEmpty() ? "" : "（使用者的預設 repo：" + defaultRepo + "）"))
                        .put("input_schema", schema));
            } catch (Exception ignored) { }
        }
        return out;
    }

    static String token(Context c) {
        try {
            return new String(java.nio.file.Files.readAllBytes(new File(c.getFilesDir(), TOKEN_FILE).toPath()), StandardCharsets.UTF_8).trim();
        } catch (Exception e) { return ""; }
    }

    static boolean hasToken(Context c) { return !token(c).isEmpty(); }

    private static JSONArray cached(Context c) {
        try {
            File f = new File(c.getFilesDir(), CACHE_FILE);
            if (!f.exists()) return new JSONArray();
            return new JSONArray(new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        } catch (Exception e) { return new JSONArray(); }
    }

    /** 這個工具名稱是不是 GitHub MCP 的（而且上次連線時有抓到）。 */
    static boolean handles(String name) {
        if (name == null || !name.startsWith(PREFIX) || App.ctx == null) return false;
        JSONArray a = cached(App.ctx);
        for (int i = 0; i < a.length(); i++) if (name.equals(a.optJSONObject(i).optString("name"))) return true;
        return false;
    }

    /** 把 GitHub 工具加進聊天助理的工具清單（沒設權杖或還沒抓過就什麼都不加）。 */
    static void append(JSONArray tools) {
        if (App.ctx == null || !hasToken(App.ctx)) return;
        JSONArray a = cached(App.ctx);
        for (int i = 0; i < a.length(); i++) tools.put(a.opt(i));
    }

    private static McpClient shared;
    private static long sharedAt;

    private static synchronized McpClient client(Context c, boolean fresh) throws Exception {
        long now = System.currentTimeMillis();
        if (fresh || shared == null || now - sharedAt > 10 * 60_000L) {   // 連線太久沒用就重新握手
            McpClient k = new McpClient(URL, token(c));
            k.initialize();
            shared = k;
        }
        sharedAt = now;
        return shared;
    }

    /** 執行一個 GitHub 工具（在背景執行緒呼叫）。失敗回一段說明文字，不丟例外。 */
    static String run(Context c, String name, JSONObject args) {
        String real = name.substring(PREFIX.length());
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return client(c, attempt > 0).callTool(real, args, MAX_RESULT);
            } catch (Exception e) {
                if (attempt == 1) return "GitHub 查詢失敗：" + e.getMessage();
            }
        }
        return "GitHub 查詢失敗";
    }

    /** 連線、抓工具清單、存起來。回傳給人看的結果文字。 */
    static String refresh(Context c) {
        try {
            McpClient k = client(c, true);
            JSONArray conv = convert(k.listTools(), BuildConfig.REPO);
            File f = new File(c.getFilesDir(), CACHE_FILE);
            java.nio.file.Files.write(f.toPath(), conv.toString().getBytes(StandardCharsets.UTF_8));
            return conv.length() == 0 ? L.t("連上了，但沒有找到可用的唯讀工具") : String.format(L.t("連上了，可用的唯讀工具 %d 個"), conv.length());
        } catch (Exception e) {
            return L.t("連線失敗：") + e.getMessage();
        }
    }

    static void clear(Context c) {
        new File(c.getFilesDir(), TOKEN_FILE).delete();
        new File(c.getFilesDir(), CACHE_FILE).delete();
        synchronized (McpTools.class) { shared = null; }
    }

    static int toolCount(Context c) { return cached(c).length(); }
}
