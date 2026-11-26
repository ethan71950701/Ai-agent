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
        /** 已驗證的對方連上來：記下它的來源位址（區網或 Tailscale），之後我們也能連回去。 */
        void peerSeen(String peerId, String ip);
        /** 已驗證的對方送來它的推播 token（叫醒它用）。 */
        default void peerFcm(String peerId, String token) { }
        /** 我自己的推播 token（回給剛送來 token 的對方）；沒有回 null。 */
        default String myFcmToken() { return null; }
        /** 處理某個已驗證對方的請求時出錯（原因給使用者看；以前只會默默斷線）。 */
        default void serverError(String peerId, String what) { }
        /** 已驗證的對方想把推播設定（Firebase 設定檔＋發送金鑰）傳給我：要使用者確認並存起來；接受回 true。會等使用者按（可能很久）。 */
        default boolean fcmSetupOffer(String peerId, String configJson, String keyJson) { return false; }
    }

    /** 接收端的使用者按了「停止」（keep=false）或「暫停」（keep=true）：host.progress 丟這個，伺服器會明確告訴對方，對方就不會當成斷線一直重試。 */
    static final class Aborted extends RuntimeException {
        final boolean keepPartial;
        Aborted(boolean keepPartial) { super(keepPartial ? "paused by receiver" : "stopped by receiver"); this.keepPartial = keepPartial; }
    }

    /** 剛被接收端停掉的上傳（對方編號|指紋 → 時間）：對方在這幾秒內重連要直接回「已停止」，不能接著收。 */
    private static final java.util.Map<String, Long> STOPPED = new java.util.concurrent.ConcurrentHashMap<>();
    static final long STOPPED_MEMORY_MS = 5_000;

    /** 把過期的「剛停止」紀錄清掉（放進新的之前順便做，Map 不會一直變大）。回傳清了幾筆。 */
    static int pruneStopped(java.util.Map<String, Long> m, long nowMs) {
        int n = 0;
        for (java.util.Iterator<Long> it = m.values().iterator(); it.hasNext();) if (nowMs - it.next() >= STOPPED_MEMORY_MS) { it.remove(); n++; }
        return n;
    }

    /** 剛收完的上傳（對方編號|指紋 → 結果）：送出端沒收到「收到了」的回覆時，可以來問一聲，不必整個重傳（會變成「檔名 (1)」的重複檔案）；校驗碼對不上時也靠它請我們刪掉那個檔案。 */
    static final class Done {
        final String name, sha;
        final long size, at;
        Done(String name, long size, String sha, long at) { this.name = name; this.size = size; this.sha = sha; this.at = at; }
    }
    static final long DONE_MEMORY_MS = 10 * 60_000L;
    private static final java.util.Map<String, Done> COMPLETED = java.util.Collections.synchronizedMap(new LinkedHashMap<String, Done>() {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Done> e) { return size() > 64; }
    });

    private static Done recentDone(String peer, String fp) {
        if (!Resume.validFp(fp)) return null;
        Done d = COMPLETED.get(peer + "|" + fp);
        return d != null && System.currentTimeMillis() - d.at < DONE_MEMORY_MS ? d : null;
    }

    static final int MAX_HEADER_LINES = 64, MAX_HEADER_BYTES = 16 * 1024;
    /** 請求標頭整段最多給多久（毫秒）；測試會調短。 */
    static volatile long headDeadlineMs = 15_000;

    private static int timeLeft(long deadlineMs) { return (int) Math.max(200, Math.min(20_000, deadlineMs - System.currentTimeMillis())); }

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

    /** 同時最多幾個連線（含傳輸中的），超過就拒絕新的。 */
    static final int MAX_CONNECTIONS = 8;
    private static final Checksum.Cache SENT_SHA = new Checksum.Cache(64);
    /** 沒有現成校驗碼時，超過這個大小就不替對方現算（讀太久），回空的，對方略過校驗。 */
    static final long SHA_ON_DEMAND_MAX = 512L * 1024 * 1024;
    /** 目前正在處理的連線數（臨時開的伺服器用它判斷什麼時候可以關）。 */
    static int activeConnections() { return ACTIVE.get(); }

    private static final java.util.concurrent.atomic.AtomicInteger ACTIVE = new java.util.concurrent.atomic.AtomicInteger();
    /** 單一檔案接收上限（64 GiB，實質上不限，只是一道保險）；真正擋住塞滿儲存空間的是下面的「收完要保留」與 SpaceReserver 的空間檢查。 */
    static final long MAX_UPLOAD_BYTES = 64L * 1024 * 1024 * 1024;
    private static final long KEEP_FREE_BYTES = 200L * 1024 * 1024;   // 接收後至少還要剩這麼多空間
    private static final Object RENAME_LOCK = new Object();
    /** 正在接收的續傳半成品（同一份不能同時被兩個連線寫）。 */
    private static final java.util.Set<String> ACTIVE_PARTS = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private static final SpaceReserver RESERVER = new SpaceReserver();

    private void loop() {
        ServerSocket ss = server;
        while (running && ss != null) {
            try {
                final Socket s = ss.accept();
                if (Announcements.off("file_transfer")) { try { s.close(); } catch (IOException ignored) { } continue; }   // 遠端公告暫時停用檔案互傳
                if (ACTIVE.incrementAndGet() > MAX_CONNECTIONS) {   // 同時連線太多（異常或惡意）：直接關掉，不再開執行緒
                    ACTIVE.decrementAndGet();
                    try { s.close(); } catch (IOException ignored) { }
                    continue;
                }
                Thread t = new Thread(() -> { try { serve(s); } finally { ACTIVE.decrementAndGet(); } }, "transfer-conn");
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                if (!running) return;
            }
        }
    }

    /** 是不是 Tailscale 的位址（100.64.0.0/10）。 */
    static boolean isTailnetAddr(InetAddress a) {
        byte[] b = a.getAddress();
        return b.length == 4 && (b[0] & 255) == 100 && (b[1] & 0xC0) == 64;
    }

    static boolean isTailnetString(String ip) {
        try {
            return isTailnetAddr(InetAddress.getByName(ip)) && ip.indexOf(':') < 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** 區網、本機、link-local，以及 Tailscale 的位址範圍（100.64.0.0/10，Java 不把它算成區網）。 */
    static boolean allowed(InetAddress a) {
        if (a.isSiteLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress()) return true;
        byte[] b = a.getAddress();
        return b.length == 4 && (b[0] & 255) == 100 && (b[1] & 0xC0) == 64;
    }

    // ---------- 連線處理 ----------

    private void serve(Socket sock) {
        try (Socket s = sock) {
            s.setSoTimeout(20000);
            TransferNotify.bindCloser(s);   // 這條連線的傳輸被按停止／暫停時，直接關掉它（對方不收資料、寫入卡住時才停得下來）
            InetAddress a = s.getInetAddress();
            if (!allowed(a)) return;   // 只服務區網與 Tailscale（100.64.0.0/10）
            String ip = a.getHostAddress();
            InputStream in = new BufferedInputStream(s.getInputStream());
            OutputStream out = new BufferedOutputStream(s.getOutputStream());
            if (blocked(ip)) { reply(out, 429, "too many failures"); return; }
            // 請求標頭要在限定時間內、限定大小與行數內送完：以前每次讀取各自逾時 20 秒，有人每 19 秒送一個位元組就能一直占著連線（8 個就占滿）
            final long headDeadline = System.currentTimeMillis() + headDeadlineMs;
            int headBytes = 0, headLines = 0;
            s.setSoTimeout(timeLeft(headDeadline));
            String line = readLine(in);
            if (line == null) return;
            headBytes += line.length();
            String[] rl = line.split(" ");
            if (rl.length < 2) return;
            String method = rl[0], target = rl[1];
            Map<String, String> h = new HashMap<>();
            for (;;) {
                s.setSoTimeout(timeLeft(headDeadline));
                String l = readLine(in);
                if (l == null || l.isEmpty()) break;
                headBytes += l.length();
                if (++headLines > MAX_HEADER_LINES || headBytes > MAX_HEADER_BYTES || System.currentTimeMillis() > headDeadline) return;   // 太多、太大、太慢：直接掛斷
                int c = l.indexOf(':');
                if (c > 0) h.put(l.substring(0, c).trim().toLowerCase(Locale.ROOT), l.substring(c + 1).trim());
            }
            s.setSoTimeout(20000);
            String path = target, query = "";
            int q = target.indexOf('?');
            if (q >= 0) { path = target.substring(0, q); query = target.substring(q + 1); }
            Map<String, String> params = parseQuery(query);
            if (path.startsWith("/v1/pair/")) { pair(path, params, h, in, out); return; }

            Auth au = authenticate(method, target, h);
            if (au == null) { fail(ip); reply(out, 401, "unauthorized"); return; }
            au.dk = "1".equals(params.get("dk"));   // dk 在網址裡，已被簽章涵蓋
            host.peerSeen(au.peer, ip);
            String myTailnet = h.get("x-tailnet");   // 對方自己的 Tailscale 位址：已驗證，記下來
            if (myTailnet != null && isTailnetString(myTailnet)) host.peerSeen(au.peer, myTailnet);
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
                    long off = 0;
                    try { off = Long.parseLong(params.get("offset") == null ? "0" : params.get("offset").trim()); } catch (Exception ignored) { }
                    if (off < 0 || off > f.length()) off = 0;   // 要的起點不合理：從頭送（對方看回應的 X-Offset 就知道）
                    try { sendFile(out, au, f, off); } finally { host.transferEnded(); }
                    break;
                }
                case "/v1/discard": {   // 使用者按「停止」：請對方（我）把這個指紋的半成品刪掉，不留續傳用的殘檔
                    String fp = params.get("fp");
                    boolean gone = false;
                    if (Resume.validFp(fp) && insideRoot(host.root(), host.inbox())) {
                        File part = new File(host.inbox(), Resume.partName(au.peer, fp));
                        // 對方剛按停止：它的上一條連線可能還沒被我發現已經斷了，那份半成品還在「使用中」；等它收尾（最多 3 秒）再刪
                        for (int i = 0; i < 30 && ACTIVE_PARTS.contains(part.getName()); i++) {
                            try { Thread.sleep(100); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                        }
                        if (!ACTIVE_PARTS.contains(part.getName())) gone = part.delete();
                    }
                    sendText(out, au, gone ? "deleted" : "none");
                    break;
                }
                case "/v1/done": {   // 送出端沒收到回覆時來問：這個指紋剛剛是不是已經收完了（檔案還在才算）
                    Done d = recentDone(au.peer, params.get("fp"));
                    File f = d == null ? null : new File(host.inbox(), d.name);
                    sendText(out, au, d != null && f.isFile() && f.length() == d.size ? "saved\t" + d.name + "\t" + d.size + "\t" + d.sha : "");
                    break;
                }
                case "/v1/undo": {   // 送出端核對校驗碼發現不一樣：請我們把剛收下的那個檔案刪掉，不留壞檔
                    Done d = recentDone(au.peer, params.get("fp"));
                    File f = d == null ? null : new File(host.inbox(), d.name);
                    boolean gone = f != null && f.isFile() && f.length() == d.size && insideRoot(host.inbox(), f) && f.delete();
                    if (gone) COMPLETED.remove(au.peer + "|" + params.get("fp"));
                    sendText(out, au, gone ? "deleted" : "none");
                    break;
                }
                case "/v1/offset": {   // 續傳：這個指紋的半成品現在收了幾個位元組（沒有回 0）
                    String fp = params.get("fp");
                    long have = 0;
                    if (Resume.validFp(fp)) {
                        File part = new File(host.inbox(), Resume.partName(au.peer, fp));
                        if (part.isFile() && insideRoot(host.root(), host.inbox())) have = part.length();
                    }
                    sendText(out, au, String.valueOf(have));
                    break;
                }
                case "/v1/fcm": {
                    String tk = params.get("token");
                    boolean asking = tk == null || tk.isEmpty();   // 空的＝我沒有 token，只是想拿你的
                    if (!asking && !Doorbell.validToken(tk)) { reply(out, 400, "bad token"); return; }
                    if (!asking) host.peerFcm(au.peer, tk);
                    String mine = host.myFcmToken();
                    sendText(out, au, "ok\t" + (Doorbell.validToken(mine) ? mine : ""));   // 同一次請求互換：把我的 token 一起回給對方
                    break;
                }
                case "/v1/space":   // 對方傳大檔案前先問：現在能收多少（扣掉別人已預留的）、要保留多少、單檔上限
                    sendText(out, au, Math.max(0, host.inbox().getUsableSpace() - RESERVER.reserved()) + "\t" + KEEP_FREE_BYTES + "\t" + MAX_UPLOAD_BYTES);
                    break;
                case "/v1/sha": {
                    File f = resolve(params.get("path"));
                    if (!f.isFile()) { reply(out, 404, "not a file"); return; }
                    String have = SENT_SHA.get(f);
                    if (have == null && f.length() <= SHA_ON_DEMAND_MAX) {
                        have = Checksum.sha256(f);
                        SENT_SHA.put(f, have);
                    }
                    sendText(out, au, have == null ? "" : have);
                    break;
                }
                case "/v1/fcmsetup":
                    receiveFcmSetup(in, out, au, params.get("size"));
                    break;
                case "/v1/put":
                    try { receive(in, out, au, params.get("name"), params.get("size"), params.get("fp"), params.get("offset")); } finally { host.transferEnded(); }
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
        boolean dk;   // 對方是新版（請求網址帶已簽章的 dk=1）：回應用另一把金鑰，標頭回 X-Dk: 1

        /** 回應的加密金鑰（跟請求本文分開，避免 AES-GCM 金鑰＋IV 重複）。 */
        byte[] respKey() { return dk ? TransferCrypto.respKey(key, nonce) : TransferCrypto.encKey(key, nonce); }

        String dkHeader() { return dk ? "X-Dk: 1\r\n" : ""; }
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
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\n" + au.dkHeader() + "Connection: close\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
        try (TransferCrypto.SealedOut so = new TransferCrypto.SealedOut(out, au.respKey())) {
            so.write(text.getBytes(StandardCharsets.UTF_8));
        }
        out.flush();
    }

    private void sendFile(OutputStream out, Auth au, File f, long offset) throws IOException {
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nX-Size: " + f.length() + "\r\nX-Offset: " + offset + "\r\n" + au.dkHeader() + "Connection: close\r\n\r\n")
                .getBytes(StandardCharsets.ISO_8859_1));
        long total = offset;
        java.security.MessageDigest md = Checksum.newDigest();   // 邊送邊算整檔校驗碼；送完存起來，對方之後來問就不用再讀一遍（只有從頭送才算得出整檔的）
        final long lenAtStart = f.length(), mtimeAtStart = f.lastModified();
        try (InputStream fin = new FileInputStream(f);
             TransferCrypto.SealedOut so = new TransferCrypto.SealedOut(out, au.respKey())) {
            long skipped = 0;
            while (skipped < offset) {   // 續傳：跳過對方已經有的部分
                long k = fin.skip(offset - skipped);
                if (k <= 0) throw new IOException("cannot seek to " + offset);
                skipped += k;
            }
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = fin.read(buf)) > 0) {
                so.write(buf, 0, n);
                md.update(buf, 0, n);
                total += n;
                host.progress(au.peer, true, f.getName(), total, f.length());
            }
        }
        out.flush();
        if (offset == 0 && total == lenAtStart && f.length() == lenAtStart && f.lastModified() == mtimeAtStart) SENT_SHA.put(f, Checksum.hex(md.digest()));
        host.event(au.peer, "get", rel(f), total - offset);
    }


    /** 對方傳來推播設定（兩個小 JSON，加密傳輸，最多 32 KB）：驗證格式後交給 host 問使用者；接受才存。 */
    private void receiveFcmSetup(InputStream in, OutputStream out, Auth au, String size) throws IOException {
        long expect;
        try { expect = Long.parseLong(size == null ? "" : size.trim()); } catch (Exception e) { expect = -1; }
        if (expect <= 0 || expect > 32 * 1024) { reply(out, 413, "bad size"); return; }
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        try (InputStream body = new TransferCrypto.OpenedIn(in, TransferCrypto.encKey(au.key, au.nonce))) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = body.read(buf)) > 0) {
                bo.write(buf, 0, n);
                if (bo.size() > expect) { reply(out, 413, "larger than declared"); return; }
            }
        }
        if (bo.size() != expect) { reply(out, 400, "size mismatch"); return; }
        String config, key;
        try {
            org.json.JSONObject j = new org.json.JSONObject(bo.toString("UTF-8"));
            config = j.getString("config");
            key = j.getString("key");
        } catch (Exception e) { reply(out, 400, "bad body"); return; }
        if (FcmFiles.classify(config) != FcmFiles.Kind.CONFIG || FcmFiles.classify(key) != FcmFiles.Kind.SERVICE_ACCOUNT
                || FcmFiles.configProblem(config) != null || FcmFiles.mismatch(config, key) != null) { reply(out, 400, "bad files"); return; }
        boolean ok = host.fcmSetupOffer(au.peer, config, key);
        sendText(out, au, ok ? "accepted" : "declined");
    }

    private void receive(InputStream in, OutputStream out, Auth au, String name, String size, String fpParam, String offsetParam) throws IOException {
        long expect;
        try { expect = Long.parseLong(size == null ? "" : size.trim()); } catch (Exception e) { expect = -1; }
        if (expect < 0) { reply(out, 411, "size required"); return; }   // 沒給大小或不是數字：不收（以前沒給大小就跳過空間檢查）
        String base = name == null ? "" : new File(name.replace('\\', '/')).getName();
        if (base.isEmpty() || base.equals("..") || base.equals(".")) { reply(out, 400, "bad name"); return; }
        File inbox = host.inbox();
        if (!inbox.isDirectory() && !inbox.mkdirs()) { reply(out, 500, "no inbox"); return; }
        if (expect > MAX_UPLOAD_BYTES) { reply(out, 413, "too large"); return; }
        if (!insideRoot(host.root(), inbox)) { reply(out, 500, "inbox outside root"); return; }   // 收件匣本身是指到共享根目錄外面的符號連結：不寫
        Resume.purgeOldParts(inbox, System.currentTimeMillis());   // 順便清掉三天前沒人接著傳的半成品

        // 續傳：對方帶了指紋就把半成品留在固定名稱的暫存檔，下次從斷點接著收；沒帶（舊版）就維持原本的隨機暫存檔、失敗就刪
        final boolean resumable = Resume.validFp(fpParam);
        long offset = 0;
        File tmp;
        String activeKey = null;
        if (resumable) {
            tmp = new File(inbox, Resume.partName(au.peer, fpParam));
            if (java.nio.file.Files.isSymbolicLink(tmp.toPath())) { reply(out, 500, "partial file is a symlink"); return; }   // 開檔會跟著連結寫到收件匣以外的地方
            Long stoppedAt = STOPPED.get(au.peer + "|" + fpParam);
            if (stoppedAt != null && System.currentTimeMillis() - stoppedAt < STOPPED_MEMORY_MS) { reply(out, 499, "stopped by receiver"); return; }
            long want = 0;
            try { want = Long.parseLong(offsetParam == null ? "0" : offsetParam.trim()); } catch (Exception e) { want = -1; }
            long have = tmp.isFile() ? tmp.length() : 0;
            if (!Resume.offsetOk(have, want, expect)) { reply(out, 409, "offset mismatch: have " + have); return; }   // 對不上：對方重新問一次斷點再來
            offset = want;
            activeKey = tmp.getName();
            if (!ACTIVE_PARTS.add(activeKey)) { reply(out, 409, "busy: same upload in progress"); return; }
        } else {
            tmp = new File(inbox, "." + java.util.UUID.randomUUID() + ".part");   // 暫存檔用隨機名稱：同名檔案同時上傳不會互相蓋掉
        }
        final long toReceive = expect - offset;   // 這一次還要收多少（只預留這一段的空間）
        if (!RESERVER.tryReserve(inbox.getUsableSpace(), toReceive, KEEP_FREE_BYTES)) {   // 預留空間：同時好幾個上傳各自看到「夠用」、一起寫滿的情形，要把已答應的量算進去
            if (activeKey != null) ACTIVE_PARTS.remove(activeKey);
            reply(out, 507, "not enough space");
            return;
        }
        long total = offset;
        boolean keepPart = resumable;   // 斷線這類可以接著傳的失敗，半成品留著；大小對不上、改名失敗這類才刪
        File finalTmp = tmp;
        try {   // 不管成功、失敗、被中斷（含 Error），預留的空間與暫存檔一定要處理掉
            java.security.MessageDigest md = Checksum.newDigest();
            if (offset > 0) {   // 整檔校驗碼要涵蓋已經收下的那一段：把它讀一遍算進去
                try (InputStream old = new FileInputStream(tmp)) {
                    byte[] b = new byte[64 * 1024];
                    long left = offset;
                    int n;
                    while (left > 0 && (n = old.read(b, 0, (int) Math.min(b.length, left))) > 0) { md.update(b, 0, n); left -= n; }
                    if (left > 0) { keepPart = false; throw new IOException("partial file shorter than expected"); }
                }
            }
            try (InputStream body = new TransferCrypto.OpenedIn(in, TransferCrypto.encKey(au.key, au.nonce));
                 FileOutputStream fo = new FileOutputStream(tmp, offset > 0)) {
                byte[] buf = new byte[32 * 1024];
                int n;
                while ((n = body.read(buf)) > 0) {
                    fo.write(buf, 0, n);
                    md.update(buf, 0, n);
                    total += n;
                    if (total > MAX_UPLOAD_BYTES || total > expect) { keepPart = false; throw new IOException("upload larger than declared"); }   // 超過上限或比宣告的大小多一個位元組：中止
                    if ((total & 0x3FFFFF) < n && inbox.getUsableSpace() < KEEP_FREE_BYTES / 2) throw new IOException("out of space");   // 每約 4MB 看一次剩餘空間
                    host.progress(au.peer, false, base, total, expect);
                }
            }
            if (total != expect) throw new IOException("upload size mismatch: declared " + expect + ", got " + total);   // 提早結束（對方斷線）：半成品留著給續傳；大小多出來的上面已經處理
            File dest;
            String finalSha;
            synchronized (RENAME_LOCK) {   // 取不重複的檔名與改名要一起做，兩個同名上傳才不會搶到同一個名字
                dest = unique(inbox, base);
                if (!tmp.renameTo(dest)) { keepPart = false; reply(out, 500, "rename failed"); return; }
                tmp = null;   // 已經改名成正式檔案，不要在 finally 刪掉
                finalSha = Checksum.hex(md.digest());
                if (resumable) COMPLETED.put(au.peer + "|" + fpParam, new Done(dest.getName(), total, finalSha, System.currentTimeMillis()));
            }
            host.event(au.peer, "put", rel(dest), total - offset);
            sendText(out, au, "saved\t" + dest.getName() + "\t" + total + "\t" + finalSha);
        } catch (Aborted a) {   // 這一端的使用者按了停止／暫停：明確回覆（對方才不會當成斷線一直重試）；停止就把半成品刪掉
            keepPart = a.keepPartial;
            if (!a.keepPartial && resumable) { pruneStopped(STOPPED, System.currentTimeMillis()); STOPPED.put(au.peer + "|" + fpParam, System.currentTimeMillis()); }
            try { reply(out, 499, a.getMessage()); } catch (IOException ignored) { }
        } catch (IOException e) {
            if (!keepPart) host.serverError(au.peer, L.t("收「") + base + L.t("」時出錯：") + e.getMessage());   // 留記錄，也盡量告訴對方原因（斷線這種之後會續傳的不算錯誤）
            try { reply(out, 500, String.valueOf(e.getMessage())); } catch (IOException ignored) { }
        } finally {
            if (tmp != null && !keepPart) tmp.delete();   // 不能續傳的失敗：未完成的檔案刪掉
            RESERVER.release(toReceive);
            if (activeKey != null) ACTIVE_PARTS.remove(activeKey);
        }
    }

    /** 進行中的上傳「已答應」的空間總量；判斷剩餘空間夠不夠時要先扣掉它。純邏輯，可單元測試。 */
    static final class SpaceReserver {
        private long reserved;

        synchronized boolean tryReserve(long usable, long expect, long keepFree) {
            if (expect < 0 || usable - reserved - expect < keepFree) return false;
            reserved += expect;
            return true;
        }

        synchronized void release(long n) { reserved = Math.max(0, reserved - n); }

        synchronized long reserved() { return reserved; }
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

    private String list(File dir) { return listIn(host.root(), dir); }

    /**
     * 目錄內容：符號連結與指到共享根目錄以外的項目不列出（不然會洩漏外面的檔名與絕對路徑）。
     * 每個項目只讀一次檔案屬性（以前每項要查十幾次，手機的共用儲存空間很慢，一個資料夾要好幾百毫秒）；
     * 資料夾本身已經確認在根目錄之內，所以裡面不是符號連結的項目一定也在裡面，不用逐項解析真實路徑。
     */
    static String listIn(File rootDir, File dir) {
        File[] all = dir.listFiles();
        if (all == null) return "";
        String relDir;
        try {
            String root = rootDir.getCanonicalPath(), p = dir.getCanonicalPath();
            if (p.equals(root)) relDir = "";
            else if (p.startsWith(root + File.separator)) relDir = p.substring(root.length() + 1) + File.separator;
            else return "";   // 資料夾在根目錄之外：不列
        } catch (IOException e) {
            return "";
        }
        final class Item {
            final String name;
            final boolean dir;
            final long size, mtime;
            Item(String name, boolean dir, long size, long mtime) { this.name = name; this.dir = dir; this.size = size; this.mtime = mtime; }
        }
        List<Item> items = new ArrayList<>();
        for (File k : all) {
            try {
                java.nio.file.attribute.BasicFileAttributes a = java.nio.file.Files.readAttributes(k.toPath(), java.nio.file.attribute.BasicFileAttributes.class, java.nio.file.LinkOption.NOFOLLOW_LINKS);
                if (a.isSymbolicLink()) continue;
                items.add(new Item(k.getName(), a.isDirectory(), a.isRegularFile() ? a.size() : 0, a.lastModifiedTime().toMillis()));
            } catch (IOException | RuntimeException e) {
                // 讀不到屬性的項目（剛被刪掉、沒權限）就略過
            }
        }
        Collections.sort(items, (x, y) -> x.dir != y.dir ? (x.dir ? -1 : 1) : x.name.compareToIgnoreCase(y.name));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size() && i < 500; i++) {
            Item it = items.get(i);
            sb.append(it.dir ? "D" : "F").append('\t').append(it.size).append('\t').append(it.mtime).append('\t').append(relDir).append(it.name).append('\n');
        }
        return sb.toString();
    }

    /** f 的真實路徑還在 rootDir 之內（含 rootDir 本身）。 */
    static boolean insideRoot(File rootDir, File f) {
        try {
            String root = rootDir.getCanonicalPath(), p = f.getCanonicalPath();
            return p.equals(root) || p.startsWith(root + File.separator);
        } catch (IOException e) {
            return false;
        }
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
                if (java.nio.file.Files.isSymbolicLink(k.toPath())) continue;   // 符號連結不跟、不列出（可能指到共享根目錄以外）
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
        reply(out, 200, "id=" + host.myId() + "\nname=" + host.myName().replace('\n', ' ').replace('\r', ' ') + "\npub=" + TransferCrypto.hex(myPub));   // 名稱裡的換行會被當成另一行（例如假的 pub=）
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
