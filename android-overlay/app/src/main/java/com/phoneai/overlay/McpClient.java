package com.phoneai.overlay;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 最小的 MCP 用戶端（Streamable HTTP）：initialize → notifications/initialized → tools/list／tools/call。
 * 只連遠端 MCP 服務（網址＋Bearer 權杖）；回應可以是一般 JSON，也可以是 SSE（text/event-stream）。
 * 不傳送任何使用者資料以外的東西；權杖只放在請求標頭，不寫進紀錄。
 */
final class McpClient {
    static final String PROTOCOL = "2025-06-18";
    private static final int MAX_BODY = 2 * 1024 * 1024;

    private final String url, token;
    private String session;
    private int nextId = 1;

    McpClient(String url, String token) {
        this.url = url;
        this.token = token;
    }

    /**
     * 純邏輯（可單元測試）：從 HTTP 回應本文找出 id 對應的那則 JSON-RPC 回應。
     * contentType 含 text/event-stream 時，本文是一串 SSE 事件（data: 行，空行分隔）；否則是一則 JSON（或一個陣列）。
     * 回應是 error 就丟例外；找不到對應 id 也丟例外。
     */
    static JSONObject parseResponse(String contentType, String body, int id) throws Exception {
        JSONObject found = null;
        if (contentType != null && contentType.toLowerCase(java.util.Locale.ROOT).contains("text/event-stream")) {
            StringBuilder data = new StringBuilder();
            for (String line : (body + "\n\n").split("\r?\n", -1)) {
                if (line.startsWith("data:")) {
                    data.append(line.substring(5).trim());
                } else if (line.isEmpty() && data.length() > 0) {
                    JSONObject o = tryObj(data.toString());
                    data.setLength(0);
                    if (o != null && o.has("id") && o.optInt("id", -1) == id && (o.has("result") || o.has("error"))) { found = o; break; }
                }
            }
        } else {
            String t = body.trim();
            if (t.startsWith("[")) {
                JSONArray a = new JSONArray(t);
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.optJSONObject(i);
                    if (o != null && o.optInt("id", -1) == id) { found = o; break; }
                }
            } else {
                JSONObject o = tryObj(t);
                if (o != null && o.optInt("id", -1) == id) found = o;
            }
        }
        if (found == null) throw new Exception(L.t("MCP 沒有回應這個請求（id ") + id + "）");
        JSONObject err = found.optJSONObject("error");
        if (err != null) throw new Exception(L.t("MCP 錯誤：") + err.optString("message", err.toString()));
        JSONObject r = found.optJSONObject("result");
        return r == null ? new JSONObject() : r;
    }

    private static JSONObject tryObj(String s) {
        try { return new JSONObject(s); } catch (Exception e) { return null; }
    }

    private static final class Resp {
        int code;
        String type, session, body, wwwAuth;
    }

    /** 服務回 401：要登入（或權杖過期）。wwwAuth 是 WWW-Authenticate 標頭（OAuth 用來找授權伺服器），可能是 null。 */
    static final class AuthRequired extends Exception {
        final String wwwAuth;
        AuthRequired(String wwwAuth) {
            super(L.t("權杖被拒絕（HTTP 401）：請確認權杖有沒有過期、有沒有需要的權限"));
            this.wwwAuth = wwwAuth;
        }
    }

    private Resp post(JSONObject msg, boolean wantBody) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Accept", "application/json, text/event-stream");
        c.setRequestProperty("MCP-Protocol-Version", PROTOCOL);
        c.setRequestProperty("User-Agent", "Vertex");
        if (token != null && !token.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + token);
        if (session != null) c.setRequestProperty("Mcp-Session-Id", session);
        try (OutputStream o = c.getOutputStream()) { o.write(msg.toString().getBytes(StandardCharsets.UTF_8)); }
        Resp r = new Resp();
        r.code = c.getResponseCode();
        r.type = c.getContentType();
        r.wwwAuth = c.getHeaderField("WWW-Authenticate");
        String sid = c.getHeaderField("Mcp-Session-Id");
        if (sid != null && !sid.isEmpty()) r.session = sid;
        if (!wantBody) { c.disconnect(); return r; }
        InputStream in = r.code >= 400 ? c.getErrorStream() : c.getInputStream();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        if (in != null) {
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) {
                if (bo.size() + n > MAX_BODY) throw new Exception(L.t("MCP 回應太大"));
                bo.write(b, 0, n);
            }
            in.close();
        }
        r.body = bo.toString("UTF-8");
        return r;
    }

    private JSONObject rpc(String method, JSONObject params) throws Exception {
        int id = nextId++;
        JSONObject m = new JSONObject().put("jsonrpc", "2.0").put("id", id).put("method", method);
        if (params != null) m.put("params", params);
        Resp r = post(m, true);
        if (r.session != null) session = r.session;
        if (r.code == 401) throw new AuthRequired(r.wwwAuth);
        if (r.code == 403) throw new Exception(L.t("權杖被拒絕（HTTP 403）：請確認權杖有沒有過期、有沒有需要的權限"));
        if (r.code >= 400) throw new Exception(L.t("MCP 連線失敗（HTTP ") + r.code + "）");
        return parseResponse(r.type, r.body, id);
    }

    /** 連線握手（只需要做一次；之後用同一個 session）。 */
    void initialize() throws Exception {
        JSONObject p = new JSONObject().put("protocolVersion", PROTOCOL).put("capabilities", new JSONObject())
                .put("clientInfo", new JSONObject().put("name", "Vertex").put("version", "1"));
        rpc("initialize", p);
        post(new JSONObject().put("jsonrpc", "2.0").put("method", "notifications/initialized"), false);
    }

    /** 列出服務提供的所有工具（有分頁就一頁一頁取，最多 10 頁）。 */
    JSONArray listTools() throws Exception {
        JSONArray all = new JSONArray();
        String cursor = null;
        for (int page = 0; page < 10; page++) {
            JSONObject r = rpc("tools/list", cursor == null ? new JSONObject() : new JSONObject().put("cursor", cursor));
            JSONArray t = r.optJSONArray("tools");
            for (int i = 0; t != null && i < t.length(); i++) all.put(t.get(i));
            cursor = r.optString("nextCursor", "");
            if (cursor.isEmpty()) break;
        }
        return all;
    }

    /** 呼叫一個工具，把回傳的文字內容接起來（上限 maxChars 字）。工具自己回報失敗（isError）也會丟例外。 */
    String callTool(String name, JSONObject args, int maxChars) throws Exception {
        JSONObject r = rpc("tools/call", new JSONObject().put("name", name).put("arguments", args == null ? new JSONObject() : args));
        StringBuilder sb = new StringBuilder();
        JSONArray content = r.optJSONArray("content");
        for (int i = 0; content != null && i < content.length(); i++) {
            JSONObject c = content.optJSONObject(i);
            if (c != null && "text".equals(c.optString("type"))) sb.append(c.optString("text")).append('\n');
        }
        String out = sb.toString().trim();
        if (out.length() > maxChars) out = out.substring(0, maxChars) + "\n…（太長，已截斷）";
        if (r.optBoolean("isError")) throw new Exception(out.isEmpty() ? "工具回報失敗" : out);
        return out;
    }
}
