package com.phoneai.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class WebReaderTest {
    /** 一個很小的假網站（JDK 內建的 HttpServer 在 Android 的單元測試環境用不了，所以自己用 ServerSocket 寫）。 */
    private static final class Fake {
        final java.net.ServerSocket ss;
        final java.util.Map<String, Object[]> routes = new java.util.HashMap<>();   // 路徑 → {狀態碼, Content-Type, 本文, Location}
        volatile boolean stop;

        Fake() throws Exception {
            ss = new java.net.ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
            Thread t = new Thread(() -> {
                while (!stop) {
                    try {
                        final java.net.Socket c = ss.accept();
                        new Thread(() -> serve(c)).start();
                    } catch (Exception e) { return; }
                }
            });
            t.setDaemon(true);
            t.start();
        }

        void route(String path, int code, String type, String body, String location) { routes.put(path, new Object[]{code, type, body, location}); }

        private void serve(java.net.Socket c) {
            try (java.net.Socket s = c) {
                java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(s.getInputStream(), StandardCharsets.ISO_8859_1));
                String line = r.readLine();
                for (String h; (h = r.readLine()) != null && !h.isEmpty(); ) { }
                String path = line == null ? "/" : line.split(" ")[1];
                Object[] rt = routes.get(path);
                OutputStream o = s.getOutputStream();
                if (rt == null) { o.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes()); return; }
                byte[] body = ((String) rt[2]).getBytes(StandardCharsets.UTF_8);
                StringBuilder h = new StringBuilder("HTTP/1.1 " + rt[0] + " X\r\nContent-Type: " + rt[1] + "\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n");
                if (rt[3] != null) h.append("Location: ").append(rt[3]).append("\r\n");
                o.write((h + "\r\n").getBytes());
                o.write(body);
                o.flush();
            } catch (Exception ignored) { }
        }
    }

    private Fake http;
    private String base;

    @Before
    public void setUp() throws Exception {
        http = new Fake();
        http.route("/page", 200, "text/html; charset=utf-8",
                "<html><head><title>測試 &amp; 標題</title><style>.x{color:red}</style></head><body><script>evil()</script>"
                + "<h1>大標題</h1><p>第一段&nbsp;文字 &lt;ok&gt; &#20013;</p><ul><li>甲</li><li>乙</li></ul>"
                + "<a href=\"/next\">下一頁</a><a href=\"javascript:x()\">壞</a><a href=\"mailto:a@b.c\">信</a></body></html>", null);
        http.route("/redirect", 302, "text/plain", "", "/page");
        http.route("/loop", 302, "text/plain", "", "/loop");
        http.route("/img", 200, "image/png", "PNGDATA", null);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100_000; i++) sb.append("行").append(i).append("\n");
        http.route("/big", 200, "text/plain; charset=utf-8", sb.toString(), null);
        http.route("/json", 200, "application/json", "{\"a\":1}", null);
        http.route("/missing", 404, "text/plain", "nope", null);
        base = "http://127.0.0.1:" + http.ss.getLocalPort();
        WebReader.allowPrivateForTest = true;
        WebReader.newTurn();
    }

    @After
    public void tearDown() throws Exception {
        WebReader.allowPrivateForTest = false;
        WebReader.newTurn();
        http.stop = true;
        http.ss.close();
    }

    // ---------- 純邏輯 ----------

    @Test
    public void onlyPlainWebUrlsOnAllowedPortsPass() {
        WebReader.allowPrivateForTest = false;
        assertNull(WebReader.checkUrl("https://example.com/a?b=c"));
        assertNull(WebReader.checkUrl("http://example.com:8080/x"));
        assertNotNull(WebReader.checkUrl("file:///sdcard/secret.txt"));
        assertNotNull(WebReader.checkUrl("ftp://example.com/x"));
        assertNotNull(WebReader.checkUrl("https://user:pw@example.com/"));
        assertNotNull(WebReader.checkUrl("https://example.com:22/"));
        assertNotNull(WebReader.checkUrl("http://localhost/x"));
        assertNotNull(WebReader.checkUrl("http://printer.local/x"));
        assertNotNull(WebReader.checkUrl("https://exa mple.com"));
        assertNotNull(WebReader.checkUrl(""));
        assertEquals("沒寫 https:// 就補上", "https://example.com", WebReader.normalize("  example.com "));
    }

    @Test
    public void internalAddressesAreRecognised() throws Exception {
        for (String ip : new String[]{"127.0.0.1", "10.1.2.3", "192.168.0.7", "172.16.5.5", "169.254.1.1", "100.100.1.1", "0.0.0.0", "::1", "fc00::1", "fe80::1", "::ffff:192.168.1.1"})
            assertTrue(ip, WebReader.isInternal(InetAddress.getByName(ip)));
        for (String ip : new String[]{"8.8.8.8", "93.184.216.34", "2606:4700::1111", "::ffff:8.8.8.8"})
            assertFalse(ip, WebReader.isInternal(InetAddress.getByName(ip)));
    }

    @Test
    public void ipLiteralsOfInternalHostsAreRefusedEvenWithoutAName() {
        WebReader.allowPrivateForTest = false;
        assertTrue(WebReader.run(null, "http://192.168.1.1/admin").startsWith("錯誤"));
        assertTrue(WebReader.run(null, "http://127.0.0.1:8080/").startsWith("錯誤"));
        assertTrue(WebReader.run(null, "http://100.64.0.5/").startsWith("錯誤"));
    }

    @Test
    public void htmlBecomesReadableText() {
        String t = WebReader.htmlToText("<html><body><script>x()</script><style>a{}</style><h1>標題</h1><p>內容&amp;更多</p><ul><li>甲</li><li>乙</li></ul><!-- c --></body></html>");
        assertTrue(t, t.contains("標題") && t.contains("內容&更多") && t.contains("- 甲") && t.contains("- 乙"));
        assertFalse(t.contains("x()") || t.contains("a{}") || t.contains("<"));
    }

    @Test
    public void linksAreResolvedAndUnsafeOnesDropped() {
        List<String[]> l = WebReader.linksOf("<a href='/a'>甲</a> <a href=\"https://x.com/b\"><b>乙</b></a> <a href='#top'>頂</a> <a href='javascript:1'>j</a> <a href='mailto:a@b'>m</a> <a href='/a'>重複</a>", "https://site.example/dir/page");
        assertEquals(2, l.size());
        assertEquals("https://site.example/a", l.get(0)[1]);
        assertEquals("乙", l.get(1)[0]);
    }

    @Test
    public void githubListingIsFormatted() {
        String s = WebReader.listing("[{\"name\":\"src\",\"type\":\"dir\"},{\"name\":\"a.md\",\"type\":\"file\",\"size\":12}]");
        assertTrue(s, s.contains("[資料夾] src") && s.contains("[檔案] a.md（12 位元組）"));
    }

    @Test
    public void laterToolsAreRestrictedAfterUntrustedContent() {
        assertTrue(ChatEngine.allowedAfterUntrusted("get_time", true));
        assertTrue(ChatEngine.allowedAfterUntrusted("read_notifications", false));
        assertTrue("只讀過網頁：可以繼續讀（同網站）", ChatEngine.allowedAfterUntrusted("read_url", false));
        assertFalse("讀過通知：連 read_url 也不行（防止被叫去帶資料連外）", ChatEngine.allowedAfterUntrusted("read_url", true));
        assertFalse(ChatEngine.allowedAfterUntrusted("file_send", false));
        assertFalse(ChatEngine.allowedAfterUntrusted("file_delete", false));
        assertFalse(ChatEngine.allowedAfterUntrusted("memory_add", true));
    }

    // ---------- 真的連本機的小網站 ----------

    @Test
    public void readsAPageFollowsRedirectsAndMarksItAsUntrusted() {
        String out = WebReader.run(null, base + "/redirect");
        assertTrue(out, out.startsWith("【網頁內容"));
        assertTrue(out, out.contains("標題：測試 & 標題") && out.contains("大標題") && out.contains("第一段 文字 <ok> 中") && out.contains("- 甲"));
        assertFalse(out.contains("evil()"));
        assertTrue("連結清單要有下一頁：" + out, out.contains("下一頁 → " + base + "/next"));
        assertFalse(out.contains("javascript"));
    }

    @Test
    public void nonTextAndErrorsAreReportedNotCrashed() {
        assertTrue(WebReader.run(null, base + "/img").contains("不是文字內容"));
        assertTrue(WebReader.run(null, base + "/missing").contains("HTTP 404"));
        assertTrue(WebReader.run(null, base + "/loop").contains("轉址太多次"));
        assertTrue(WebReader.run(null, "http://127.0.0.1:1/").startsWith("錯誤"));
    }

    @Test
    public void hugeContentIsCutAndSaysSo() {
        String out = WebReader.run(null, base + "/big");
        assertTrue("長度：" + out.length(), out.length() < WebReader.MAX_CHARS + 600);
        assertTrue(out.contains("內容太長"));
        assertTrue(WebReader.run(null, base + "/json").contains("{\"a\":1}"));
    }

    @Test
    public void aSecondWebsiteInTheSameTurnNeedsTheUsersApproval() {
        assertTrue(WebReader.run(null, base + "/page").contains("大標題"));
        String other = base.replace("127.0.0.1", "localhost") + "/page";   // 另一個網站名稱（同一台，測試用）
        assertEquals("使用者沒有同意讀另一個網站", WebReader.run(msg -> false, other));
        assertEquals("沒有確認框＝不同意", "使用者沒有同意讀另一個網站", WebReader.run(null, other));
        assertTrue("同意之後可以讀", WebReader.run(msg -> true, other).contains("大標題"));
        WebReader.newTurn();   // 新的一輪：不用再確認
        assertTrue(WebReader.run(null, other).contains("大標題"));
    }
}
