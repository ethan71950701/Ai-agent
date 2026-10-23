package com.phoneai.overlay;

import java.io.File;

/** 互通測試用：用手機版的 Java 伺服器，讓 Windows 版（Python）當客戶端來連。參數：埠 金鑰(hex) 根目錄 收件匣。 */
public class InteropServer {
    public static void main(String[] a) throws Exception {
        final byte[] key = TransferCrypto.unhex(a[1]);
        final File root = new File(a[2]), inbox = new File(a[3]);
        TransferServer s = new TransferServer(new TransferServer.Host() {
            public String myId() { return "javapc"; }
            public String myName() { return "JavaPC"; }
            public byte[] keyOf(String peerId) { return peerId.equals("pyphone") ? key : null; }
            public File root() { return root; }
            public File inbox() { return inbox; }
            public boolean pairingOpen() { return true; }
            public void pairRequest(String peerId, String peerName, byte[] k, String sas) {
                System.out.println("PAIR " + peerId + " " + peerName + " " + sas + " " + TransferCrypto.hex(k));
                System.out.flush();
            }
            public String pairState(String peerId) { return "none"; }
            public void event(String peerId, String kind, String what, long size) {
                System.out.println("EVENT " + kind + " " + size);
                System.out.flush();
            }
            public void peerName(String peerId, String name) { System.out.println("NAME " + name); System.out.flush(); }
            public void progress(String peerId, boolean toPeer, String name, long done, long total) { }
            public void transferEnded() { }
        });
        s.start(Integer.parseInt(a[0]));
        System.out.println("READY");
        System.out.flush();
        Thread.sleep(Long.MAX_VALUE);
    }
}
