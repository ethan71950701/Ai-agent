package com.phoneai.overlay;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 裝置之間傳檔案的伺服器（純 Java，不依賴 Android）。設計與安全底線見 docs/FILE_TRANSFER.md：
 * 只服務區網位址；每個請求要驗簽章（防重送、防亂猜）；只開放 host.root()（/sdcard），路徑正規化後不能跳出去；
 * 收到的檔案只能放進 host.inbox()。回應內容與收進來的檔案都是加密分段（TransferCrypto）。
 */
final class TransferServer {
    static final int PORT = 47321;

    interface Host {
        String myId();
        String myName();
        /** 這個裝置配對過的長期金鑰；沒配對過回 null。 */
        byte[] keyOf(String peerId);
        File root();
        File inbox();
        boolean pairingOpen();
        /** 有裝置想配對：把確認碼顯示給使用者；使用者按相符才算數（host 之後自己存金鑰）。 */
        void pairRequest(String peerId, String peerName, byte[] key, String sas);
        /** waiting／accepted／rejected／none */
        String pairState(String peerId);
        /** 記錄與通知：kind = get／put／list／find；what = 路徑或名稱；size = 位元組數（沒有就 0）。 */
        void event(String peerId, String kind, String what, long size);
        /** 已驗證的對方每次連上都會帶它現在的名稱；存下來，改名後不用重新配對。 */
        void peerName(String peerId, String name);
        /** 傳輸進度：toPeer = 我們送給對方（對方取檔），否則是對方送來給我們。 */
        void progress(String peerId, boolean toPeer, String name, long done, long total);
        /** 一次傳輸結束（成功或失敗），用來收起進度通知。 */
        void transferEnded();
    }

    private final Host host;
    private ServerSocket server;
    private volatile boolean running;
    private final LinkedHashMap<String, Long> seen = new LinkedHashMap<String, Long>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Long> e) { return size() > 2000; }
    };
    private final HashMap<String, long[]> fails = new HashMap<>();   // ip → {失敗次數, 第一次失敗的時間}

    TransferServer(Host host) { this.host = host; }

    synchronized void start(int port) throws IOException {
        if (running) return;
        server = new ServerSocket();
        server.setReuseAddress(true);
        server.bind(new InetSocketAddress(port));
        running = true;
        Thread t = new Thread(this::loop, "transfer-server");
        t.setDaemon(true);
        t.start();
    }

    synchronized int port() { return server == null ? -1 : server.getLocalPort(); }

    synchronized void stop() {
        running = false;
        try { if (server != null) server.close(); } catch (IOException ignored) { }
        server = null;
    }

    private void loop() {
        ServerSocket ss = server;
        while (running && ss != null) {
            try {
                final Socket s = ss.accept();
                Thread t = new Thread(() -> serve(s), "transfer-conn");
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                if (!running) return;
            }
        }
    }

    // ---------- 連線處理 ----------

    private void serve(Socket sock) {
        try (Socket s = sock) {
            s.setSoTimeout(20000);
            InetAddress a = s.getInetAddress();
            if (!(a.isSiteLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress())) return;   // 只服務區網
            String ip = a.getHostAddress();
            InputStream in = new BufferedInputStream(s.getInputStream());
            OutputStream out = new BufferedOutputStream(s.getOutputStream());
            if (blocked(ip)) { reply(out, 429, "too many failures"); return; }
            String line = readLine(in);
            if (line == null) return;
            String[] rl = line.split(" ");
            if (rl.length < 2) return;
            String method = rl[0], target = rl[1];
            Map<String, String> h = new HashMap<>();
            for (;;) {
                String l = readLine(in);
                if (l == null || l.isEmpty()) break;
                int c = l.indexOf(':');
                if (c > 0) h.put(l.substring(0, c).trim().toLowerCase(Locale.ROOT), l.substring(c + 1).trim());
            }
            String path = target, query = "";
            int q = target.indexOf('?');
            if (q >= 0) { path = target.substring(0, q); query = target.substring(q + 1); }
            Map<String, String> params = parseQuery(query);
            if (path.startsWith("/v1/pair/")) { pair(path, params, h, in, out); return; }

            Auth au = authenticate(method, target, h);
            if (au == null) { fail(ip); reply(out, 401, "unauthorized"); return; }
            String nm = h.get("x-name");
            if (nm != null) {
                try {
                    String name = java.net.URLDecoder.decode(nm, "UTF-8").trim();
                    if (!name.isEmpty() && name.length() <= 40) host.peerName(au.peer, name);
                } catch (Exception ignored) { }
            }
            switch (path) {
                case "/v1/ping":
                    sendText(out, au, "ok\t" + host.myName());
                    break;
                case "/v1/list": {
                    File dir = resolve(params.get("path"));
                    host.event(au.peer, "list", rel(dir), 0);
                    sendText(out, au, list(dir));
                    break;
                }
                case "/v1/find": {
                    String name = params.get("name");
                    host.event(au.peer, "find", name == null ? "" : name, 0);
                    sendText(out, au, find(name == null ? "" : name));
                    break;
                }
                case "/v1/file": {
                    File f = resolve(params.get("path"));
                    if (!f.isFile()) { reply(out, 404, "not a file"); return; }
                    try { sendFile(out, au, f); } finally { host.transferEnded(); }
                    break;
                }
                case "/v1/put":
                    try { receive(in, out, au, params.get("name"), params.get("size")); } finally { host.transferEnded(); }
                    break;
                default:
                    reply(out, 404, "unknown");
            }
        } catch (Exception ignored) {
            // 單一連線出錯不影響伺服器
        }
    }

    private static final class Auth {
        String peer;
        byte[] key;
        String nonce;
    }

    private Auth authenticate(String method, String target, Map<String, String> h) {
        String id = h.get("x-id"), ts = h.get("x-ts"), nonce = h.get("x-nonce"), sig = h.get("x-sig");
        if (id == null || ts == null || nonce == null || sig == null || nonce.length() > 64 || id.length() > 64) return null;
        byte[] key = host.keyOf(id);
        if (key == null) return null;
        long t;
        try { t = Long.parseLong(ts); } catch (NumberFormatException e) { return null; }
        if (Math.abs(System.currentTimeMillis() / 1000 - t) > 60) return null;
        if (!TransferCrypto.sameHex(TransferCrypto.sign(key, method, target, ts, nonce), sig)) return null;
        synchronized (seen) {
            if (seen.containsKey(id + nonce)) return null;   // 同一個請求被重送
            seen.put(id + nonce, System.currentTimeMillis());
        }
        Auth a = new Auth();
        a.peer = id;
        a.key = key;
        a.nonce = nonce;
        return a;
    }

    private synchronized boolean blocked(String ip) {
        long[] f = fails.get(ip);
        if (f == null) return false;
        if (System.currentTimeMillis() - f[1] > 5 * 60_000L) { fails.remove(ip); return false; }
        return f[0] >= 10;
    }

    private synchronized void fail(String ip) {
        long[] f = fails.get(ip);
        long now = System.currentTimeMillis();
        if (f == null || now - f[1] > 5 * 60_000L) fails.put(ip, new long[]{1, now});
        else f[0]++;
    }

    // ---------- 回應 ----------

    private static void reply(OutputStream out, int code, String msg) throws IOException {
        byte[] b = msg.getBytes(StandardCharsets.UTF_8);
        out.write(("HTTP/1.1 " + code + " X\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: " + b.length
                + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
        out.write(b);
        out.flush();
    }

    private static void sendText(OutputStream out, Auth au, String text) throws IOException {
        out.write("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
        try (TransferCrypto.SealedOut so = new TransferCrypto.SealedOut(out, TransferCrypto.encKey(au.key, au.nonce))) {
            so.write(text.getBytes(StandardCharsets.UTF_8));
        }
        out.flush();
    }

    private void sendFile(OutputStream out, Auth au, File f) throws IOException {
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nX-Size: " + f.length() + "\r\nConnection: close\r\n\r\n")
                .getBytes(StandardCharsets.ISO_8859_1));
        long total = 0;
        try (InputStream fin = new FileInputStream(f);
             TransferCrypto.SealedOut so = new TransferCrypto.SealedOut(out, TransferCrypto.encKey(au.key, au.nonce))) {
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = fin.read(buf)) > 0) {
                so.write(buf, 0, n);
                total += n;
                host.progress(au.peer, true, f.getName(), total, f.length());
            }
        }
        out.flush();
        host.event(au.peer, "get", rel(f), total);
    }

    private void receive(InputStream in, OutputStream out, Auth au, String name, String size) throws IOException {
        long expect = 0;
        try { expect = Long.parseLong(size); } catch (Exception ignored) { }
        String base = name == null ? "" : new File(name.replace('\\', '/')).getName();
        if (base.isEmpty() || base.equals("..") || base.equals(".")) { reply(out, 400, "bad name"); return; }
        File inbox = host.inbox();
        if (!inbox.isDirectory() && !inbox.mkdirs()) { reply(out, 500, "no inbox"); return; }
        File dest = unique(inbox, base);
        File tmp = new File(inbox, "." + dest.getName() + ".part");
        long total = 0;
        try (InputStream body = new TransferCrypto.OpenedIn(in, TransferCrypto.encKey(au.key, au.nonce));
             FileOutputStream fo = new FileOutputStream(tmp)) {
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = body.read(buf)) > 0) {
                fo.write(buf, 0, n);
                total += n;
                host.progress(au.peer, false, base, total, expect);
            }
        } catch (IOException | RuntimeException e) {
            tmp.delete();   // 中斷或被停止：未完成的檔案刪掉
            throw e;
        }
        if (!tmp.renameTo(dest)) { tmp.delete(); reply(out, 500, "rename failed"); return; }
        host.event(au.peer, "put", rel(dest), total);
        sendText(out, au, "saved\t" + dest.getName() + "\t" + total);
    }

    private static File unique(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) return f;
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name, ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; i < 1000; i++) {
            f = new File(dir, stem + " (" + i + ")" + ext);
            if (!f.exists()) return f;
        }
        return new File(dir, stem + "-" + System.currentTimeMillis() + ext);
    }

    // ---------- 路徑、列表、搜尋 ----------

    /** 把使用者給的路徑（相對於根目錄，或帶根目錄前綴）換成實際檔案；不能跳出根目錄。 */
    private File resolve(String path) throws IOException {
        File root = host.root().getCanonicalFile();
        String r = path == null ? "" : path.replace('\\', '/');
        String rootPath = root.getPath();
        if (r.startsWith(rootPath)) r = r.substring(rootPath.length());
        if (r.startsWith("/sdcard")) r = r.substring("/sdcard".length());
        while (r.startsWith("/")) r = r.substring(1);
        File f = new File(root, r).getCanonicalFile();
        if (!f.equals(root) && !f.getPath().startsWith(rootPath + File.separator)) throw new IOException("path outside root");
        return f;
    }

    private String rel(File f) { return relTo(host.root(), f); }

    static String relTo(File rootDir, File f) {
        try {
            String root = rootDir.getCanonicalPath();
            String p = f.getCanonicalPath();
            return p.equals(root) ? "" : p.startsWith(root + File.separator) ? p.substring(root.length() + 1) : p;
        } catch (IOException e) {
            return f.getName();
        }
    }

    static String lineOf(File rootDir, File f) {
        return (f.isDirectory() ? "D" : "F") + "\t" + (f.isFile() ? f.length() : 0) + "\t" + f.lastModified() + "\t" + relTo(rootDir, f) + "\n";
    }

    private String line(File f) { return lineOf(host.root(), f); }

    private String list(File dir) {
        File[] kids = dir.listFiles();
        if (kids == null) return "";
        Arrays.sort(kids, (x, y) -> x.isDirectory() != y.isDirectory() ? (x.isDirectory() ? -1 : 1) : x.getName().compareToIgnoreCase(y.getName()));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < kids.length && i < 500; i++) sb.append(line(kids[i]));
        return sb.toString();
    }

    /** 依檔名（包含關鍵字、不分大小寫）找檔案：最多找 4 秒、4 萬個項目，回傳最新的 30 個。 */
    private String find(String query) throws IOException { return findIn(host.root(), query); }

    /** 在 rootDir 底下依檔名找；伺服器與「找本機檔案」共用。 */
    static String findIn(File rootDir, String query) throws IOException {
        String q = query.toLowerCase(Locale.ROOT).trim();
        if (q.isEmpty()) return "";
        File root = rootDir.getCanonicalFile();
        long deadline = System.currentTimeMillis() + 4000;
        int visited = 0;
        List<File> hits = new ArrayList<>();
        ArrayDeque<Object[]> todo = new ArrayDeque<>();
        todo.add(new Object[]{root, 0});
        while (!todo.isEmpty() && visited < 40000 && System.currentTimeMillis() < deadline && hits.size() < 200) {
            Object[] cur = todo.poll();
            File dir = (File) cur[0];
            int depth = (Integer) cur[1];
            File[] kids = dir.listFiles();
            if (kids == null) continue;
            for (File k : kids) {
                visited++;
                String n = k.getName();
                if (n.startsWith(".")) continue;
                if (depth == 0 && n.equals("Android")) continue;   // 其他 App 的資料夾，不找
                if (n.toLowerCase(Locale.ROOT).contains(q)) hits.add(k);
                if (k.isDirectory() && depth < 8) todo.add(new Object[]{k, depth + 1});
            }
        }
        Collections.sort(hits, (x, y) -> Long.compare(y.lastModified(), x.lastModified()));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hits.size() && i < 30; i++) sb.append(lineOf(rootDir, hits.get(i)));
        return sb.toString();
    }

    // ---------- 配對（只在配對視窗開著時；不需要簽章，因為還沒有金鑰） ----------

    private void pair(String path, Map<String, String> params, Map<String, String> h, InputStream in, OutputStream out) throws Exception {
        if (!host.pairingOpen()) { reply(out, 403, "pairing closed"); return; }
        if ("/v1/pair/hello".equals(path)) {
            reply(out, 200, "PHONEAI\t" + host.myId() + "\t" + host.myName().replace('\t', ' ').replace('\n', ' ') + "\t" + port());
            return;
        }
        if ("/v1/pair/status".equals(path)) {
            reply(out, 200, host.pairState(params.get("id") == null ? "" : params.get("id")));
            return;
        }
        if (!"/v1/pair/start".equals(path)) { reply(out, 404, "unknown"); return; }
        int len = 0;
        try { len = Integer.parseInt(h.get("content-length")); } catch (Exception ignored) { }
        if (len <= 0 || len > 2048) { reply(out, 400, "bad body"); return; }
        byte[] body = new byte[len];
        int got = 0;
        while (got < len) {
            int r = in.read(body, got, len - got);
            if (r < 0) return;
            got += r;
        }
        Map<String, String> m = parseLines(new String(body, StandardCharsets.UTF_8));
        String id = m.get("id"), name = m.get("name"), pub = m.get("pub");
        if (id == null || id.isEmpty() || id.length() > 64 || pub == null) { reply(out, 400, "bad body"); return; }
        byte[] theirPub = TransferCrypto.unhex(pub);
        KeyPair mine = TransferCrypto.newPair();
        byte[] myPub = mine.getPublic().getEncoded();
        byte[] shared = TransferCrypto.shared(mine.getPrivate(), theirPub);
        String tr = TransferCrypto.transcript(id, theirPub, host.myId(), myPub);
        host.pairRequest(id, name == null ? id : name, TransferCrypto.pairKey(shared, tr), TransferCrypto.sas(shared, tr));
        reply(out, 200, "id=" + host.myId() + "\nname=" + host.myName() + "\npub=" + TransferCrypto.hex(myPub));
    }

    // ---------- 小工具 ----------

    static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) >= 0) {
            if (c == '\n') break;
            if (c != '\r') b.write(c);
            if (b.size() > 8192) throw new IOException("line too long");
        }
        if (c < 0 && b.size() == 0) return null;
        return new String(b.toByteArray(), StandardCharsets.ISO_8859_1);
    }

    static Map<String, String> parseQuery(String q) {
        Map<String, String> m = new HashMap<>();
        if (q == null || q.isEmpty()) return m;
        for (String kv : q.split("&")) {
            int e = kv.indexOf('=');
            try {
                if (e > 0) m.put(URLDecoder.decode(kv.substring(0, e), "UTF-8"), URLDecoder.decode(kv.substring(e + 1), "UTF-8"));
            } catch (Exception ignored) { }
        }
        return m;
    }

    static Map<String, String> parseLines(String s) {
        Map<String, String> m = new HashMap<>();
        for (String l : s.split("\n")) {
            int e = l.indexOf('=');
            if (e > 0) m.put(l.substring(0, e).trim(), l.substring(e + 1).trim());
        }
        return m;
    }
}
