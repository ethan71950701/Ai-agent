package com.phoneai.overlay;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 服務的 OAuth 登入（照 MCP 授權規格：OAuth 2.1 授權碼＋PKCE）。
 * 步驟：服務回 401 → 從 WWW-Authenticate 或 /.well-known/oauth-protected-resource 找到授權伺服器 →
 * 讀授權伺服器的設定（/.well-known/oauth-authorization-server 或 openid-configuration）→ 動態註冊這個 App（拿 client_id）→
 * 開瀏覽器讓使用者登入、按同意 → 瀏覽器跳回本機 127.0.0.1 的小伺服器帶回授權碼 → 換成存取權杖（與更新權杖）。
 * 權杖只存在 App 私有資料夾（權限 600），不寫進紀錄、不給 AI。
 */
final class McpOAuth {
    private McpOAuth() { }

    static final long LOGIN_TIMEOUT_MS = 5 * 60_000L;

    /** 一個服務登入後要記住的東西。 */
    static final class Session {
        String clientId = "", clientSecret = "", tokenEndpoint = "", resource = "", accessToken = "", refreshToken = "";
        long expiresAt;   // 0 = 不知道（服務沒說）

        JSONObject toJson() {
            try {
                return new JSONObject().put("client_id", clientId).put("client_secret", clientSecret).put("token_endpoint", tokenEndpoint)
                        .put("resource", resource).put("access_token", accessToken).put("refresh_token", refreshToken).put("expires_at", expiresAt);
            } catch (Exception e) { return new JSONObject(); }
        }

        static Session fromJson(JSONObject j) {
            Session s = new Session();
            if (j == null) return s;
            s.clientId = j.optString("client_id");
            s.clientSecret = j.optString("client_secret");
            s.tokenEndpoint = j.optString("token_endpoint");
            s.resource = j.optString("resource");
            s.accessToken = j.optString("access_token");
            s.refreshToken = j.optString("refresh_token");
            s.expiresAt = j.optLong("expires_at", 0);
            return s;
        }

        /** 快過期了（剩不到 1 分鐘）而且有更新權杖可以換新的。 */
        boolean needsRefresh(long now) { return expiresAt > 0 && now > expiresAt - 60_000L && !refreshToken.isEmpty(); }
    }

    // ---------- 純邏輯（可單元測試） ----------

    /** 從 WWW-Authenticate 標頭取出某個參數（例如 resource_metadata、scope）；沒有回 null。 */
    static String authParam(String wwwAuth, String name) {
        if (wwwAuth == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)(?:^|[\\s,])" + java.util.regex.Pattern.quote(name) + "\\s*=\\s*(?:\"([^\"]*)\"|([^\\s,]+))").matcher(wwwAuth);
        if (!m.find()) return null;
        return m.group(1) != null ? m.group(1) : m.group(2);
    }

    /** 受保護資源設定檔的候選網址：先試帶路徑的，再試根目錄的。 */
    static List<String> resourceMetadataUrls(String mcpUrl) {
        List<String> out = new ArrayList<>();
        URI u = URI.create(mcpUrl);
        String origin = u.getScheme() + "://" + u.getRawAuthority();
        String path = u.getRawPath() == null ? "" : u.getRawPath();
        while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        if (!path.isEmpty()) out.add(origin + "/.well-known/oauth-protected-resource" + path);
        out.add(origin + "/.well-known/oauth-protected-resource");
        return out;
    }

    /** 授權伺服器設定檔的候選網址（RFC 8414 與 OpenID Connect 兩種，帶路徑與不帶路徑）。 */
    static List<String> authServerMetadataUrls(String issuer) {
        List<String> out = new ArrayList<>();
        URI u = URI.create(issuer);
        String origin = u.getScheme() + "://" + u.getRawAuthority();
        String path = u.getRawPath() == null ? "" : u.getRawPath();
        while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        if (!path.isEmpty()) {
            out.add(origin + "/.well-known/oauth-authorization-server" + path);
            out.add(origin + "/.well-known/openid-configuration" + path);
            out.add(origin + path + "/.well-known/openid-configuration");
        } else {
            out.add(origin + "/.well-known/oauth-authorization-server");
            out.add(origin + "/.well-known/openid-configuration");
        }
        return out;
    }

    /** PKCE：code_verifier 的 SHA-256，再做 base64url（不補等號）。 */
    static String pkceChallenge(String verifier) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(d);
    }

    static String randomToken(int bytes) {
        byte[] b = new byte[bytes];
        new SecureRandom().nextBytes(b);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    static String form(Map<String, String> m) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : m.entrySet()) {
            if (e.getValue() == null || e.getValue().isEmpty()) continue;
            if (sb.length() > 0) sb.append('&');
            sb.append(enc(e.getKey())).append('=').append(enc(e.getValue()));
        }
        return sb.toString();
    }

    /** 組出要在瀏覽器打開的授權網址。 */
    static String authorizeUrl(String endpoint, String clientId, String redirect, String challenge, String state, String resource, String scope) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("response_type", "code");
        m.put("client_id", clientId);
        m.put("redirect_uri", redirect);
        m.put("code_challenge", challenge);
        m.put("code_challenge_method", "S256");
        m.put("state", state);
        m.put("resource", resource);
        m.put("scope", scope);
        if (isGoogle(endpoint)) {   // Google：不認 resource；要 offline＋consent 才會給更新權杖（不然大約一小時就要重新登入）
            m.remove("resource");
            m.put("access_type", "offline");
            m.put("prompt", "consent");
        }
        return endpoint + (endpoint.contains("?") ? "&" : "?") + form(m);
    }

    /** 拆網址的查詢字串（a=1&b=2）。 */
    static Map<String, String> parseQuery(String q) {
        Map<String, String> out = new LinkedHashMap<>();
        if (q == null) return out;
        for (String part : q.split("&")) {
            if (part.isEmpty()) continue;
            int i = part.indexOf('=');
            try {
                String k = URLDecoder.decode(i < 0 ? part : part.substring(0, i), "UTF-8");
                String v = i < 0 ? "" : URLDecoder.decode(part.substring(i + 1), "UTF-8");
                out.put(k, v);
            } catch (Exception ignored) { }
        }
        return out;
    }

    /** 權杖回應（JSON）寫進 session；沒有 access_token 就丟例外。更新時服務沒給新的更新權杖，就保留舊的。 */
    static void applyToken(Session s, JSONObject j, long now) throws Exception {
        String at = j.optString("access_token", "");
        if (at.isEmpty()) throw new Exception("登入失敗：" + j.optString("error_description", j.optString("error", "沒有拿到權杖")));
        s.accessToken = at;
        String rt = j.optString("refresh_token", "");
        if (!rt.isEmpty()) s.refreshToken = rt;
        long exp = j.optLong("expires_in", 0);
        s.expiresAt = exp > 0 ? now + exp * 1000L : 0;
    }

    // ---------- 網路 ----------

    private static final class Http {
        int code;
        String body = "";
    }

    private static Http http(String method, String url, String contentType, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestMethod(method);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "Vertex");
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", contentType);
            try (OutputStream o = c.getOutputStream()) { o.write(body.getBytes(StandardCharsets.UTF_8)); }
        }
        Http r = new Http();
        r.code = c.getResponseCode();
        InputStream in = r.code >= 400 ? c.getErrorStream() : c.getInputStream();
        if (in != null) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0 && bo.size() < 512 * 1024) bo.write(b, 0, n);
            in.close();
            r.body = bo.toString("UTF-8");
        }
        c.disconnect();
        return r;
    }

    private static JSONObject getJson(List<String> urls) {
        for (String u : urls) {
            try {
                Http r = http("GET", u, null, null);
                if (r.code == 200) return new JSONObject(r.body);
            } catch (Exception ignored) { }
        }
        return null;
    }

    /** 使用者自己填的 App 資料（服務不支援自動註冊時用，例如 Google）；都可以是空的。 */
    static final class Manual {
        String clientId = "", clientSecret = "", scope = "";
    }

    /** 是不是 Google 的登入（Google 不認 resource 參數；要 access_type=offline 才給更新權杖）。 */
    static boolean isGoogle(String url) {
        try {
            String h = URI.create(url).getHost();
            return h != null && (h.equals("accounts.google.com") || h.equals("oauth2.googleapis.com") || h.endsWith(".googleapis.com"));
        } catch (Exception e) { return false; }
    }

    /** Google Workspace MCP 服務預設要的權限（照 Google 官方文件）。不是 Google 的回 null。 */
    static String googleScopes(String mcpUrl) {
        String h;
        try { h = URI.create(mcpUrl).getHost(); } catch (Exception e) { return null; }
        if (h == null || !h.endsWith(".googleapis.com")) return null;
        String g = "https://www.googleapis.com/auth/";
        String drive = g + "drive.readonly " + g + "drive.file";
        if (h.startsWith("gmailmcp.")) return g + "gmail.readonly " + g + "gmail.compose " + g + "gmail.modify";
        if (h.startsWith("calendarmcp.")) return g + "calendar.calendarlist.readonly " + g + "calendar.events.freebusy " + g + "calendar.events.readonly " + g + "calendar.events";
        if (h.startsWith("drivemcp.")) return drive;
        if (h.startsWith("docsmcp.")) return drive + " " + g + "documents.readonly " + g + "documents";
        if (h.startsWith("sheetsmcp.")) return drive + " " + g + "spreadsheets.readonly " + g + "spreadsheets";
        if (h.startsWith("slidesmcp.")) return drive + " " + g + "presentations.readonly " + g + "presentations";
        if (h.startsWith("people.")) return g + "directory.readonly " + g + "userinfo.profile " + g + "contacts.readonly";
        return null;
    }

    interface Ui {
        void progress(String msg);
        /** 開瀏覽器到這個網址。 */
        void openBrowser(String url);
        boolean cancelled();
    }

    /**
     * 完整登入（在背景執行緒呼叫，會等使用者在瀏覽器登入，最多 5 分鐘）。
     * wwwAuth：服務回 401 時的 WWW-Authenticate 標頭（可能是 null）。成功回 session；失敗丟例外（訊息可以直接給使用者看）。
     */
    static Session login(String mcpUrl, String wwwAuth, Ui ui) throws Exception { return login(mcpUrl, wwwAuth, new Manual(), ui); }

    static Session login(String mcpUrl, String wwwAuth, Manual manual, Ui ui) throws Exception {
        if (manual == null) manual = new Manual();
        ui.progress(L.t("找登入方式…"));
        String prmUrl = authParam(wwwAuth, "resource_metadata");
        List<String> prmCands = new ArrayList<>();
        if (prmUrl != null) prmCands.add(prmUrl);
        prmCands.addAll(resourceMetadataUrls(mcpUrl));
        JSONObject prm = getJson(prmCands);
        String resource = prm != null && !prm.optString("resource").isEmpty() ? prm.optString("resource") : mcpUrl;
        String issuer;
        JSONArray as = prm == null ? null : prm.optJSONArray("authorization_servers");
        if (as != null && as.length() > 0) issuer = as.optString(0);
        else if (googleScopes(mcpUrl) != null || mcpUrl.contains(".googleapis.com/")) issuer = "https://accounts.google.com";   // Google 的服務：登入一律在 accounts.google.com
        else { URI u = URI.create(mcpUrl); issuer = u.getScheme() + "://" + u.getRawAuthority(); }   // 舊版服務：授權伺服器就在同一個網域
        JSONObject meta = getJson(authServerMetadataUrls(issuer));
        if (meta == null) throw new Exception(L.t("這個服務沒有提供 OAuth 登入資訊；請改用權杖"));
        String authz = meta.optString("authorization_endpoint"), tokenEp = meta.optString("token_endpoint"), reg = meta.optString("registration_endpoint");
        if (authz.isEmpty() || tokenEp.isEmpty()) throw new Exception(L.t("這個服務的登入資訊不完整；請改用權杖"));
        String scope = !manual.scope.trim().isEmpty() ? manual.scope.trim() : authParam(wwwAuth, "scope");
        if (scope == null) scope = googleScopes(mcpUrl);
        if (scope == null && prm != null && prm.optJSONArray("scopes_supported") != null) {
            JSONArray sc = prm.optJSONArray("scopes_supported");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < sc.length(); i++) sb.append(i > 0 ? " " : "").append(sc.optString(i));
            scope = sb.toString();
        }

        try (ServerSocket server = new ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))) {
            String redirect = "http://127.0.0.1:" + server.getLocalPort() + "/callback";
            Session s = new Session();
            s.tokenEndpoint = tokenEp;
            s.resource = resource;
            if (!manual.clientId.trim().isEmpty()) {   // 使用者自己建的 App（Google 等）：不用註冊
                s.clientId = manual.clientId.trim();
                s.clientSecret = manual.clientSecret.trim();
            } else {
                if (reg.isEmpty()) throw new Exception(L.t("這個服務不支援自動註冊 App（沒有 registration_endpoint）；請填 Client ID，或改用權杖"));
                ui.progress(L.t("向服務註冊這個 App…"));
                JSONObject regBody = new JSONObject().put("client_name", "Vertex").put("redirect_uris", new JSONArray().put(redirect))
                        .put("grant_types", new JSONArray().put("authorization_code").put("refresh_token"))
                        .put("response_types", new JSONArray().put("code")).put("token_endpoint_auth_method", "none");
                Http rr = http("POST", reg, "application/json", regBody.toString());
                if (rr.code >= 400) throw new Exception(L.t("註冊 App 失敗（HTTP ") + rr.code + "）");
                JSONObject rj = new JSONObject(rr.body);
                s.clientId = rj.optString("client_id");
                s.clientSecret = rj.optString("client_secret");
                if (s.clientId.isEmpty()) throw new Exception(L.t("註冊 App 失敗：沒有拿到 client_id"));
            }

            String verifier = randomToken(48), state = randomToken(24);
            ui.progress(L.t("請在瀏覽器登入並按同意，完成後回到這裡"));
            ui.openBrowser(authorizeUrl(authz, s.clientId, redirect, pkceChallenge(verifier), state, resource, scope));
            Map<String, String> q = waitCallback(server, state, ui);
            if (q.containsKey("error")) throw new Exception(L.t("登入被拒絕或取消：") + q.get("error") + (q.containsKey("error_description") ? "（" + q.get("error_description") + "）" : ""));
            String code = q.get("code");
            if (code == null || code.isEmpty()) throw new Exception(L.t("沒有拿到授權碼"));

            ui.progress(L.t("換取權杖…"));
            Map<String, String> f = new LinkedHashMap<>();
            f.put("grant_type", "authorization_code");
            f.put("code", code);
            f.put("redirect_uri", redirect);
            f.put("client_id", s.clientId);
            f.put("client_secret", s.clientSecret);
            f.put("code_verifier", verifier);
            if (!isGoogle(tokenEp)) f.put("resource", resource);
            Http tr = http("POST", tokenEp, "application/x-www-form-urlencoded", form(f));
            JSONObject tj;
            try { tj = new JSONObject(tr.body); } catch (Exception e) { throw new Exception(L.t("換取權杖失敗（HTTP ") + tr.code + "）"); }
            if (tr.code >= 400 && tj.optString("access_token").isEmpty()) throw new Exception(L.t("換取權杖失敗：") + tj.optString("error_description", tj.optString("error", "HTTP " + tr.code)));
            applyToken(s, tj, System.currentTimeMillis());
            return s;
        }
    }

    /** 等瀏覽器跳回 127.0.0.1：回一個「完成」頁面，檢查 state，回傳查詢參數。 */
    private static Map<String, String> waitCallback(ServerSocket server, String state, Ui ui) throws Exception {
        server.setSoTimeout(1000);
        long until = System.currentTimeMillis() + LOGIN_TIMEOUT_MS;
        while (System.currentTimeMillis() < until) {
            if (ui.cancelled()) throw new Exception(L.t("已取消登入"));
            Socket sock;
            try { sock = server.accept(); } catch (java.net.SocketTimeoutException e) { continue; }
            try (Socket so = sock) {
                so.setSoTimeout(5000);
                InputStream in = so.getInputStream();
                StringBuilder line = new StringBuilder();
                int ch;
                while ((ch = in.read()) >= 0 && ch != '\n' && line.length() < 8192) line.append((char) ch);
                String[] parts = line.toString().trim().split(" ");
                String target = parts.length >= 2 ? parts[1] : "";
                if (!target.startsWith("/callback")) { respond(so, 404, "Not found"); continue; }
                int qi = target.indexOf('?');
                Map<String, String> q = parseQuery(qi < 0 ? "" : target.substring(qi + 1));
                if (!state.equals(q.get("state"))) { respond(so, 400, L.t("登入驗證碼不符，請回到 App 重試")); continue; }   // 不是這次登入發出的：不收
                respond(so, 200, q.containsKey("error") ? L.t("登入沒有完成，請回到 Vertex 看說明。") : L.t("登入完成，可以回到 Vertex 了。"));
                return q;
            }
        }
        throw new Exception(L.t("等太久了（5 分鐘），沒有完成登入"));
    }

    private static void respond(Socket so, int code, String msg) {
        try {
            String html = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width\"><title>Vertex</title></head>"
                    + "<body style=\"font-family:sans-serif;padding:32px;font-size:20px\">" + msg.replace("<", "&lt;") + "</body></html>";
            byte[] b = html.getBytes(StandardCharsets.UTF_8);
            String head = "HTTP/1.1 " + code + " OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + b.length + "\r\nConnection: close\r\n\r\n";
            OutputStream o = so.getOutputStream();
            o.write(head.getBytes(StandardCharsets.US_ASCII));
            o.write(b);
            o.flush();
        } catch (Exception ignored) { }
    }

    /** 用更新權杖換新的存取權杖（成功會直接改 s）。 */
    static void refresh(Session s) throws Exception {
        if (s.refreshToken.isEmpty() || s.tokenEndpoint.isEmpty()) throw new Exception(L.t("登入已過期，請重新登入"));
        Map<String, String> f = new LinkedHashMap<>();
        f.put("grant_type", "refresh_token");
        f.put("refresh_token", s.refreshToken);
        f.put("client_id", s.clientId);
        f.put("client_secret", s.clientSecret);
        if (!isGoogle(s.tokenEndpoint)) f.put("resource", s.resource);
        Http r = http("POST", s.tokenEndpoint, "application/x-www-form-urlencoded", form(f));
        if (r.code >= 400) throw new Exception(L.t("登入已過期，請重新登入"));
        applyToken(s, new JSONObject(r.body), System.currentTimeMillis());
    }
}
