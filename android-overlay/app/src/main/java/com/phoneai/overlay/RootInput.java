package com.phoneai.overlay;

import android.util.Log;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 使用者「接手」背景螢幕時，把預覽上的手勢變成 `input -d <螢幕編號> …` 指令，用 root 送到背景虛擬螢幕。
 * 所有參數都是整數、由程式自己組字串，沒有任何使用者輸入的文字進到 shell（輸入文字走剪貼簿貼上，見 TakeoverInputActivity）。
 * 單一執行緒依序執行，手勢順序不會亂；每個指令約 0.2~0.4 秒（input 指令本身要啟動一個 Java 程序），所以是「一個手勢一個指令」。
 */
final class RootInput {
    private static final ExecutorService EX = Executors.newSingleThreadExecutor();

    private RootInput() { }

    static void run(String cmd) {
        EX.execute(() -> {
            try {
                Process p = new ProcessBuilder(Root.su(), "-c", cmd).redirectErrorStream(true).start();
                try (InputStream in = p.getInputStream()) {
                    byte[] buf = new byte[1024];
                    while (in.read(buf) > 0) { /* 丟掉輸出 */ }
                }
                p.waitFor();
            } catch (Exception e) {
                Log.e(OverlayService.TAG, "root input: " + cmd, e);
            }
        });
    }

    static void tap(int display, int x, int y) {
        if (display > 0) run("input -d " + display + " tap " + x + " " + y);
    }

    static void swipe(int display, int x1, int y1, int x2, int y2, int ms) {
        if (display > 0) run("input -d " + display + " swipe " + x1 + " " + y1 + " " + x2 + " " + y2 + " " + ms);
    }

    static void key(int display, int keycode) {
        if (display > 0) run("input -d " + display + " keyevent " + keycode);
    }
}
