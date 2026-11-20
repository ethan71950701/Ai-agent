package com.phoneai.overlay;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * 服務帳戶金鑰的保險箱：用 Android Keystore 裡的 AES-GCM 金鑰加密後才存檔（金鑰本身拿不出 Keystore，
 * 所以就算有人複製了 App 的資料夾，也解不開這個檔案）。檔案格式：12 位元組 IV＋密文。
 * Keystore 在 JVM 單元測試裡不存在，這個類別只在手機上用。
 */
final class FcmVault {
    private FcmVault() { }

    private static final String ALIAS = "phoneai_fcm_vault";
    static final String FILE = "fcm_service_account.enc";

    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(ALIAS)) return (SecretKey) ks.getKey(ALIAS, null);
        KeyGenerator g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        g.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build());
        return g.generateKey();
    }

    static boolean has(Context c) { return new File(c.getFilesDir(), FILE).isFile(); }

    /** 加密後存起來；回 null 成功，否則錯誤。 */
    static String put(Context c, String json) {
        try {
            Cipher cp = Cipher.getInstance("AES/GCM/NoPadding");
            cp.init(Cipher.ENCRYPT_MODE, key());
            byte[] ct = cp.doFinal(json.getBytes(StandardCharsets.UTF_8));
            byte[] iv = cp.getIV();
            byte[] all = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, all, 0, iv.length);
            System.arraycopy(ct, 0, all, iv.length, ct.length);
            File f = new File(c.getFilesDir(), FILE), tmp = new File(f.getPath() + ".tmp");
            Files.write(tmp.toPath(), all);
            tmp.setReadable(false, false); tmp.setReadable(true, true);
            tmp.setWritable(false, false); tmp.setWritable(true, true);
            if (!tmp.renameTo(f)) return "存檔失敗";
            return null;
        } catch (Throwable e) {
            return "加密存放失敗：" + e;
        }
    }

    /** 解密讀回；沒有或失敗回 null（金鑰被系統清掉、檔案損壞時也是 null）。 */
    static String get(Context c) {
        try {
            byte[] all = Files.readAllBytes(new File(c.getFilesDir(), FILE).toPath());
            if (all.length < 13) return null;
            Cipher cp = Cipher.getInstance("AES/GCM/NoPadding");
            cp.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, all, 0, 12));
            return new String(cp.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
        } catch (Throwable e) {
            return null;
        }
    }

    static void delete(Context c) {
        new File(c.getFilesDir(), FILE).delete();
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            ks.deleteEntry(ALIAS);   // 連 Keystore 裡的金鑰一起刪，之後就算舊檔案殘留也解不開
        } catch (Throwable ignored) { }
    }
}
