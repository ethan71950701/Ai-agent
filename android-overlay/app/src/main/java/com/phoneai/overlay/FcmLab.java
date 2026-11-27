package com.phoneai.overlay;

import android.content.Context;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * 實驗（不在正式版）：用 FCM 高優先級推播叫醒沒開著的 App。
 * 設定檔（google-services.json）在執行時由使用者選，存在 App 私有資料夾，不進 repo；沒有 google-services 外掛。
 * 發送端用服務帳戶金鑰（只放私有資料夾，權限 600，用完要刪）簽 JWT 換 access token，再呼叫 FCM HTTP v1。
 */
final class FcmLab {
    private FcmLab() { }

    static final String CONFIG = "fcm_google_services.json";
    static final String SENDER = "fcm_service_account.json";

    static File file(Context c, String name) { return new File(c.getFilesDir(), name); }

    private static android.content.SharedPreferences prefs(Context c) { return c.getSharedPreferences("cfg", Context.MODE_PRIVATE); }

    /** 記下最近一次收到推播的時間，以及它從送出到收到花的時間（兩支手機的時鐘有差的話會有誤差）。 */
    static void noteReceived(Context c, java.util.Map<String, String> data) {
        try {
            long sent = 0;
            String v = data.get("ts") != null ? data.get("ts") : data.get("sent");
            if (v != null && v.matches("\\d{1,15}")) sent = Long.parseLong(v);
            long now = System.currentTimeMillis();
            prefs(c).edit().putLong("fcm_last_rx_at", now).putLong("fcm_last_rx_delay", sent > 0 ? now - sent : -1).apply();
        } catch (Throwable ignored) { }
    }

    /** 給設定頁看的一行：最近一次收到推播的時間與延遲；沒收過回空字串。 */
    static String lastReceivedText(Context c) {
        long at = prefs(c).getLong("fcm_last_rx_at", 0), d = prefs(c).getLong("fcm_last_rx_delay", -1);
        if (at == 0) return "";
        String when = new java.text.SimpleDateFormat("MM/dd HH:mm:ss", java.util.Locale.US).format(new java.util.Date(at));
        String delay = d < 0 ? "?" : d < 10_000 ? d + " ms" : (d / 1000) + " 秒" + (d > 30_000 ? "（很慢）" : "");
        return when + "　延遲 " + delay;
    }

    /** 這支手機的推播 token（上次取得並存起來的）；沒有回 null。 */
    static String myToken(Context c) {
        String t = prefs(c).getString("fcm_my_token", "");
        return Doorbell.validToken(t) ? t : null;
    }

    static void saveMyToken(Context c, String token) {
        if (Doorbell.validToken(token)) prefs(c).edit().putString("fcm_my_token", token).apply();
    }

    /** 向 Firebase 要一次最新的 token 並存起來（要先 init 成功）。非同步，不阻塞。 */
    static void refreshMyToken(Context c) {
        try {
            if (init(c) != null) return;
            final Context app = c.getApplicationContext();
            com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken().addOnSuccessListener(t -> saveMyToken(app, t));
        } catch (Throwable ignored) { }
    }

    private static final java.util.Map<String, Long> LAST_AUTO_EXCHANGE = new java.util.HashMap<>();

    /** 收到的對方 token 要不要換掉我們存的：格式正確、而且和存的不一樣（對方換過 token，舊的叫不醒它了）。 */
    static boolean shouldAdopt(String stored, String received) {
        return Doorbell.validToken(received) && !received.equals(stored);
    }

    /** 距離上次互換 token 超過 10 分鐘（或從沒換過）才再換一次，避免每次連線都換。 */
    static boolean exchangeDue(Long lastMs, long nowMs) { return lastMs == null || nowMs - lastMs >= 600_000L; }

    /** 對方連進來我們的伺服器（已驗證）：反過來也順便跟它互換一次 token，這樣不論誰先連誰，下次連線雙方的 token 都會補齊、更新。 */
    static void healOnInbound(Context c, String peerId, String ip) {
        try {
            for (TransferStore.Peer p : TransferStore.peers(c)) {
                if (!p.id.equals(peerId)) continue;
                healTokenAsync(c, p, new TransferClient(ip, TransferServer.PORT, TransferStore.myId(c), TransferStore.myName(c), p.key));
            }
        } catch (Throwable ignored) { }
    }

    /** 送推播的錯誤是不是「這個 token 已經失效」（對方重裝、清過資料或換了 token）。 */
    static boolean tokenGone(String err) {
        return err != null && (err.contains("UNREGISTERED") || err.contains("NOT_FOUND") || err.contains("HTTP 404"));
    }

    /** 能連上這台已配對裝置時，順便在背景互換一次 token：我們沒有它的、或它換過新的，我們的新 token 它也會同時收到。每台 10 分鐘最多試一次；不支援的（電腦版）會失敗，直接忽略。 */
    static void healTokenAsync(Context c, TransferStore.Peer p, TransferClient client) {
        if (p == null || myToken(c) == null) return;
        synchronized (LAST_AUTO_EXCHANGE) {
            long now = System.currentTimeMillis();
            if (!exchangeDue(LAST_AUTO_EXCHANGE.get(p.id), now)) return;
            LAST_AUTO_EXCHANGE.put(p.id, now);
        }
        final Context app = c.getApplicationContext();
        final String id = p.id;
        Thread t = new Thread(() -> {
            try {
                String theirs = client.exchangeFcmToken(myToken(app));
                if (shouldAdopt(p.fcmToken, theirs)) {
                    TransferStore.setFcmToken(app, id, theirs);
                    TransferStore.log(app, "已自動和「" + TransferStore.nameOf(app, id) + "」" + (p.fcmToken == null ? "補互換" : "更新（對方換過）") + "推播 token");
                }
            } catch (Throwable ignored) { }
        }, "fcm-heal-token");
        t.setDaemon(true);
        t.start();
    }

    /** 我們的 token 換了：趁還連得上，馬上告訴每一台已配對裝置（用上次記下的區網或 Tailscale 位址，連不上就算了；之後連得上時 healTokenAsync 也會補）。要在背景執行緒。 */
    static void pushTokenToPeers(Context c) {
        final String mine = myToken(c);
        if (mine == null) return;
        for (TransferStore.Peer p : TransferStore.peers(c)) {
            String[][] addrs = {{p.ip, String.valueOf(p.port)}, {p.tsIp, String.valueOf(p.tsPort)}};
            for (String[] a : addrs) {
                if (a[0] == null || a[1].equals("0")) continue;
                try {
                    TransferClient cl = new TransferClient(a[0], Integer.parseInt(a[1]), TransferStore.myId(c), TransferStore.myName(c), p.key);
                    String theirs = cl.exchangeFcmToken(mine);
                    if (shouldAdopt(p.fcmToken, theirs)) TransferStore.setFcmToken(c, p.id, theirs);
                    TransferStore.log(c, "我們的推播 token 換了，已通知「" + p.name + "」");
                    break;
                } catch (Exception ignored) { }
            }
        }
    }

    /** 配對剛成功時呼叫（要在背景執行緒）：用同一次請求跟對方互換 token。失敗不影響配對本身。 */
    static void exchangeAfterPairing(Context c, TransferClient client, String peerId) {
        try {
            String theirs = client.exchangeFcmToken(myToken(c));
            if (theirs != null) {
                TransferStore.setFcmToken(c, peerId, theirs);
                TransferStore.log(c, "已和「" + TransferStore.nameOf(c, peerId) + "」互換推播 token");
            }
        } catch (Throwable e) {
            TransferStore.log(c, "互換推播 token 沒成功（之後可以在「推播喚醒設定」再做一次）：" + e.getMessage());
        }
    }

    /** 目前存的設定檔原文；沒有回 null。 */
    static String configJson(Context c) {
        try { return read(c, CONFIG); } catch (Exception e) { return null; }
    }

    /** 存設定檔或發送金鑰（內容自己判斷是哪一種，也檢查格式）。回 null 成功，否則原因。 */
    static String storeAuto(Context c, String json) {
        FcmFiles.Kind k = FcmFiles.classify(json);
        if (k == FcmFiles.Kind.UNKNOWN) return "不是 Firebase 設定檔，也不是服務帳戶金鑰";
        if (k == FcmFiles.Kind.CONFIG) {
            String bad = FcmFiles.configProblem(json);
            if (bad != null) return bad;
        }
        String other = k == FcmFiles.Kind.CONFIG ? FcmVault.get(c) : configJson(c);
        String mm = k == FcmFiles.Kind.CONFIG ? FcmFiles.mismatch(json, other) : FcmFiles.mismatch(other, json);
        if (mm != null) return mm;
        return store(c, new java.io.ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), k == FcmFiles.Kind.CONFIG ? CONFIG : SENDER);
    }

    /** 這支手機的推播設定準備好了嗎（兩個檔案都有，可以傳給別台）。 */
    static boolean canShareSetup(Context c) { return hasConfig(c) && FcmVault.has(c); }

    /** 把兩個檔案傳給已配對裝置（背景執行緒呼叫）。回 null＝對方接受，否則原因。 */
    static String shareSetup(Context c, TransferStore.Peer peer) {
        try {
            String key = FcmVault.get(c), cfg = configJson(c);
            if (key == null || cfg == null) return "這支手機還沒有完整的設定（要有設定檔和發送金鑰）";
            boolean ok = FileTools.clientFor(c, peer).sendFcmSetup(cfg, key);
            if (!ok) return "對方拒絕了，或沒有按確認";
            exchangeAfterSetup(c, peer);
            return null;
        } catch (Exception e) {
            return String.valueOf(e.getMessage());
        }
    }

    private static void exchangeAfterSetup(Context c, TransferStore.Peer peer) {
        try { refreshMyToken(c); Thread.sleep(1500); exchangeAfterPairing(c, FileTools.clientFor(c, peer), peer.id); } catch (Exception ignored) { }
    }

    static boolean hasConfig(Context c) { return file(c, CONFIG).isFile(); }

    static boolean hasSenderKey(Context c) { return FcmVault.has(c); }

    /** 把使用者選的檔案複製進私有資料夾（權限 600）。回 null 成功，否則錯誤訊息。 */
    static String store(Context c, InputStream in, String name) {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n, total = 0;
            while ((n = in.read(b)) > 0) { total += n; if (total > 64 * 1024) return "檔案太大，不像設定檔"; bo.write(b, 0, n); }
            new JSONObject(bo.toString("UTF-8"));   // 至少要是 JSON
            if (name.equals(SENDER)) {   // 服務帳戶金鑰：只存加密後的版本，不留明文檔案
                JSONObject j = new JSONObject(bo.toString("UTF-8"));
                if (!j.has("private_key") || !j.has("client_email")) return "這不像服務帳戶金鑰（沒有 private_key／client_email）";
                file(c, SENDER).delete();
                return FcmVault.put(c, bo.toString("UTF-8"));
            }
            File f = file(c, name), tmp = new File(f.getPath() + ".tmp");
            try (java.io.FileOutputStream o = new java.io.FileOutputStream(tmp)) { o.write(bo.toByteArray()); }
            tmp.setReadable(false, false); tmp.setReadable(true, true);
            tmp.setWritable(false, false); tmp.setWritable(true, true);
            if (!tmp.renameTo(f)) return "存檔失敗";
            return null;
        } catch (Exception e) {
            return "不是正確的 JSON 檔：" + e.getMessage();
        }
    }

    private static String read(Context c, String name) throws Exception {
        return new String(java.nio.file.Files.readAllBytes(file(c, name).toPath()), StandardCharsets.UTF_8);
    }

    /** 啟動時（含被推播叫醒、Application.onCreate）呼叫：有設定檔就初始化 Firebase。回 null 成功，否則原因。 */
    static String init(Context c) {
        try {
            if (!hasConfig(c)) return "還沒有 google-services.json";
            if (!FirebaseApp.getApps(c).isEmpty()) return null;
            JSONObject j = new JSONObject(read(c, CONFIG));
            JSONObject info = j.getJSONObject("project_info");
            JSONObject cl = j.getJSONArray("client").getJSONObject(0);
            FirebaseOptions o = new FirebaseOptions.Builder()
                    .setProjectId(info.getString("project_id"))
                    .setGcmSenderId(info.getString("project_number"))
                    .setApplicationId(cl.getJSONObject("client_info").getString("mobilesdk_app_id"))
                    .setApiKey(cl.getJSONArray("api_key").getJSONObject(0).getString("current_key"))
                    .build();
            FirebaseApp.initializeApp(c.getApplicationContext(), o);
            return null;
        } catch (Throwable e) {
            return String.valueOf(e);
        }
    }

    static String projectId(Context c) {
        try { return new JSONObject(read(c, CONFIG)).getJSONObject("project_info").getString("project_id"); } catch (Exception e) { return null; }
    }

    /** 送一則高優先級「資料訊息」給指定裝置 token。回 null 成功，否則錯誤（含狀態碼）。 */
    static String send(Context c, String token, String text) {
        java.util.Map<String, String> d = new java.util.LinkedHashMap<>();
        d.put("kind", "fcm_lab");
        d.put("text", text);
        d.put("sent", String.valueOf(System.currentTimeMillis()));
        return sendData(c, token, d);
    }

    static String sendData(Context c, String token, java.util.Map<String, String> data) {
        try {
            String sa = FcmVault.get(c);
            if (sa == null) return "這支手機沒有服務帳戶金鑰（只有發送端需要）";
            String project = projectId(c);
            if (project == null) return "沒有 google-services.json";
            String access = accessToken(new JSONObject(sa));
            JSONObject dj = new JSONObject();
            for (java.util.Map.Entry<String, String> e : data.entrySet()) dj.put(e.getKey(), e.getValue());
            JSONObject msg = new JSONObject()
                    .put("token", token.trim())
                    .put("data", dj)
                    .put("android", new JSONObject().put("priority", "high"));
            byte[] body = new JSONObject().put("message", msg).toString().getBytes(StandardCharsets.UTF_8);
            HttpURLConnection con = (HttpURLConnection) new URL("https://fcm.googleapis.com/v1/projects/" + project + "/messages:send").openConnection();
            con.setConnectTimeout(10000);
            con.setReadTimeout(20000);
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setRequestProperty("Authorization", "Bearer " + access);
            con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            try (OutputStream o = con.getOutputStream()) { o.write(body); }
            int code = con.getResponseCode();
            if (code / 100 == 2) return null;
            return "HTTP " + code + "：" + errText(slurp(con.getErrorStream()));
        } catch (Throwable e) {
            return String.valueOf(e);
        }
    }

    /** Google 的錯誤是一大段 JSON：只取 error.status 與 error.message（不含 token），顯示得完整、好讀。 */
    private static String errText(String body) {
        try {
            JSONObject e = new JSONObject(body).getJSONObject("error");
            return e.optString("status") + " — " + LlmErrors.redact(e.optString("message"));
        } catch (Exception ex) {
            return LlmErrors.redact(body);
        }
    }

    /** 本機「Wi-Fi 上」的區網位址與網段大小，格式「位址/前綴長度」（例如 192.168.1.20/24）；只看 Wi-Fi，行動數據與 VPN 不算。沒有回 null。 */
    static String lanNet() {
        try {
            android.net.ConnectivityManager cm = App.ctx.getSystemService(android.net.ConnectivityManager.class);
            for (android.net.Network n : cm.getAllNetworks()) {
                android.net.NetworkCapabilities caps = cm.getNetworkCapabilities(n);
                if (caps == null || !caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) continue;
                android.net.LinkProperties lp = cm.getLinkProperties(n);
                if (lp == null) continue;
                for (android.net.LinkAddress la : lp.getLinkAddresses()) {
                    String h = la.getAddress().getHostAddress();
                    if (la.getAddress() instanceof java.net.Inet4Address && Doorbell.isPrivateLan(h)) return h + "/" + la.getPrefixLength();
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    static String lanIp() {
        String n = lanNet();
        return n == null ? null : n.substring(0, n.indexOf('/'));
    }

    /** 這台已配對裝置可以用推播叫醒嗎：對方存過 token、這支手機有設定檔，也有（加密存放的）發送金鑰。 */
    static boolean canRing(Context c, TransferStore.Peer p) {
        return p != null && p.fcmToken != null && hasConfig(c) && FcmVault.has(c);
    }

    /**
     * 我和對方現在是不是「不在同一個區網」：自己沒連 Wi-Fi，或連的網段和對方上次記下的區網位址不同（例如對方上次在 192.168.1.x、我現在在 192.168.51.x）。
     * 對方的位址不知道時照舊（當作同一個區網，先用區網找）。
     */
    static boolean isRemote(String myLanNet, String peerLanIp) {
        if (myLanNet == null) return true;
        if (peerLanIp == null || peerLanIp.isEmpty()) return false;
        return !Doorbell.sameSubnet(myLanNet, peerLanIp);
    }

    /**
     * 被外網叫醒、Tailscale 開起來之後，把我的 Tailscale 位址用推播回報給叫醒我的人。最多等 20 秒讓 Tailscale 出現位址；沒有就不回報。
     * 背景執行緒做（不佔著收推播的回呼）。
     */
    static void reportAddressAsync(final Context c, final String peerId) {
        final Context app = c.getApplicationContext();
        Thread t = new Thread(() -> {
            try {
                TransferStore.Peer peer = null;
                for (TransferStore.Peer q : TransferStore.peers(app)) if (q.id.equals(peerId)) peer = q;
                if (peer == null || peer.fcmToken == null) return;
                String ts = null;
                long until = System.currentTimeMillis() + 20_000;
                while (ts == null && System.currentTimeMillis() < until) {
                    ts = TransferClient.localTailnetIp();
                    if (ts == null) Thread.sleep(500);
                }
                if (ts == null) { LifeLog.add(app, "FCM：Tailscale 一直沒有位址，沒辦法回報給「" + peer.name + "」"); return; }
                String err = sendData(app, peer.fcmToken, Doorbell.makeAddr(peer.key, TransferStore.myId(app), ts, TransferServer.PORT, System.currentTimeMillis()));
                LifeLog.add(app, "FCM：把我的 Tailscale 位址回報給「" + peer.name + "」" + (err == null ? "" : "失敗：" + err));
            } catch (Throwable ignored) { }
        }, "fcm-report-addr");
        t.setDaemon(true);
        t.start();
    }

    /** 叫醒對方：送一則「請把互傳伺服器開起來」的門鈴。回 null 成功，否則錯誤。之後的連線都照原本的直連方式。 */
    static String wake(Context c, TransferStore.Peer peer) {
        return wake(c, peer, isRemote(lanNet(), peer == null ? null : peer.ip));   // 沒連 Wi-Fi，或連的是另一個網路（網段跟對方上次的區網位址不同）：從外面來，走 Tailscale
    }

    /** remote 由呼叫的人決定（找對方時 PeerFinder 已判斷要走外網，例如不同路由器但網段一樣）：對方看到 remote 才會開 Tailscale、回報位址。 */
    static String wake(Context c, TransferStore.Peer peer, boolean remote) {
        if (peer == null || peer.fcmToken == null) return "還沒有存這台裝置的 token";
        String ip = lanIp();
        if (remote) {
            String ts = TransferClient.localTailnetIp();
            ip = ts == null ? "" : ts;
        }
        return sendData(c, peer.fcmToken, Doorbell.makeServe(peer.key, TransferStore.myId(c), ip, TransferServer.PORT, System.currentTimeMillis(), remote));
    }

    /** 連線失敗的類型（純邏輯，診斷頁用）：refused＝對方的網路通了但那個埠沒人聽；timeout＝連不到（對方沒開 Tailscale、不在同一個網路、被擋）；noroute＝本機沒有路由；其他。 */
    static String classifyConnectError(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof java.net.SocketTimeoutException) return "timeout";
            if (t instanceof java.net.NoRouteToHostException || t instanceof java.net.UnknownHostException) return "noroute";
            String m = String.valueOf(t.getMessage()).toLowerCase(java.util.Locale.ROOT);
            if (m.contains("econnrefused") || m.contains("connection refused")) return "refused";
            if (m.contains("etimedout") || m.contains("timed out")) return "timeout";
            if (m.contains("enetunreach") || m.contains("ehostunreach") || m.contains("network is unreachable") || m.contains("no route")) return "noroute";
        }
        return "other";
    }

    /** 連一下某個位址：ok／refused／timeout／noroute／other。 */
    static String probe(String ip, int port, int timeoutMs) {
        try (java.net.Socket sock = new java.net.Socket()) {
            sock.connect(new java.net.InetSocketAddress(ip, port), timeoutMs);
            return "ok";
        } catch (Exception e) {
            return classifyConnectError(e);
        }
    }

    static String describe(String cls) {
        switch (cls) {
            case "ok": return L.t("✅ 連得上");
            case "refused": return L.t("對方回「拒絕」（網路通，但互傳伺服器沒在跑）");
            case "timeout": return L.t("逾時");
            case "noroute": return L.t("沒有路由");
            default: return L.t("失敗");
        }
    }

    /** 外網連線檢查：本機 Tailscale、對方的位址紀錄、實際連一次，並依失敗類型說明斷在哪一環。要在背景執行緒呼叫。 */
    static String diagnoseRemote(Context c, TransferStore.Peer p) {
        StringBuilder sb = new StringBuilder();
        sb.append(L.t("對象：")).append(p.name).append('\n');
        String mine = TransferClient.localTailnetIp();
        sb.append(L.t("1. 本機 Tailscale：")).append(mine != null ? L.t("✅ 開著（") + mine + "）" : L.t("❌ 沒開（外網要先開；可按「測試開關 Tailscale」確認這支手機開得起來）")).append('\n');
        String lan = lanIp();
        sb.append(L.t("2. 本機網路：")).append(lan != null ? "Wi-Fi（" + lan + "）" : L.t("行動數據或沒有 Wi-Fi")).append('\n');
        sb.append(L.t("3. 對方的 Tailscale 位址紀錄：")).append(p.tsIp != null ? L.t("✅ ") + p.tsIp + ":" + p.tsPort : L.t("❌ 沒有")).append('\n');
        sb.append(L.t("4. 對方的區網位址紀錄：")).append(p.ip != null ? p.ip + ":" + p.port : L.t("沒有")).append('\n');
        sb.append(L.t("5. 對方的推播 token：")).append(p.fcmToken != null ? L.t("✅ 有（沒開互傳時可以叫醒它）") : L.t("❌ 沒有（沒辦法叫醒它，對方的互傳頁面要自己開著）")).append('\n');
        if (p.tsIp == null || p.tsPort <= 0) {
            sb.append(L.t("→ 沒有對方的 Tailscale 位址：要先在家讓兩台**同時開著 Tailscale**，各自開互傳頁連一次，才會記下來。\n"));
            return sb.toString();
        }
        long t0 = System.currentTimeMillis();
        String cls;
        try (java.net.Socket sock = new java.net.Socket()) {
            sock.connect(new java.net.InetSocketAddress(p.tsIp, p.tsPort), 5000);
            cls = "ok";
        } catch (Exception e) {
            cls = classifyConnectError(e);
        }
        long ms = System.currentTimeMillis() - t0;
        switch (cls) {
            case "ok": {
                boolean ping = false;
                try { new TransferClient(p.tsIp, p.tsPort, TransferStore.myId(c), TransferStore.myName(c), p.key).ping(); ping = true; } catch (Exception ignored) { }
                sb.append(L.t("6. 直接連 ")).append(p.tsIp).append(":").append(p.tsPort).append(L.t("：✅ 連得上（")).append(ms).append(L.t(" 毫秒）\n"));
                sb.append(ping ? L.t("7. 互傳伺服器有回應 ✅ → 外網是通的，可以直接傳檔案\n")
                        : L.t("7. ❌ 連得上埠，但伺服器沒有正常回應（驗證失敗？對方的程式版本太舊？）：先把兩支都更新到最新版\n"));
                break;
            }
            case "refused":
                sb.append(L.t("6. 直接連 ")).append(p.tsIp).append(":").append(p.tsPort).append(L.t("：對方拒絕連線\n"));
                sb.append(L.t("→ 對方的 Tailscale 通了，**但它的互傳伺服器沒在跑**。")).append(p.fcmToken != null ? L.t("要靠推播叫醒它（傳檔案時會自動做）；叫不醒就看「測試叫醒對方」的診斷。") : L.t("沒有它的推播 token，沒辦法叫醒：請在那支手機打開互傳頁面，或補互換 token。")).append('\n');
                break;
            case "timeout":
                sb.append(L.t("6. 直接連 ")).append(p.tsIp).append(":").append(p.tsPort).append(L.t("：逾時（")).append(ms).append(L.t(" 毫秒）\n"));
                // 逾時代表封包沒到對方（對方的 Tailscale 通的話，埠沒人聽會立刻被拒絕）。再用更久的時間試一次，看是不是第一次連線建立路徑太慢；也試對方的區網位址，看它醒著沒、在不在同一個網路
                String retry = probe(p.tsIp, p.tsPort, 15000);
                sb.append(L.t("   再試一次（等最多 15 秒）：")).append(describe(retry)).append('\n');
                if (p.ip != null) sb.append(L.t("   對方的區網位址 ")).append(p.ip).append(":").append(p.port).append("：").append(describe(probe(p.ip, p.port, 4000))).append('\n');
                if ("ok".equals(retry) || "refused".equals(retry)) {
                    sb.append(L.t("→ 第二次通了：是第一次連線在建立路徑，比較慢；實際傳檔案時 App 會等得比較久。\n"));
                    break;
                }
                sb.append(L.t("→ 兩次都逾時：封包到不了對方，或對方沒有回應。**對方的 Tailscale 可能被系統凍住**（App 顯示開著、其實不處理封包）：到對方手機給 Tailscale「自啟動」「電池無限制」並在最近工作清單鎖定它，再試。\n   "));
                sb.append(L.t("請到對方手機打開 Tailscale，看它自己的位址是不是 ")).append(p.tsIp).append(L.t("（不一樣就是我們記的過期了：兩台同時開著 Tailscale，各自開互傳頁連一次就會更新）；也請讓對方螢幕亮著再檢查一次，看是不是它休眠。\n"));
                sb.append(L.t("→ 連不到對方：最常見是**對方的 Tailscale 沒開**（平常關著的話就是這樣）、或兩台不在同一個 Tailscale 帳號。")).append(p.fcmToken != null ? L.t("傳檔案時 App 會先叫醒對方並請它開 Tailscale（要 root，也要給 Tailscale 自啟動）。") : L.t("沒有它的推播 token，沒辦法請它開：請在那支手機手動開 Tailscale。")).append('\n');
                break;
            case "noroute":
                sb.append(L.t("6. 直接連 ")).append(p.tsIp).append(L.t("：沒有路由\n→ 本機的 Tailscale 沒開或沒連上，先把本機的 Tailscale 開起來。\n"));
                break;
            default:
                sb.append(L.t("6. 直接連 ")).append(p.tsIp).append(L.t("：失敗（原因不明）\n"));
        }
        return sb.toString();
    }

    /** 逐步診斷「叫醒對方」：每一步的結果都寫出來（不含 token 全文與金鑰），看是哪一步斷掉。回傳多行文字。 */
    static String diagnoseWake(Context c, TransferStore.Peer p) {
        StringBuilder sb = new StringBuilder();
        sb.append(L.t("對象：")).append(p.name).append(L.t("（編號 ")).append(p.id.length() > 8 ? p.id.substring(0, 8) : p.id).append(L.t("，上次的位址 ")).append(p.ip == null ? L.t("未知") : p.ip).append("）\n");
        sb.append(L.t("1. 對方的 token：")).append(p.fcmToken == null ? L.t("❌ 沒有（先互換 token）") : L.t("✅ 有（結尾 ") + p.fcmToken.substring(Math.max(0, p.fcmToken.length() - 6)) + "）").append('\n');
        sb.append(L.t("2. 這支手機的設定檔：")).append(hasConfig(c) ? L.t("✅") : L.t("❌")).append(L.t("；發送金鑰：")).append(FcmVault.has(c) ? L.t("✅") : L.t("❌")).append('\n');
        String ip = lanNet();
        sb.append(L.t("3. 這支手機的 Wi-Fi 位址：")).append(ip != null ? L.t("✅ ") + ip : p.tsIp != null ? L.t("（沒連 Wi-Fi，改走 Tailscale 外網；對方的 Tailscale 位址 ") + p.tsIp + "）" : L.t("❌ 沒有（也沒有對方的 Tailscale 位址）")).append('\n');
        if (p.fcmToken == null || !hasConfig(c) || !FcmVault.has(c) || (ip == null && p.tsIp == null)) { sb.append(L.t("→ 上面有 ❌，先處理它")).append('\n'); return sb.toString(); }
        TransferDiscovery.Found already = FileTools.scanPeer(p, 1200);
        if (already != null) { sb.append(L.t("4. 對方現在其實已經在線上（")).append(already.ip).append(L.t("），沒有休眠，這次測不出叫醒。請讓它休眠幾分鐘再測。")).append('\n'); return sb.toString(); }
        sb.append(L.t("4. 對方目前不在線上（找不到），開始叫醒\n"));
        long t0 = System.currentTimeMillis();
        String err = wake(c, p);
        sb.append(L.t("5. 推播送出：")).append(err == null ? L.t("✅ Google 收下了（花 ") + (System.currentTimeMillis() - t0) + L.t(" 毫秒）") : L.t("❌ ") + err).append('\n');
        if (err != null) {
            if (err.contains("UNREGISTERED") || err.contains("NOT_FOUND")) sb.append(L.t("→ 對方的 token 已失效（它重裝過或清過資料）：請在能連上它的時候重新互換 token。\n"));
            if (err.contains("PERMISSION") || err.contains("401") || err.contains("403")) sb.append(L.t("→ 發送金鑰沒有權限或不是這個專案的：重新匯入正確的金鑰。\n"));
            return sb.toString();
        }
        TransferDiscovery.Found f = null;
        long until = System.currentTimeMillis() + 20000;
        while (f == null && System.currentTimeMillis() < until) f = FileTools.scanPeer(p, 1500);
        if (f != null) sb.append(L.t("6. ✅ 對方起來了（")).append(f.ip).append(L.t("），共花 ")).append((System.currentTimeMillis() - t0) / 1000.0).append(L.t(" 秒\n"));
        else sb.append(L.t("6. ❌ 20 秒內對方沒有出現。推播送到了，但對方沒有把伺服器開起來：到對方手機看有沒有跳「FCM 門鈴」的通知（會寫被拒絕的原因）；沒有任何通知的話，是對方的系統沒有叫醒它（自啟動、電池限制）；對方的互傳總開關也要開著。\n"));
        return sb.toString();
    }

    /** 被「叫醒」的這一端：把互傳伺服器開起來，沒人用了就自動關（瀏覽時最後一個連線結束後多留 60 秒）。 */
    static String serveForAWhile(Context c) { return serveForAWhile(c, false); }

    /** remote＝對方從外面來：要走 Tailscale，先確定自己的 Tailscale 開著（沒開就盡力開起來；是我們開的，用完會關回去）。 */
    static String serveForAWhile(Context c, boolean remote) {
        final Context app = c.getApplicationContext();
        if (!TransferStore.enabled(app)) { LifeLog.add(app, "FCM 叫醒：互傳總開關沒開，不開伺服器"); return "互傳總開關沒開，所以沒有開伺服器（到「設定 → 互傳檔案」打開）"; }
        String note = "";
        boolean weTurnedTailscaleOn = false;
        if (remote) TailscaleControl.hold(90_000);   // 對方在連進來的路上：先不要把 Tailscale 關掉
        if (remote && TailscaleControl.isOn()) TailscaleControl.nudge(app);   // 顯示開著但可能被系統凍住：戳一下讓它醒過來
        if (remote && !TailscaleControl.isOn()) {
            weTurnedTailscaleOn = TailscaleControl.turnOn(app, 12000);
            note = weTurnedTailscaleOn ? "，已幫你把 Tailscale 開起來" : "，但 Tailscale 開不起來（對方從外面連不進來）";
            LifeLog.add(app, "FCM 叫醒（外網）：Tailscale " + (weTurnedTailscaleOn ? "已開起來" : "開不起來"));
        }
        final boolean restore = weTurnedTailscaleOn;   // 只用在記錄文字；Tailscale 是否關回去由 TailscaleControl 的共用監看負責（伺服器本來就開著時也一樣）
        final boolean startedHere = !TransferService.running;
        TransferService.start(app);
        if (!startedHere) return "互傳本來就開著，等對方連進來" + note;   // 本來就開著（使用者自己開的）：不去關它
        if (!Waits.until(() -> TransferService.running, 5000, 100)) {   // 要等它真的在聽才算開起來；啟動失敗（前景服務被擋、埠被占用）要照實說
            String why = TransferService.lastError.isEmpty() ? "沒有回應" : TransferService.lastError;
            LifeLog.add(app, "FCM 叫醒：互傳伺服器啟動失敗：" + why);
            return "互傳伺服器啟動失敗（" + why + "），對方連不進來" + note;
        }
        Thread t = new Thread(() -> {
            IdleWatch w = new IdleWatch(System.currentTimeMillis(), 60_000);
            try {
                while (!w.tick(System.currentTimeMillis(), TransferServer.activeConnections())) Thread.sleep(3000);
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            TransferService.stop(app);
            TransferStore.log(app, "被叫醒而開起的互傳伺服器已自動關閉" + (restore ? "（我們開的 Tailscale 閒置後會自動關回去）" : ""));
        }, "fcm-serve-autostop");
        t.setDaemon(true);
        t.start();
        return "已把互傳伺服器開起來，等對方連進來（沒人用會自動關）" + note;
    }

    private static String slurp(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] b = new byte[4096];
        int n, total = 0;
        while ((n = in.read(b)) > 0 && total < 8192) { bo.write(b, 0, n); total += n; }
        return bo.toString("UTF-8");
    }

    /** 服務帳戶 → 簽 RS256 JWT → 換 OAuth access token（scope：firebase.messaging）。 */
    private static String accessToken(JSONObject sa) throws Exception {
        String email = sa.getString("client_email");
        String pem = sa.getString("private_key").replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "");
        PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
        long now = System.currentTimeMillis() / 1000;
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        String head = enc.encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String claims = enc.encodeToString(new JSONObject().put("iss", email).put("scope", "https://www.googleapis.com/auth/firebase.messaging")
                .put("aud", "https://oauth2.googleapis.com/token").put("iat", now).put("exp", now + 3000).toString().getBytes(StandardCharsets.UTF_8));
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(key);
        sig.update((head + "." + claims).getBytes(StandardCharsets.UTF_8));
        String jwt = head + "." + claims + "." + enc.encodeToString(sig.sign());
        HttpURLConnection con = (HttpURLConnection) new URL("https://oauth2.googleapis.com/token").openConnection();
        con.setConnectTimeout(10000);
        con.setReadTimeout(20000);
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        try (OutputStream o = con.getOutputStream()) {
            o.write(("grant_type=" + java.net.URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", "UTF-8") + "&assertion=" + jwt).getBytes(StandardCharsets.UTF_8));
        }
        if (con.getResponseCode() / 100 != 2) throw new java.io.IOException("換 access token 失敗 HTTP " + con.getResponseCode());
        return new JSONObject(slurp(con.getInputStream())).getString("access_token");
    }
}
