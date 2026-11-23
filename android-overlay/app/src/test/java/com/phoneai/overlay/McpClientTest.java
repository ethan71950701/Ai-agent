package com.phoneai.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class McpClientTest {
    @Test public void parsesPlainJsonAndSseAndErrors() throws Exception {
        JSONObject r = McpClient.parseResponse("application/json", "{\"jsonrpc\":\"2.0\",\"id\":3,\"result\":{\"x\":1}}", 3);
        assertEquals(1, r.getInt("x"));
        String sse = "event: message\ndata: {\"jsonrpc\":\"2.0\",\"method\":\"notifications/progress\"}\n\nevent: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":7,\"result\":{\"ok\":true}}\n\n";
        assertTrue(McpClient.parseResponse("text/event-stream", sse, 7).getBoolean("ok"));
        try { McpClient.parseResponse("application/json", "{\"jsonrpc\":\"2.0\",\"id\":3,\"error\":{\"code\":-1,\"message\":\"壞了\"}}", 3); fail(); }
        catch (Exception e) { assertTrue(e.getMessage().contains("壞了")); }
        try { McpClient.parseResponse("application/json", "{\"jsonrpc\":\"2.0\",\"id\":9,\"result\":{}}", 3); fail(); }
        catch (Exception expected) { }
    }

    @Test public void convertKeepsOnlyAllowedReadOnlyTools() throws Exception {
        JSONArray in = new JSONArray("[" +
                "{\"name\":\"list_releases\",\"description\":\"列出 release\",\"inputSchema\":{\"$schema\":\"x\",\"type\":\"object\",\"properties\":{\"owner\":{\"type\":\"string\"}}},\"annotations\":{\"readOnlyHint\":true}}," +
                "{\"name\":\"create_issue\",\"description\":\"會寫入\",\"inputSchema\":{\"type\":\"object\"}}," +                         // 不在白名單
                "{\"name\":\"get_file_contents\",\"description\":\"d\",\"inputSchema\":{\"type\":\"object\"},\"annotations\":{\"readOnlyHint\":false}}," + // 白名單內但服務標示會寫入
                "{\"name\":\"issue_read\",\"description\":\"d\"}]");
        JSONArray out = McpTools.convert(in, "me/repo");
        assertEquals(2, out.length());
        assertEquals("gh_list_releases", out.getJSONObject(0).getString("name"));
        assertFalse(out.getJSONObject(0).getJSONObject("input_schema").has("$schema"));
        assertTrue(out.getJSONObject(0).getString("description").contains("me/repo"));
        assertEquals("gh_issue_read", out.getJSONObject(1).getString("name"));
    }

    /** 假的 MCP 服務（ServerSocket）：回應 initialize、tools/list、tools/call，並檢查權杖與 session 標頭有帶。 */
    @Test public void clientTalksToFakeServer() throws Exception {
        final ServerSocket ss = new ServerSocket(0);
        final List<String> seen = new ArrayList<>();
        Thread t = new Thread(() -> {
            try {
                for (int n = 0; n < 4; n++) {
                    try (Socket s = ss.accept()) {
                        InputStream in = s.getInputStream();
                        BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                        String line, auth = "", sid = "";
                        int len = 0;
                        while ((line = br.readLine()) != null && !line.isEmpty()) {
                            String low = line.toLowerCase();
                            if (low.startsWith("content-length:")) len = Integer.parseInt(line.substring(15).trim());
                            if (low.startsWith("authorization:")) auth = line.substring(14).trim();
                            if (low.startsWith("mcp-session-id:")) sid = line.substring(15).trim();
                        }
                        char[] buf = new char[len];
                        int got = 0;
                        while (got < len) { int k = br.read(buf, got, len - got); if (k < 0) break; got += k; }
                        JSONObject req = new JSONObject(new String(buf, 0, got));
                        seen.add(req.optString("method") + "|" + auth + "|" + sid);
                        String body, extra = "";
                        int status = 200;
                        String m = req.optString("method");
                        if (m.equals("initialize")) { body = "{\"jsonrpc\":\"2.0\",\"id\":" + req.getInt("id") + ",\"result\":{\"protocolVersion\":\"2025-06-18\"}}"; extra = "Mcp-Session-Id: S1\r\n"; }
                        else if (m.equals("notifications/initialized")) { body = ""; status = 202; }
                        else if (m.equals("tools/list")) body = "{\"jsonrpc\":\"2.0\",\"id\":" + req.getInt("id") + ",\"result\":{\"tools\":[{\"name\":\"list_releases\"}]}}";
                        else body = "{\"jsonrpc\":\"2.0\",\"id\":" + req.getInt("id") + ",\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"hello\"}]}}";
                        byte[] b = body.getBytes(StandardCharsets.UTF_8);
                        OutputStream o = s.getOutputStream();
                        o.write(("HTTP/1.1 " + status + " X\r\nContent-Type: application/json\r\n" + extra + "Content-Length: " + b.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                        o.write(b);
                        o.flush();
                    }
                }
            } catch (Exception ignored) { }
        });
        t.start();
        McpClient c = new McpClient("http://127.0.0.1:" + ss.getLocalPort() + "/mcp/", "tok123");
        c.initialize();
        assertEquals(1, c.listTools().length());
        assertEquals("hello", c.callTool("list_releases", new JSONObject(), 1000));
        t.join(3000);
        ss.close();
        assertEquals("initialize|Bearer tok123|", seen.get(0));
        assertTrue(seen.get(3).startsWith("tools/call|Bearer tok123|S1"));   // 握手之後的請求都帶 session
    }
}
