package com.phoneai.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class McpOAuthTest {
    @Test public void parsesWwwAuthenticate() {
        String h = "Bearer error=\"invalid_token\", resource_metadata=\"https://mcp.example.com/.well-known/oauth-protected-resource\", scope=\"read write\"";
        assertEquals("https://mcp.example.com/.well-known/oauth-protected-resource", McpOAuth.authParam(h, "resource_metadata"));
        assertEquals("read write", McpOAuth.authParam(h, "scope"));
        assertNull(McpOAuth.authParam(h, "realm"));
        assertNull(McpOAuth.authParam(null, "scope"));
        assertEquals("abc", McpOAuth.authParam("Bearer scope=abc", "scope"));
    }

    @Test public void wellKnownUrls() {
        assertEquals(List.of("https://a.com/.well-known/oauth-protected-resource/mcp", "https://a.com/.well-known/oauth-protected-resource"),
                McpOAuth.resourceMetadataUrls("https://a.com/mcp/"));
        assertEquals(List.of("https://a.com/.well-known/oauth-protected-resource"), McpOAuth.resourceMetadataUrls("https://a.com"));
        assertEquals(List.of("https://a.com/.well-known/oauth-authorization-server", "https://a.com/.well-known/openid-configuration"),
                McpOAuth.authServerMetadataUrls("https://a.com/"));
        assertEquals(3, McpOAuth.authServerMetadataUrls("https://a.com/tenant1").size());
        assertEquals("https://a.com/.well-known/oauth-authorization-server/tenant1", McpOAuth.authServerMetadataUrls("https://a.com/tenant1").get(0));
    }

    @Test public void pkceIsBase64UrlSha256() throws Exception {
        assertEquals("frKPRvBSexh6xho8ogz9XhgIp386P1MbNSJt_XGj73U", McpOAuth.pkceChallenge("dBjftJeZ4CVP-mJ92K1qUhs2PwVu7jHt9rxmGOT28qM"));
    }

    @Test public void authorizeUrlAndQuery() {
        String u = McpOAuth.authorizeUrl("https://as.com/authorize", "cid", "http://127.0.0.1:5000/callback", "ch", "st", "https://mcp.com/mcp", null);
        assertTrue(u.startsWith("https://as.com/authorize?response_type=code&client_id=cid"));
        Map<String, String> q = McpOAuth.parseQuery(u.substring(u.indexOf('?') + 1));
        assertEquals("http://127.0.0.1:5000/callback", q.get("redirect_uri"));
        assertEquals("S256", q.get("code_challenge_method"));
        assertEquals("https://mcp.com/mcp", q.get("resource"));
        assertFalse(q.containsKey("scope"));   // 沒有就不送
    }

    @Test public void googleSpecifics() {
        assertTrue(McpOAuth.googleScopes("https://drivemcp.googleapis.com/mcp/v1").contains("drive.readonly"));
        assertTrue(McpOAuth.googleScopes("https://calendarmcp.googleapis.com/mcp/v1").contains("calendar.events"));
        assertTrue(McpOAuth.googleScopes("https://gmailmcp.googleapis.com/mcp/v1").contains("gmail.readonly"));
        assertNull(McpOAuth.googleScopes("https://mcp.notion.com/mcp"));
        String u = McpOAuth.authorizeUrl("https://accounts.google.com/o/oauth2/v2/auth", "cid", "http://127.0.0.1:1/callback", "ch", "st", "https://drivemcp.googleapis.com/mcp/v1", "a b");
        Map<String, String> q = McpOAuth.parseQuery(u.substring(u.indexOf('?') + 1));
        assertFalse(q.containsKey("resource"));   // Google 不認 resource
        assertEquals("offline", q.get("access_type"));
        assertEquals("consent", q.get("prompt"));
        assertEquals("a b", q.get("scope"));
        assertTrue(McpOAuth.isGoogle("https://oauth2.googleapis.com/token"));
        assertFalse(McpOAuth.isGoogle("https://mcp.notion.com/token"));
    }

    @Test public void tokenAndSession() throws Exception {
        McpOAuth.Session s = new McpOAuth.Session();
        s.refreshToken = "old";
        McpOAuth.applyToken(s, new JSONObject().put("access_token", "A").put("expires_in", 3600), 1000);
        assertEquals("A", s.accessToken);
        assertEquals("old", s.refreshToken);   // 沒給新的更新權杖就留舊的
        assertEquals(1000 + 3_600_000L, s.expiresAt);
        assertFalse(s.needsRefresh(1000));
        assertTrue(s.needsRefresh(1000 + 3_590_000L));
        McpOAuth.Session t = McpOAuth.Session.fromJson(s.toJson());
        assertEquals("A", t.accessToken);
        assertEquals(s.expiresAt, t.expiresAt);
        try { McpOAuth.applyToken(s, new JSONObject().put("error", "invalid_grant"), 0); fail(); }
        catch (Exception e) { assertTrue(e.getMessage().contains("invalid_grant")); }
    }

    // ---- 整個登入流程：假的 MCP＋授權伺服器（同一個本機伺服器），「瀏覽器」直接帶授權碼跳回 ----

    private static final class Req {
        String method, path, body;
    }

    private static Req read(Socket s) throws Exception {
        InputStream in = s.getInputStream();
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int b, state = 0;
        while ((b = in.read()) >= 0) {
            head.write(b);
            state = (b == '\r' || b == '\n') ? state + 1 : 0;
            if (state == 4) break;
        }
        String h = head.toString("UTF-8");
        Req r = new Req();
        String[] first = h.split("\r\n")[0].split(" ");
        r.method = first[0];
        r.path = first[1];
        int len = 0;
        for (String line : h.split("\r\n")) if (line.toLowerCase().startsWith("content-length:")) len = Integer.parseInt(line.substring(15).trim());
        byte[] body = new byte[len];
        int got = 0;
        while (got < len) got += in.read(body, got, len - got);
        r.body = new String(body, StandardCharsets.UTF_8);
        return r;
    }

    private static void reply(Socket s, int code, String body) throws Exception {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        OutputStream o = s.getOutputStream();
        o.write(("HTTP/1.1 " + code + " X\r\nContent-Type: application/json\r\nContent-Length: " + b.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        o.write(b);
        o.flush();
    }

    @Test public void fullLoginFlow() throws Exception {
        final ServerSocket ss = new ServerSocket(0);
        final String base = "http://127.0.0.1:" + ss.getLocalPort();
        final List<String> seen = new ArrayList<>();
        final String[] tokenForm = new String[1];
        Thread srv = new Thread(() -> {
            try {
                while (!ss.isClosed()) {
                    try (Socket s = ss.accept()) {
                        Req r = read(s);
                        seen.add(r.method + " " + r.path);
                        if (r.path.equals("/.well-known/oauth-protected-resource/mcp"))
                            reply(s, 200, new JSONObject().put("resource", base + "/mcp").put("authorization_servers", new JSONArray().put(base)).toString());
                        else if (r.path.equals("/.well-known/oauth-authorization-server"))
                            reply(s, 200, new JSONObject().put("authorization_endpoint", base + "/authorize").put("token_endpoint", base + "/token").put("registration_endpoint", base + "/register").toString());
                        else if (r.path.equals("/register"))
                            reply(s, 201, new JSONObject().put("client_id", "client-1").toString());
                        else if (r.path.equals("/token")) {
                            tokenForm[0] = r.body;
                            reply(s, 200, new JSONObject().put("access_token", "AT").put("refresh_token", "RT").put("expires_in", 3600).toString());
                        } else reply(s, 404, "{}");
                    } catch (Exception ignored) { }
                }
            } catch (Exception ignored) { }
        });
        srv.setDaemon(true);
        srv.start();
        try {
            McpOAuth.Session s = McpOAuth.login(base + "/mcp", null, new McpOAuth.Ui() {
                @Override public void progress(String msg) { }
                @Override public void openBrowser(String url) {
                    Map<String, String> q = McpOAuth.parseQuery(url.substring(url.indexOf('?') + 1));
                    assertEquals("client-1", q.get("client_id"));
                    assertEquals(base + "/mcp", q.get("resource"));
                    new Thread(() -> {   // 使用者在瀏覽器按了同意：跳回 redirect_uri
                        try {
                            HttpURLConnection c = (HttpURLConnection) new URL(q.get("redirect_uri") + "?code=CODE1&state=" + McpOAuth.enc(q.get("state"))).openConnection();
                            c.getResponseCode();
                            c.disconnect();
                        } catch (Exception ignored) { }
                    }).start();
                }
                @Override public boolean cancelled() { return false; }
            });
            assertEquals("AT", s.accessToken);
            assertEquals("RT", s.refreshToken);
            assertEquals("client-1", s.clientId);
            assertEquals(base + "/token", s.tokenEndpoint);
            Map<String, String> f = McpOAuth.parseQuery(tokenForm[0]);
            assertEquals("authorization_code", f.get("grant_type"));
            assertEquals("CODE1", f.get("code"));
            assertEquals(McpOAuth.parseQuery(tokenForm[0]).get("code_verifier").length() > 40, true);
            assertTrue(seen.contains("POST /register"));
        } finally {
            ss.close();
        }
    }

    @Test public void wrongStateIsIgnoredAndNoRegistrationFails() throws Exception {
        final ServerSocket ss = new ServerSocket(0);
        final String base = "http://127.0.0.1:" + ss.getLocalPort();
        Thread srv = new Thread(() -> {
            while (!ss.isClosed()) {
                try (Socket s = ss.accept()) {
                    Req r = read(s);
                    if (r.path.equals("/.well-known/oauth-authorization-server"))
                        reply(s, 200, new JSONObject().put("authorization_endpoint", base + "/authorize").put("token_endpoint", base + "/token").toString());
                    else reply(s, 404, "{}");
                } catch (Exception ignored) { }
            }
        });
        srv.setDaemon(true);
        srv.start();
        try {
            McpOAuth.login(base + "/mcp", null, new McpOAuth.Ui() {
                @Override public void progress(String msg) { }
                @Override public void openBrowser(String url) { fail(); }
                @Override public boolean cancelled() { return false; }
            });
            fail();
        } catch (Exception e) {
            assertTrue(e.getMessage(), e.getMessage().contains("registration_endpoint"));
        } finally {
            ss.close();
        }
    }
}
