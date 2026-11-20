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

    /** 最近一次傳輸（取檔或送檔）的整檔校驗碼；對方不支援校驗時 verified 是 false。 */
    volatile String lastSha = "";
    volatile boolean lastVerified;

    private final String ip;
    private final int port;
    private final String myId;
    private final byte[] key;
    private final String myName;

    TransferClient(String ip, int port, String myId, String myName, byte[] key) {
        this.ip = ip;
        this.port = port;
        this.myId = myId;
        this.myName = myName;
        this.key = key;
    }

    private Socket open() throws IOException {
        Socket s = new Socket();
        s.connect(new InetSocketAddress(ip, port), 4000);
        s.setSoTimeout(20000);
        return s;
    }

    /** 本機在 Tailscale（100.64.0.0/10）裡的位址；沒有就回 null。 */
    static String localTailnetIp() {
        try {
            for (java.net.NetworkInterface ni : java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp()) continue;
                for (java.net.InetAddress a : java.util.Collections.list(ni.getInetAddresses())) {
                    if (TransferServer.isTailnetAddr(a)) return a.getHostAddress();
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    /** 寫出帶簽章的請求標頭；回傳這個請求的隨機數（內容加密金鑰由它衍生）。 */
    private String writeHead(OutputStream out, String method, String target) throws IOException {
        String ts = String.valueOf(System.currentTimeMillis() / 1000), nonce = TransferCrypto.hex(TransferCrypto.random(12));
        String sig = TransferCrypto.sign(key, method, target, ts, nonce);
        String myTs = localTailnetIp();   // 自己的 Tailscale 位址（有開 Tailscale 才有）：對方記下來，換網路後也連得回來
        out.write((method + " " + target + " HTTP/1.1\r\nHost: phoneai\r\nX-Id: " + myId + "\r\nX-Ts: " + ts + "\r\nX-Nonce: " + nonce
                + "\r\nX-Name: " + enc(myName) + "\r\nX-Sig: " + sig
                + (myTs == null ? "" : "\r\nX-Tailnet: " + myTs) + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
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

    /** 把我的推播 token 交給對方（對方存起來，之後才叫得醒我）。 */
    String sendFcmToken(String token) throws IOException { return getText("/v1/fcm?token=" + enc(token)); }

    /** 互換 token：送出我的（沒有就送空的，只是拿對方的），回傳對方的 token；對方沒有或格式不對回 null。 */
    String exchangeFcmToken(String mine) throws IOException {
        String r = getText("/v1/fcm?token=" + enc(mine == null ? "" : mine));
        int tab = r.indexOf('\t');
        String theirs = tab >= 0 ? r.substring(tab + 1).trim() : "";
        return Doorbell.validToken(theirs) ? theirs : null;
    }

    /** 傳之前先問對方收不收得下（空間與單檔上限）。收不下回原因；收得下、或對方是舊版沒有這個端點、或問不到，回 null。 */
    String spaceProblem(long size) {
        try {
            long[] v = SpaceCheck.parse(getText("/v1/space"));
            return v == null ? null : SpaceCheck.problem(size, v[0], v[1], v[2]);
        } catch (IOException e) {
            return null;
        }
    }

    /** 向對方要它剛送出的那個檔案的整檔校驗碼；對方沒有現成的又太大時回空字串。 */
    String remoteSha(String path) throws IOException {
        String r = getText("/v1/sha?path=" + enc(path)).trim();
        return r.matches("[0-9a-fA-F]{64}") ? r : "";
    }

    /** 把推播設定（設定檔＋發送金鑰）傳給對方，等對方的使用者按接受或拒絕（最多等 90 秒）。回傳 true＝接受。 */
    boolean sendFcmSetup(String configJson, String keyJson) throws IOException {
        final byte[] body;
        try {
            body = new org.json.JSONObject().put("config", configJson).put("key", keyJson).toString().getBytes(StandardCharsets.UTF_8);
        } catch (org.json.JSONException e) {
            throw new IOException(e);
        }
        String target = "/v1/fcmsetup?size=" + body.length;
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(ip, port), 4000);
            s.setSoTimeout(90000);
            OutputStream out = new BufferedOutputStream(s.getOutputStream());
            String nonce = writeHead(out, "PUT", target);
            try (TransferCrypto.SealedOut so = new TransferCrypto.SealedOut(out, TransferCrypto.encKey(key, nonce))) {
                so.write(body);
            }
            out.flush();
            InputStream in = new BufferedInputStream(s.getInputStream());
            readStatus(in);
            try (InputStream r = new TransferCrypto.OpenedIn(in, TransferCrypto.encKey(key, nonce))) {
                return "accepted".equals(new String(readAll(r), StandardCharsets.UTF_8).trim());
            }
        }
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

    /** 半成品的檔名（固定，下次才找得到）。 */
    static File partOf(File dest) { return new File(dest.getParentFile(), "." + dest.getName() + ".part"); }

    /**
     * 從對方取一個檔案，存到 dest：先寫 .part，完整收到並核對校驗碼才改名。傳到一半斷線會自動接著收（最多 MAX_ATTEMPTS 次），
     * 全部失敗時 .part 還在，之後再取同一個檔案會從斷點繼續。回傳這個檔案的總位元組數。
     */
    long download(String remotePath, File dest, Progress cb) throws IOException {
        try {
            long n = downloadWithRetries(remotePath, dest, tracked(cb));
            trackEnd(false);
            return n;
        } catch (TransferNotify.Stopped stop) {
            partOf(dest).delete();   // 使用者手動停止：已收到的也刪掉（意外斷線才保留續傳）
            trackEnd(false);
            throw stop;
        } catch (TransferNotify.Paused pz) {
            trackEnd(true);   // 使用者按「暫停」：半成品保留
            throw pz;
        } catch (IOException e) {
            trackEnd(!String.valueOf(e.getMessage()).startsWith("校驗碼不符"));   // 內容壞掉的半成品已經刪了，不用留；其他（斷線）留著讓使用者按「繼續」
            throw e;
        }
    }

    private long downloadWithRetries(String remotePath, File dest, Progress cb) throws IOException {
        IOException last = null;
        for (int attempt = 1; attempt <= Resume.MAX_ATTEMPTS; attempt++) {
            if (TransferNotify.pauseRequested()) throw new TransferNotify.Paused();
            try {
                return downloadOnce(remotePath, dest, cb);
            } catch (IOException e) {
                last = e;
                if (!Resume.retryable(e)) throw e;
                if (attempt == Resume.MAX_ATTEMPTS) break;
                try { Thread.sleep(Resume.backoffMs(attempt)); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw e; }
            }
        }
        throw new IOException("取檔到一半斷線，已自動重試 " + Resume.MAX_ATTEMPTS + " 次還是不行：" + (last == null ? "" : last.getMessage())
                + "（已收到的部分有保留，再取一次同一個檔案會從斷點繼續）");
    }

    private long downloadOnce(String remotePath, File dest, Progress cb) throws IOException {
        File tmp = partOf(dest);
        long have = tmp.isFile() ? tmp.length() : 0;
        String target = "/v1/file?path=" + enc(remotePath) + (have > 0 ? "&offset=" + have : "");
        boolean ok = false;
        try (Socket s = open()) {
            OutputStream out = s.getOutputStream();
            String nonce = writeHead(out, "GET", target);
            out.flush();
            InputStream in = new BufferedInputStream(s.getInputStream());
            Map<String, String> h = readStatus(in);
            long total = 0, start = 0;
            try { total = Long.parseLong(h.get("x-size")); } catch (Exception ignored) { }
            try { start = Long.parseLong(h.get("x-offset")); } catch (Exception ignored) { }   // 對方是舊版沒回這個：當作從頭送
            if (start != have) have = 0;   // 對方沒照我們要的起點送（舊版、或檔案變了）：整個重來
            java.security.MessageDigest md = Checksum.newDigest();
            if (have > 0) {   // 整檔校驗碼要涵蓋已經收下的那一段
                try (InputStream old = new FileInputStream(tmp)) {
                    byte[] b = new byte[64 * 1024];
                    long left = have;
                    int n;
                    while (left > 0 && (n = old.read(b, 0, (int) Math.min(b.length, left))) > 0) { md.update(b, 0, n); left -= n; }
                    if (left > 0) have = 0;
                }
                if (have == 0) md = Checksum.newDigest();
            }
            long done = have;
            if (cb != null) cb.onBytes(done, total);
            try (InputStream body = new TransferCrypto.OpenedIn(in, TransferCrypto.encKey(key, nonce));
                 FileOutputStream fo = new FileOutputStream(tmp, have > 0)) {
                byte[] buf = new byte[32 * 1024];
                int n;
                while ((n = body.read(buf)) > 0) {
                    fo.write(buf, 0, n);
                    md.update(buf, 0, n);
                    done += n;
                    if (cb != null) cb.onBytes(done, total);
                }
            }
            if (total > 0 && done != total) throw new java.io.EOFException("download ended early: " + done + " of " + total);   // 對方中途斷線：半成品留著，之後接著收
            final String mine = Checksum.hex(md.digest());
            String theirs = "";
            try { theirs = remoteSha(remotePath); } catch (IOException ignored) { }   // 對方是舊版（沒有這個端點）或不給：略過校驗
            lastSha = mine;
            lastVerified = !theirs.isEmpty();
            if (lastVerified && !Checksum.same(mine, theirs)) throw new IOException("校驗碼不符：收到的檔案和對方送出的不一樣（已丟棄）");
            if (!tmp.renameTo(dest)) throw new IOException("無法存成 " + dest.getName());
            ok = true;
            return done;
        } catch (IOException e) {
            String m = String.valueOf(e.getMessage());
            if (m.startsWith("校驗碼不符") || m.startsWith("無法存成")) tmp.delete();   // 內容不對或改名失敗：半成品沒用了。其他（斷線等）留著續傳
            throw e;
        }
    }

    /** 可以重新打開的來源（續傳要從頭或中間再讀一次）。 */
    interface Source { InputStream open() throws IOException; }

    /** 請對方把這個指紋的半成品刪掉（使用者手動停止、或按「丟掉」）。盡力而為：連不上或對方是舊版就算了。 */
    void discardRemote(String fp) {
        try { getText("/v1/discard?fp=" + enc(fp)); } catch (Exception ignored) { }
    }

    // ---- 「沒傳完的檔案」清單：呼叫端用 track() 登記這一次傳輸；成功或手動停止就移除，意外失敗就留著讓使用者按「繼續」
    private PendingStore pend;
    private PendingStore.Entry pendEntry;
    private volatile long pendDone, pendTotal;

    void track(PendingStore store, PendingStore.Entry e) {
        pend = store;
        pendEntry = e;
        pendDone = 0;
        pendTotal = e.size;
        store.begin(e);
    }

    private Progress tracked(final Progress cb) {
        if (pend == null) return cb;
        final PendingStore st = pend;
        final String id = pendEntry.id;
        return (d, t) -> {
            pendDone = d;
            pendTotal = t;
            st.progress(id, d, t, lastFp, System.currentTimeMillis(), false);
            if (cb != null) cb.onBytes(d, t);
        };
    }

    /** 這次傳輸在「沒傳完」清單裡的編號；沒登記回 null。 */
    String trackedId() { return pendEntry == null ? null : pendEntry.id; }

    private void trackEnd(boolean keep) {
        if (pend == null) return;
        if (keep) pend.progress(pendEntry.id, pendDone, pendTotal, lastFp, System.currentTimeMillis(), true);
        else pend.remove(pendEntry.id);
    }

    /** 最近一次可續傳的傳送用的指紋（呼叫端要記進「沒傳完」清單，之後才能請對方丟掉半成品）。 */
    volatile String lastFp = "";

    /** 對方的半成品現在收了幾個位元組；問不到（舊版對方、網路問題）就當 0，從頭來。 */
    long remoteOffset(String fp) {
        try {
            return Math.max(0, Long.parseLong(getText("/v1/offset?fp=" + enc(fp)).trim()));
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 送檔案，傳到一半斷線會自動接著傳（對方保留半成品，這邊問斷點再從那裡繼續）：最多重試 MAX_ATTEMPTS 次，
     * 全部失敗時對方的半成品還在，使用者再傳同一個檔案會從斷點繼續。大小不明（≤0）就沒辦法續傳，走一般的單次傳送。
     */
    String putResumable(Source src, long size, String name, Progress cb) throws IOException {
        if (size <= 0) return put(src.open(), size, name, cb);
        String fp;
        try (InputStream in = src.open()) {
            byte[] head = new byte[(int) Math.min(Resume.HEAD_BYTES, size)];
            int got = 0, r;
            while (got < head.length && (r = in.read(head, got, head.length - got)) > 0) got += r;
            fp = Resume.fingerprint(head, got, size, name);
        }
        lastFp = fp;
        try {
            String r = putWithRetries(src, size, name, fp, tracked(cb));
            trackEnd(false);
            return r;
        } catch (TransferNotify.Stopped stop) {
            discardRemote(fp);   // 使用者手動停止：對方收到一半的也刪掉（意外斷線才保留續傳）
            trackEnd(false);
            throw stop;
        } catch (TransferNotify.Paused pz) {
            trackEnd(true);   // 使用者按「暫停」：對方的半成品與清單都保留
            throw pz;
        } catch (IOException e) {
            trackEnd(true);   // 意外失敗：留在「沒傳完」清單，使用者可以按「繼續」
            throw e;
        }
    }

    private String putWithRetries(Source src, long size, String name, String fp, Progress cb) throws IOException {
        IOException last = null;
        for (int attempt = 1; attempt <= Resume.MAX_ATTEMPTS; attempt++) {
            if (TransferNotify.pauseRequested()) throw new TransferNotify.Paused();   // 重試等待中按了暫停
            long offset = remoteOffset(fp);
            if (offset > size) offset = 0;
            String why = spaceProblem(size - offset);   // 只要看「還要收的那一段」
            if (why != null) throw new IOException(why);
            try {
                return putOnce(src, size, name, fp, offset, cb);
            } catch (IOException e) {
                last = e;
                boolean mismatch = e.getMessage() != null && e.getMessage().contains("offset mismatch");
                if (!Resume.retryable(e) && !mismatch) throw e;
                if (attempt == Resume.MAX_ATTEMPTS) break;
                try { Thread.sleep(Resume.backoffMs(attempt)); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw e; }
            }
        }
        throw new IOException("傳到一半斷線，已自動重試 " + Resume.MAX_ATTEMPTS + " 次還是不行：" + (last == null ? "" : last.getMessage())
                + "（對方已收下的部分有保留，再傳一次同一個檔案會從斷點繼續）");
    }

    private String putOnce(Source srcOpen, long size, String name, String fp, long offset, Progress cb) throws IOException {
        String target = "/v1/put?name=" + enc(name) + "&size=" + size + "&fp=" + fp + "&offset=" + offset;
        try (Socket s = open()) {
            OutputStream out = new BufferedOutputStream(s.getOutputStream());
            String nonce = writeHead(out, "PUT", target);
            long done = offset;
            java.security.MessageDigest md = Checksum.newDigest();
            try (TransferCrypto.SealedOut so = new TransferCrypto.SealedOut(out, TransferCrypto.encKey(key, nonce));
                 InputStream src = srcOpen.open()) {
                byte[] buf = new byte[32 * 1024];
                long left = offset;
                while (left > 0) {   // 對方已經有的部分：不再傳，但整檔校驗碼要算進去
                    int n = src.read(buf, 0, (int) Math.min(buf.length, left));
                    if (n < 0) throw new IOException("來源檔案比對方已收到的部分還短（檔案被改過？）");
                    md.update(buf, 0, n);
                    left -= n;
                }
                if (cb != null) cb.onBytes(done, size);
                int n;
                while ((n = src.read(buf)) > 0) {
                    so.write(buf, 0, n);
                    md.update(buf, 0, n);
                    done += n;
                    if (cb != null) cb.onBytes(done, size);
                }
            }
            out.flush();
            InputStream in = new BufferedInputStream(s.getInputStream());
            readStatus(in);
            try (InputStream body = new TransferCrypto.OpenedIn(in, TransferCrypto.encKey(key, nonce))) {
                String[] p = new String(readAll(body), StandardCharsets.UTF_8).split("\t");
                lastSha = Checksum.hex(md.digest());
                lastVerified = p.length > 3 && !p[3].trim().isEmpty();
                if (lastVerified && !Checksum.same(lastSha, p[3].trim()))
                    throw new IOException("校驗碼不符：對方存下的檔案和送出的不一樣（請重傳，必要時刪掉對方收件匣裡的那個檔案）");
                return p.length > 1 ? p[1] : name;
            }
        }
    }

    /** 把本機的檔案送到對方的收件匣。回傳對方存成的檔名。 */
    String put(File src, Progress cb) throws IOException {
        return put(new FileInputStream(src), src.length(), src.getName(), cb);
    }

    /** 從任何輸入串流送出（例如手機選到的 content:// 檔案）；size 是總位元組數，name 是檔名。會關閉 fin。 */
    String put(InputStream fin, long size, String name, Progress cb) throws IOException {
        String why = spaceProblem(size);   // 對方收不下就直接說原因（以前傳到一半被掛斷，只看到 Connection reset）
        if (why != null) { try { fin.close(); } catch (IOException ignored) { } throw new IOException(why); }
        String target = "/v1/put?name=" + enc(name) + "&size=" + size;
        try (Socket s = open()) {
            OutputStream out = new BufferedOutputStream(s.getOutputStream());
            String nonce = writeHead(out, "PUT", target);
            long total = size, done = 0;
            java.security.MessageDigest md = Checksum.newDigest();
            try (TransferCrypto.SealedOut so = new TransferCrypto.SealedOut(out, TransferCrypto.encKey(key, nonce));
                 InputStream src = fin) {
                byte[] buf = new byte[32 * 1024];
                int n;
                while ((n = src.read(buf)) > 0) {
                    so.write(buf, 0, n);
                    md.update(buf, 0, n);
                    done += n;
                    if (cb != null) cb.onBytes(done, total);
                }
            }
            out.flush();
            InputStream in = new BufferedInputStream(s.getInputStream());
            readStatus(in);
            try (InputStream body = new TransferCrypto.OpenedIn(in, TransferCrypto.encKey(key, nonce))) {
                String[] p = new String(readAll(body), StandardCharsets.UTF_8).split("\t");
                lastSha = Checksum.hex(md.digest());
                lastVerified = p.length > 3 && !p[3].trim().isEmpty();
                if (lastVerified && !Checksum.same(lastSha, p[3].trim()))
                    throw new IOException("校驗碼不符：對方存下的檔案和送出的不一樣（請重傳，必要時刪掉對方收件匣裡的那個檔案）");
                return p.length > 1 ? p[1] : name;
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
