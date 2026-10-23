package com.phoneai.overlay;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 裝置之間傳檔案的加密（純 Java，不依賴 Android，方便在電腦上測試）。設計見 docs/FILE_TRANSFER.md。
 * 配對：ECDH（P-256）交換暫時金鑰 → 兩邊算出同一個 6 位數「確認碼」讓使用者核對 → 長期金鑰。
 * 每個請求：HMAC-SHA256 簽章（含時間戳與隨機數，防重送）；請求與回應的內容用 AES-GCM 分段加密。
 */
final class TransferCrypto {
    static final int CHUNK = 64 * 1024;
    private static final SecureRandom RNG = new SecureRandom();

    private TransferCrypto() { }

    static byte[] random(int n) {
        byte[] b = new byte[n];
        RNG.nextBytes(b);
        return b;
    }

    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(Character.forDigit((x >> 4) & 15, 16)).append(Character.forDigit(x & 15, 16));
        return sb.toString();
    }

    static byte[] unhex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        return out;
    }

    static byte[] hmac(byte[] key, String data) {
        try {
            Mac m = Mac.getInstance("HmacSHA256");
            m.init(new SecretKeySpec(key, "HmacSHA256"));
            return m.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 請求簽章：方法、目標網址、時間戳、隨機數。 */
    static String sign(byte[] key, String method, String target, String ts, String nonce) {
        return hex(hmac(key, method + "\n" + target + "\n" + ts + "\n" + nonce));
    }

    static boolean sameHex(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    /** 這一個請求的內容加密金鑰：由長期金鑰與這次的隨機數衍生，每個請求都不一樣。 */
    static byte[] encKey(byte[] key, String nonce) {
        return hmac(key, "enc|" + nonce);
    }

    // ---------- 配對 ----------

    static KeyPair newPair() throws GeneralSecurityException {
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        return g.generateKeyPair();
    }

    static byte[] shared(PrivateKey mine, byte[] theirPubEncoded) throws GeneralSecurityException {
        PublicKey p = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(theirPubEncoded));
        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(mine);
        ka.doPhase(p, true);
        return ka.generateSecret();
    }

    /** 發起方的資料在前：雙方算出同一份字串，確認碼與長期金鑰都綁在它上面（中間人換了公開金鑰，確認碼就會對不上）。 */
    static String transcript(String initId, byte[] initPub, String respId, byte[] respPub) {
        return initId + "|" + hex(initPub) + "|" + respId + "|" + hex(respPub);
    }

    static byte[] pairKey(byte[] shared, String transcript) {
        return hmac(shared, "key|" + transcript);
    }

    /** 6 位數確認碼：兩台裝置同時顯示，使用者核對相符才接受。 */
    static String sas(byte[] shared, String transcript) {
        BigInteger v = new BigInteger(1, hmac(shared, "sas|" + transcript));
        return String.format("%06d", v.mod(BigInteger.valueOf(1_000_000)).intValue());
    }

    // ---------- 分段加密 ----------

    private static byte[] iv(long index, boolean last) {
        byte[] iv = new byte[12];
        for (int i = 0; i < 8; i++) iv[i] = (byte) (index >>> (56 - 8 * i));
        iv[11] = (byte) (last ? 1 : 0);
        return iv;
    }

    private static byte[] seal(SecretKeySpec key, long index, boolean last, byte[] data, int len) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv(index, last)));
        return c.doFinal(data, 0, len);
    }

    private static byte[] open(SecretKeySpec key, long index, boolean last, byte[] data) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv(index, last)));
        return c.doFinal(data);
    }

    /**
     * 加密輸出：每段 [旗標 1 位元組][長度 4 位元組][密文＋驗證碼]；旗標 1 = 最後一段。close() 一定送出最後一段，
     * 所以對方收不到最後一段（連線被切斷）就知道內容不完整。不會關閉底層串流。
     */
    static final class SealedOut extends OutputStream {
        private final OutputStream out;
        private final SecretKeySpec key;
        private final byte[] buf = new byte[CHUNK];
        private int n;
        private long index;
        private boolean closed;

        SealedOut(OutputStream out, byte[] encKey) {
            this.out = out;
            this.key = new SecretKeySpec(encKey, "AES");
        }

        @Override
        public void write(int b) throws IOException {
            write(new byte[]{(byte) b}, 0, 1);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            while (len > 0) {
                if (n == CHUNK) flushChunk(false);   // 滿了而且還有資料才送出，最後一段留給 close()
                int k = Math.min(len, CHUNK - n);
                System.arraycopy(b, off, buf, n, k);
                n += k;
                off += k;
                len -= k;
            }
        }

        private void flushChunk(boolean last) throws IOException {
            try {
                byte[] ct = seal(key, index++, last, buf, n);
                out.write(last ? 1 : 0);
                out.write(new byte[]{(byte) (ct.length >>> 24), (byte) (ct.length >>> 16), (byte) (ct.length >>> 8), (byte) ct.length});
                out.write(ct);
            } catch (GeneralSecurityException e) {
                throw new IOException(e);
            }
            n = 0;
        }

        @Override
        public void close() throws IOException {
            if (closed) return;
            closed = true;
            flushChunk(true);
            out.flush();
        }
    }

    /** 解密輸入：驗證每一段；沒收到最後一段就結束會丟 IOException（被截斷）。 */
    static final class OpenedIn extends InputStream {
        private final InputStream in;
        private final SecretKeySpec key;
        private byte[] cur = new byte[0];
        private int pos;
        private long index;
        private boolean ended;

        OpenedIn(InputStream in, byte[] encKey) {
            this.in = in;
            this.key = new SecretKeySpec(encKey, "AES");
        }

        private void readFully(byte[] b) throws IOException {
            int got = 0;
            while (got < b.length) {
                int r = in.read(b, got, b.length - got);
                if (r < 0) throw new IOException("資料不完整（連線被切斷）");
                got += r;
            }
        }

        private boolean fill() throws IOException {
            while (pos >= cur.length) {
                if (ended) return false;
                byte[] head = new byte[5];
                readFully(head);
                boolean last = head[0] == 1;
                int len = ((head[1] & 255) << 24) | ((head[2] & 255) << 16) | ((head[3] & 255) << 8) | (head[4] & 255);
                if (len < 16 || len > CHUNK + 64) throw new IOException("資料格式不對");
                byte[] ct = new byte[len];
                readFully(ct);
                try {
                    cur = open(key, index++, last, ct);
                } catch (GeneralSecurityException e) {
                    throw new IOException("驗證失敗（內容被改過或金鑰不對）");
                }
                pos = 0;
                if (last) ended = true;
            }
            return true;
        }

        @Override
        public int read() throws IOException {
            if (!fill()) return -1;
            return cur[pos++] & 255;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (len == 0) return 0;
            if (!fill()) return -1;
            int k = Math.min(len, cur.length - pos);
            System.arraycopy(cur, pos, b, off, k);
            pos += k;
            return k;
        }
    }
}
