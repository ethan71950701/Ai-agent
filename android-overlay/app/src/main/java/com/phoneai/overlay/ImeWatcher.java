package com.phoneai.overlay;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * 預覽窗打開時，看主螢幕的鍵盤有沒有跳出來（跳出來就把預覽移到上面，免得擋住輸入框）。
 * 做法：用 root 每 1.2 秒跑一次（dumpsys 很重，太密會讓預覽卡） `dumpsys input_method` 找「鍵盤顯示中」的欄位，有變化才印 1／0。
 * 不用無障礙「讀取視窗內容」權限（那個看得到畫面上的東西，涉及隱私）。
 * 欄位名稱在不同 Android 版本可能不同（mInputShown／mIsInputViewShown；Android 17 沒驗證），抓不到就永遠是 0、預覽不會動。
 * 停止：關掉讀取端，迴圈下一次 echo 寫不出去就會自己結束。
 */
final class ImeWatcher {
    interface Listener { void onIme(boolean shown); }

    private static final String LOOP = "last=x; while :; do "
            + "if dumpsys input_method | grep -qE 'mInputShown=true|mIsInputViewShown=true'; then s=1; else s=0; fi; "
            + "if [ \"$s\" != \"$last\" ]; then echo $s || exit; last=$s; fi; sleep 1.2; done";

    private Process proc;

    void start(Listener l) {
        stop();
        final Handler ui = new Handler(Looper.getMainLooper());
        try {
            final Process p = new ProcessBuilder(Root.su(), "-c", LOOP).redirectErrorStream(true).start();
            proc = p;
            new Thread(() -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        final boolean shown = "1".equals(line.trim());
                        Log.i(OverlayService.TAG, "ime shown=" + shown);
                        ui.post(() -> l.onIme(shown));
                    }
                } catch (Exception ignored) {
                }
            }, "ime-watch").start();
        } catch (Exception e) {
            Log.w(OverlayService.TAG, "ime watch: " + e);
        }
    }

    void stop() {
        Process p = proc;
        proc = null;
        if (p != null) {
            try { p.getInputStream().close(); } catch (Exception ignored) { }
            p.destroy();
        }
    }
}
