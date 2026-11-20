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

    /** 把使用者選的檔案複製進私有資料夾（權限 600）。回 null 成功，否則錯誤訊息。 */
    static String store(Context c, InputStream in, String name) {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n, total = 0;
            while ((n = in.read(b)) > 0) { total += n; if (total > 64 * 1024) return "檔案太大，不像設定檔"; bo.write(b, 0, n); }
            new JSONObject(bo.toString("UTF-8"));   // 至少要是 JSON
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
            if (!file(c, SENDER).isFile()) return "這支手機沒有服務帳戶金鑰（只有發送端需要）";
            String project = projectId(c);
            if (project == null) return "沒有 google-services.json";
            String access = accessToken(new JSONObject(read(c, SENDER)));
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

    /** 本機「Wi-Fi 上」的區網 IPv4（10.x／172.16–31.x／192.168.x）。只看 Wi-Fi 網路：行動數據、VPN 有時也是私有位址，不能拿來當區網位址。沒有回 null。 */
    static String lanIp() {
        try {
            android.net.ConnectivityManager cm = App.ctx.getSystemService(android.net.ConnectivityManager.class);
            for (android.net.Network n : cm.getAllNetworks()) {
                android.net.NetworkCapabilities caps = cm.getNetworkCapabilities(n);
                if (caps == null || !caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) continue;
                android.net.LinkProperties lp = cm.getLinkProperties(n);
                if (lp == null) continue;
                for (android.net.LinkAddress la : lp.getLinkAddresses()) {
                    String h = la.getAddress().getHostAddress();
                    if (la.getAddress() instanceof java.net.Inet4Address && Doorbell.isPrivateLan(h)) return h;
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    /** 按門鈴：A（本機）請這個已配對裝置來取檔案（relPath 是共享根目錄底下的相對路徑，檔案要先存在）。回 null 成功，否則錯誤。 */
    static String ring(Context c, TransferStore.Peer peer, String relPath) {
        if (peer.fcmToken == null) return "還沒有存這台裝置的 token";
        String ip = lanIp();
        if (ip == null) return "找不到本機的區網位址（要連上 Wi-Fi）";
        File f = new File(TransferStore.root(), relPath);
        if (!f.isFile()) return "找不到檔案：" + relPath;
        java.util.Map<String, String> m = Doorbell.make(peer.key, TransferStore.myId(c), ip, TransferServer.PORT, relPath, f.getName(), f.length(), System.currentTimeMillis());
        return sendData(c, peer.fcmToken, m);
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
