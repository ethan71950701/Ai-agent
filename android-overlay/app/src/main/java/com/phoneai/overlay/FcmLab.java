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

    /** 按門鈴：A（本機）請這個已配對裝置來取檔案（relPath 是共享根目錄底下的相對路徑，檔案要先存在）。回 null 成功，否則錯誤。 */
    static String ring(Context c, TransferStore.Peer peer, String relPath) {
        if (peer.fcmToken == null) return "還沒有存這台裝置的 token";
        if (!TransferStore.enabled(c)) return "互傳總開關沒開（設定 → 互傳檔案）";
        boolean startedHere = !TransferService.running;
        if (startedHere) {   // 互傳伺服器沒在跑（系統可能把它收掉了）：先臨時把它開起來，等它真的在聽才按門鈴
            TransferService.start(c);
            long until = System.currentTimeMillis() + 5000;
            while (!TransferService.running && System.currentTimeMillis() < until) {
                try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
            if (!TransferService.running) return "互傳伺服器啟動失敗" + (TransferService.lastError.isEmpty() ? "" : "：" + TransferService.lastError);
        }
        String ip = lanIp();
        if (ip == null) return "找不到本機的區網位址（要連上 Wi-Fi）";
        File f = new File(TransferStore.root(), relPath);
        if (!f.isFile()) return "找不到檔案：" + relPath;
        java.util.Map<String, String> m = Doorbell.make(peer.key, TransferStore.myId(c), ip, TransferServer.PORT, relPath, f.getName(), f.length(), System.currentTimeMillis());
        String err = sendData(c, peer.fcmToken, m);
        if (startedHere) autoStop(c, err != null);   // 是我們臨時開的：用完自動關
        return err;
    }

    /** 臨時開起的伺服器，沒人來或傳完就關（IdleWatch）。門鈴沒送成功就不用等，直接關。 */
    private static void autoStop(Context c, boolean now) {
        final Context app = c.getApplicationContext();
        if (now) { TransferService.stop(app); return; }
        Thread t = new Thread(() -> {
            IdleWatch w = new IdleWatch(System.currentTimeMillis());
            try {
                while (!w.tick(System.currentTimeMillis(), TransferServer.activeConnections())) Thread.sleep(3000);
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            TransferService.stop(app);
            TransferStore.log(app, "臨時開起的互傳伺服器已自動關閉");
        }, "fcm-autostop");
        t.setDaemon(true);
        t.start();
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
