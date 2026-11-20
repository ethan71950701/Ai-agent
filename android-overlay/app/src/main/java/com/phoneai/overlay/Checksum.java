package com.phoneai.overlay;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

/** 互傳的整檔校驗碼（SHA-256，純 Java）：傳輸時邊傳邊算，傳完兩邊比對，確認存到磁碟的檔案跟送出的一樣。 */
final class Checksum {
    private Checksum() { }

    static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String hex(byte[] b) { return TransferCrypto.hex(b); }

    /** 整個檔案讀一遍算出來（沒有現成的值時才用）。 */
    static String sha256(File f) throws IOException {
        MessageDigest md = newDigest();
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        return hex(md.digest());
    }

    /** 顯示用的前 8 碼。 */
    static String shortOf(String hex) { return hex == null ? "" : hex.length() <= 8 ? hex : hex.substring(0, 8); }

    /** 兩個校驗碼相同（忽略大小寫）；任何一個空的都不算相同。 */
    static boolean same(String a, String b) { return a != null && b != null && !a.isEmpty() && a.equalsIgnoreCase(b); }

    /** 伺服器送出檔案時順便算好的校驗碼，對方之後來問就不用再讀一遍。鍵包含大小與修改時間，檔案改過就不會誤用。 */
    static final class Cache {
        private final int max;
        private final Map<String, String> map = new LinkedHashMap<String, String>(16, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, String> e) { return size() > max; }
        };

        Cache(int max) { this.max = max; }

        static String key(File f) { return f.getPath() + "|" + f.length() + "|" + f.lastModified(); }

        synchronized void put(File f, String hex) { map.put(key(f), hex); }

        synchronized String get(File f) { return map.get(key(f)); }
    }
}
