package com.phoneai.overlay;

/** 互通測試用：印出手機版算出的請求／回應金鑰與加上 dk=1 的網址，給 Python 版比對。參數：金鑰(hex) 隨機數 網址。 */
public class KeyCheck {
    public static void main(String[] a) {
        byte[] key = TransferCrypto.unhex(a[0]);
        System.out.println(TransferCrypto.hex(TransferCrypto.encKey(key, a[1])));
        System.out.println(TransferCrypto.hex(TransferCrypto.respKey(key, a[1])));
        System.out.println(TransferCrypto.withDk(a[2]));
    }
}
