package com.phoneai.overlay;

/** 互通測試用的替身：真的 TransferNotify 要 Android 的通知；電腦上只留傳輸程式碼用到的部分（永遠不會被按停止／暫停）。 */
final class TransferNotify {
    static final class Stopped extends RuntimeException {
        Stopped() { super("stopped"); }
    }

    static final class Paused extends RuntimeException {
        Paused() { super("paused"); }
    }

    static boolean pauseRequested() { return false; }

    static boolean stopRequested() { return false; }

    static void bindCloser(java.io.Closeable c) { }
}
