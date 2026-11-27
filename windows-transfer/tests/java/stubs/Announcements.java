package com.phoneai.overlay;

/** 互通測試用的替身：真的 Announcements 要 Android 的設定檔；電腦上功能一律沒被停用。 */
final class Announcements {
    static boolean off(String flag) { return false; }
}
