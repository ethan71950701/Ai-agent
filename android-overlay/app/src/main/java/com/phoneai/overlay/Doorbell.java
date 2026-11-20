package com.phoneai.overlay;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.json.JSONObject;

/**
 * 推播「門鈴」（純 Java，docs/FCM_TRANSFER_DESIGN.md）：發送端把「請到這個位址取這個檔案」加密成一則很小的 FCM 資料訊息，
 * 接收端先驗證才會連線。門鈴只是通知，不是授權：真正取檔仍要用配對金鑰簽章的請求。
 *
 * 訊息欄位：v（版本）、src（發送端裝置 id；FCM 的資料欄位不能叫 from、message_type，或 google／gcm 開頭）、ts（毫秒）、nonce（16 位元組隨機，hex）、ct（Base64 的 AES-GCM 密文）。
 * 密文金鑰＝HMAC(配對金鑰, "bell|"+nonce)，每則都不同；AAD 綁住 v、from、ts、nonce，任何一項被改解密就失敗。
 */
final class Doorbell {
    private Doorbell() { }

    static final String VERSION = "1";
    static final long WINDOW_MS = 60_000;
    static final long MAX_SIZE = 2L * 1024 * 1024 * 1024;

    /** 驗證通過的內容。 */
    static final class Ring {
        String from, ip, path, name, mode = "file";   // mode：file＝請來取這個檔案；serve＝請把互傳伺服器開起來
        int port;
        long size, ts;
    }

    static final class Result {
        final Ring ring;
        final String error;   // ring 為 null 時的原因（只給記錄用，不顯示金鑰或內容）
        Result(Ring r, String e) { ring = r; error = e; }
        boolean ok() { return ring != null; }
    }

    interface Keys { byte[] keyOf(String peerId); }

    // ---------- 發送端 ----------

    static Map<String, String> make(byte[] key, String fromId, String ip, int port, String path, String name, long size, long nowMs) {
        try {
            String nonce = TransferCrypto.hex(TransferCrypto.random(16));
            String ts = String.valueOf(nowMs);
            JSONObject body = new JSONObject().put("ip", ip).put("port", port).put("path", path).put("name", name).put("size", size);
            byte[] ct = crypt(Cipher.ENCRYPT_MODE, key, nonce, aad(VERSION, fromId, ts, nonce), body.toString().getBytes(StandardCharsets.UTF_8));
            Map<String, String> m = new LinkedHashMap<>();
            m.put("kind", "doorbell");
            m.put("v", VERSION);
            m.put("src", fromId);   // 不能叫 from：那是 FCM 的保留字，會被 HTTP 400 拒絕
            m.put("ts", ts);
            m.put("nonce", nonce);
            m.put("ct", Base64.getEncoder().encodeToString(ct));
            return m;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 「請把你的互傳伺服器開起來」：之後的傳檔、瀏覽、找檔都照原本的直連方式，推播只負責叫醒。 */
    static Map<String, String> makeServe(byte[] key, String fromId, String ip, int port, long nowMs) {
        try {
            String nonce = TransferCrypto.hex(TransferCrypto.random(16));
            String ts = String.valueOf(nowMs);
            JSONObject body = new JSONObject().put("mode", "serve").put("ip", ip).put("port", port).put("path", "").put("name", "").put("size", 0);
            byte[] ct = crypt(Cipher.ENCRYPT_MODE, key, nonce, aad(VERSION, fromId, ts, nonce), body.toString().getBytes(StandardCharsets.UTF_8));
            Map<String, String> m = new LinkedHashMap<>();
            m.put("kind", "doorbell");
            m.put("v", VERSION);
            m.put("src", fromId);
            m.put("ts", ts);
            m.put("nonce", nonce);
            m.put("ct", Base64.getEncoder().encodeToString(ct));
            return m;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------- 接收端 ----------

    /** 驗證器：記住最近用過的 nonce，同一則門鈴只認一次。 */
    static final class Verifier {
        private final Keys keys;
        private final Map<String, Long> seen = new LinkedHashMap<>();

        Verifier(Keys keys) { this.keys = keys; }

        synchronized Result verify(Map<String, String> m, long nowMs, String myIpOrNull) {
            String v = m.get("v"), from = m.get("src"), ts = m.get("ts"), nonce = m.get("nonce"), ct = m.get("ct");
            if (!VERSION.equals(v)) return fail("版本不支援");
            if (from == null || from.isEmpty() || from.length() > 64) return fail("來源 id 不對");
            if (nonce == null || !nonce.matches("[0-9a-f]{32}")) return fail("nonce 格式不對");
            if (ts == null || !ts.matches("\\d{1,15}")) return fail("時間格式不對");
            if (ct == null || ct.length() > 3000) return fail("內容缺少或太長");
            long t = Long.parseLong(ts);
            if (Math.abs(nowMs - t) > WINDOW_MS) return fail("門鈴過期（超過 60 秒）");
            byte[] key = keys.keyOf(from);
            if (key == null) return fail("沒配對過的裝置");
            JSONObject o;
            try {
                byte[] plain = crypt(Cipher.DECRYPT_MODE, key, nonce, aad(v, from, ts, nonce), Base64.getDecoder().decode(ct));
                o = new JSONObject(new String(plain, StandardCharsets.UTF_8));
            } catch (Exception e) {
                return fail("解密或驗證失敗");   // 金鑰不對、內容或欄位被改都會到這裡
            }
            if (seen.containsKey(from + nonce)) return fail("重複的門鈴");
            Ring r = new Ring();
            r.from = from;
            r.ts = t;
            r.ip = o.optString("ip");
            r.port = o.optInt("port", 0);
            r.path = o.optString("path");
            r.name = o.optString("name");
            r.size = o.optLong("size", -1);
            r.mode = o.optString("mode", "file");
            if (!r.mode.equals("file") && !r.mode.equals("serve")) return fail("不認得的門鈴類型");
            String bad = checkFields(r, myIpOrNull);
            if (bad != null) return fail(bad);
            seen.put(from + nonce, nowMs);   // 驗證全部通過才記，亂送的壞訊息不佔位置
            prune(nowMs);
            return new Result(r, null);
        }

        private void prune(long now) {
            java.util.Iterator<Map.Entry<String, Long>> it = seen.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Long> e = it.next();
                if (now - e.getValue() > WINDOW_MS * 2 || seen.size() > 256) it.remove(); else break;   // 依加入順序，最舊的在前
            }
        }
    }

    /** FCM 裝置 token 的格式檢查：英數字、冒號、底線、減號，長度合理（只擋明顯不對的，不保證 Google 認得）。 */
    static boolean validToken(String t) {
        return t != null && t.matches("[A-Za-z0-9:_\\-]{50,400}");
    }

    private static Result fail(String why) { return new Result(null, why); }

    /** 內容欄位檢查：位址必須是區網私有位址，大小、路徑、檔名有上限，不接受控制字元。 */
    static String checkFields(Ring r, String myIpOrNull) {
        if (!isPrivateLan(r.ip)) return "位址不是區網私有位址";
        if (myIpOrNull != null && !sameSubnet(myIpOrNull, r.ip)) return "位址不在目前的網段（門鈴 " + r.ip + "，本機 " + myIpOrNull + "）";
        if (r.port < 1 || r.port > 65535) return "埠號不對";
        if ("serve".equals(r.mode)) return null;   // 只是叫醒：沒有檔案欄位要檢查
        if (r.size < 0 || r.size > MAX_SIZE) return "檔案大小不對或超過 2 GiB";
        if (r.path.isEmpty() || r.path.length() > 1024 || hasControl(r.path)) return "路徑不對";
        if (r.name.length() > 255 || hasControl(r.name)) return "檔名不對";
        return null;
    }

    private static boolean hasControl(String s) {
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) < 0x20 || s.charAt(i) == 0x7f) return true;
        return false;
    }

    /** 只接受 IPv4 數字位址（不做 DNS），且是 10.x、172.16–31.x、192.168.x（不含 127.x、100.x Tailscale、169.254.x）。 */
    static boolean isPrivateLan(String ip) {
        int[] p = octets(ip);
        if (p == null) return false;
        if (p[0] == 10) return true;
        if (p[0] == 172 && p[1] >= 16 && p[1] <= 31) return true;
        return p[0] == 192 && p[1] == 168;
    }

    static boolean sameSubnet24(String a, String b) { return sameSubnet(stripPrefix(a) + "/24", b); }

    /** a 可以是「位址」（當成 /24）或「位址/前綴長度」（手機實際回報的網段大小，例如 172.23.4.118/22）。 */
    static boolean sameSubnet(String a, String b) {
        int prefix = 24;
        String ip = a;
        int slash = a == null ? -1 : a.indexOf('/');
        if (slash >= 0) {
            ip = a.substring(0, slash);
            try { prefix = Integer.parseInt(a.substring(slash + 1)); } catch (NumberFormatException e) { return false; }
        }
        if (prefix < 8 || prefix > 30) return false;   // 太大（/8 以下）或太小的網段不合理，不當成同網段
        int[] x = octets(ip), y = octets(b);
        if (x == null || y == null) return false;
        long xi = ((long) x[0] << 24) | (x[1] << 16) | (x[2] << 8) | x[3];
        long yi = ((long) y[0] << 24) | (y[1] << 16) | (y[2] << 8) | y[3];
        long mask = (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
        return (xi & mask) == (yi & mask);
    }

    private static String stripPrefix(String a) {
        int slash = a == null ? -1 : a.indexOf('/');
        return slash >= 0 ? a.substring(0, slash) : a;
    }

    private static int[] octets(String ip) {
        if (ip == null || !ip.matches("\\d{1,3}(\\.\\d{1,3}){3}")) return null;
        String[] s = ip.split("\\.");
        int[] o = new int[4];
        for (int i = 0; i < 4; i++) {
            o[i] = Integer.parseInt(s[i]);
            if (o[i] > 255) return null;
        }
        return o;
    }

    // ---------- 加解密 ----------

    private static String aad(String v, String from, String ts, String nonce) { return "bell|" + v + "|" + from + "|" + ts + "|" + nonce; }

    private static byte[] crypt(int mode, byte[] key, String nonce, String aad, byte[] data) throws GeneralSecurityException {
        byte[] k = TransferCrypto.hmac(key, "bell|" + nonce);   // 每則訊息一把新金鑰，所以固定的 IV 不會重複使用
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(mode, new SecretKeySpec(k, "AES"), new GCMParameterSpec(128, new byte[12]));
        c.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
        return c.doFinal(data);
    }
}
