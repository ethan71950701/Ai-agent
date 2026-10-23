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
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.HashMap;
import java.util.Map;

/**
 * 裝置之間傳檔案的客戶端（純 Java）：對另一台已配對裝置的 TransferServer 發請求。
 * 請求簽章與內容加密的做法見 TransferCrypto；呼叫都是同步的，要在背景執行緒用。
 */
final class TransferClient {
    interface Progress { void onBytes(long done, long total); }

    private final String ip;
    private final int port;
    private final String myId;
    private final byte[] key;

    TransferClient(String ip, int port, String myId, byte[] key) {
        this.ip = ip;
        this.port = port;
        this.myId = myId;
        this.key = key;
    }

    private Socket open() throws IOException {
        Socket s = new Socket();
        s.connect(new InetSocketAddress(ip, port), 4000);
        s.setSoTimeout(20000);
        return s;
    }

    /** 寫出帶簽章的請求標頭；回傳這個請求的隨機數（內容加密金鑰由它衍生）。 */
    private String writeHead(OutputStream out, String method, String target) throws IOException {
        String ts = String.valueOf(System.currentTimeMillis() / 1000), nonce = TransferCrypto.hex(TransferCrypto.random(12));
        String sig = TransferCrypto.sign(key, method, target, ts, nonce);
        out.write((method + " " + target + " HTTP/1.1\r\nHost: phoneai\r\nX-Id: " + myId + "\r\nX-Ts: " + ts + "\r\nX-Nonce: " + nonce
                + "\r\nX-Sig: " + sig + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
        return nonce;
    }

    /** 讀回應標頭；狀態不是 200 就把內容當錯誤訊息丟出去。 */
    private static Map<String, String> readStatus(InputStream in) throws IOException {
        String status = TransferServer.readLine(in);
        if (status == null) throw new IOException("對方沒有回應");
        String[] p = status.split(" ");
        int code = p.length > 1 ? Integer.parseInt(p[1]) : 0;
        Map<String, String> h = new HashMap<>();
        for (;;) {
            String l = TransferServer.readLine(in);
            if (l == null || l.isEmpty()) break;
            int c = l.indexOf(':');
            if (c > 0) h.put(l.substring(0, c).trim().toLowerCase(), l.substring(c + 1).trim());
        }
        if (code != 200) {
            int len = 0;
            try { len = Integer.parseInt(h.get("content-length")); } catch (Exception ignored) { }
            byte[] b = new byte[Math.min(len, 1024)];
            int got = 0;
            while (got < b.length) { int r = in.read(b, got, b.length - got); if (r < 0) break; got += r; }
            throw new IOException(code + " " + new String(b, 0, got, StandardCharsets.UTF_8));
        }
        return h;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        return bo.toByteArray();
    }

    private static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    /** 回傳是對方的裝置名稱。 */
    String ping() throws IOException {
        String t = getText("/v1/ping");
        return t.startsWith("ok\t") ? t.substring(3) : t;
    }

    String list(String path) throws IOException { return getText("/v1/list?path=" + enc(path)); }

    String find(String name) throws IOException { return getText("/v1/find?name=" + enc(name)); }

    private String getText(String target) throws IOException {
        try (Socket s = open()) {
            OutputStream out = s.getOutputStream();
            String nonce = writeHead(out, "GET", target);
            out.flush();
            InputStream in = new BufferedInputStream(s.getInputStream());
            readStatus(in);
            try (InputStream body = new TransferCrypto.OpenedIn(in, TransferCrypto.encKey(key, nonce))) {
                return new String(readAll(body), StandardCharsets.UTF_8);
            }
        }
    }

    /** 從對方取一個檔案，存到 dest（先寫 .part，完整收到才改名）。回傳位元組數。 */
    long download(String remotePath, File dest, Progress cb) throws IOException {
        String target = "/v1/file?path=" + enc(remotePath);
        File tmp = new File(dest.getParentFile(), "." + dest.getName() + ".part");
        try (Socket s = open()) {
            OutputStream out = s.getOutputStream();
            String nonce = writeHead(out, "GET", target);
            out.flush();
            InputStream in = new BufferedInputStream(s.getInputStream());
            Map<String, String> h = readStatus(in);
            long total = 0;
            try { total = Long.parseLong(h.get("x-size")); } catch (Exception ignored) { }
            long done = 0;
            try (InputStream body = new TransferCrypto.OpenedIn(in, TransferCrypto.encKey(key, nonce));
                 FileOutputStream fo = new FileOutputStream(tmp)) {
                byte[] buf = new byte[32 * 1024];
                int n;
                while ((n = body.read(buf)) > 0) {
                    fo.write(buf, 0, n);
                    done += n;
                    if (cb != null) cb.onBytes(done, total);
                }
            }
            if (!tmp.renameTo(dest)) throw new IOException("無法存成 " + dest.getName());
            return done;
        } catch (IOException e) {
            tmp.delete();
            throw e;
        }
    }

    /** 把本機的檔案送到對方的收件匣。回傳對方存成的檔名。 */
    String put(File src, Progress cb) throws IOException {
        String target = "/v1/put?name=" + enc(src.getName());
        try (Socket s = open()) {
            OutputStream out = new BufferedOutputStream(s.getOutputStream());
            String nonce = writeHead(out, "PUT", target);
            long total = src.length(), done = 0;
            try (TransferCrypto.SealedOut so = new TransferCrypto.SealedOut(out, TransferCrypto.encKey(key, nonce));
                 InputStream fin = new FileInputStream(src)) {
                byte[] buf = new byte[32 * 1024];
                int n;
                while ((n = fin.read(buf)) > 0) {
                    so.write(buf, 0, n);
                    done += n;
                    if (cb != null) cb.onBytes(done, total);
                }
            }
            out.flush();
            InputStream in = new BufferedInputStream(s.getInputStream());
            readStatus(in);
            try (InputStream body = new TransferCrypto.OpenedIn(in, TransferCrypto.encKey(key, nonce))) {
                String[] p = new String(readAll(body), StandardCharsets.UTF_8).split("\t");
                return p.length > 1 ? p[1] : src.getName();
            }
        }
    }

    // ---------- 配對（還沒有金鑰：用明文端點，安全靠確認碼由使用者核對） ----------

    static final class Offer {
        String peerId, peerName, sas;
        byte[] key;
    }

    private static String[] plain(String ip, int port, String method, String target, byte[] body) throws IOException {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(ip, port), 4000);
            s.setSoTimeout(15000);
            OutputStream out = new BufferedOutputStream(s.getOutputStream());
            out.write((method + " " + target + " HTTP/1.1\r\nHost: phoneai\r\nContent-Length: " + (body == null ? 0 : body.length)
                    + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
            if (body != null) out.write(body);
            out.flush();
            InputStream in = new BufferedInputStream(s.getInputStream());
            String status = TransferServer.readLine(in);
            if (status == null) throw new IOException("對方沒有回應");
            int code = Integer.parseInt(status.split(" ")[1]);
            for (;;) {
                String l = TransferServer.readLine(in);
                if (l == null || l.isEmpty()) break;
            }
            return new String[]{String.valueOf(code), new String(readAll(in), StandardCharsets.UTF_8)};
        }
    }

    /** 發起配對：送出自己的暫時公開金鑰，算出確認碼與長期金鑰（長期金鑰要等使用者核對確認碼相符才存）。 */
    static Offer pairStart(String ip, int port, String myId, String myName) throws Exception {
        KeyPair mine = TransferCrypto.newPair();
        byte[] myPub = mine.getPublic().getEncoded();
        String body = "id=" + myId + "\nname=" + myName.replace('\n', ' ') + "\npub=" + TransferCrypto.hex(myPub);
        String[] r = plain(ip, port, "POST", "/v1/pair/start", body.getBytes(StandardCharsets.UTF_8));
        if (!"200".equals(r[0])) throw new IOException(r[0] + " " + r[1]);
        Map<String, String> m = TransferServer.parseLines(r[1]);
        byte[] theirPub = TransferCrypto.unhex(m.get("pub"));
        byte[] shared = TransferCrypto.shared(mine.getPrivate(), theirPub);
        String tr = TransferCrypto.transcript(myId, myPub, m.get("id"), theirPub);
        Offer o = new Offer();
        o.peerId = m.get("id");
        o.peerName = m.get("name");
        o.key = TransferCrypto.pairKey(shared, tr);
        o.sas = TransferCrypto.sas(shared, tr);
        return o;
    }

    /** 問對方有沒有接受：waiting／accepted／rejected／none。 */
    static String pairStatus(String ip, int port, String myId) throws IOException {
        String[] r = plain(ip, port, "GET", "/v1/pair/status?id=" + enc(myId), null);
        return "200".equals(r[0]) ? r[1].trim() : "none";
    }
}
