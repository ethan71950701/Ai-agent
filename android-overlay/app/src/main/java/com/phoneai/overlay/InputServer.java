package com.phoneai.overlay;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

/**
 * 用 root 即時把觸控事件送到指定螢幕（背景虛擬螢幕）的小程式。由 LiveInput 用
 * `su -c "CLASSPATH=<apk> app_process /system/bin com.phoneai.overlay.InputServer <螢幕編號>"` 啟動，常駐著從標準輸入讀指令：
 *   D x y   手指按下      M x y   手指移動      U x y   手指放開      C   取消
 * 為什麼不用 `input swipe`：它每次都要啟動一個 root 殼＋Java 程序（約 0.3～0.6 秒），而且只能送「一整個手勢」，
 * 沒辦法讓背景螢幕跟著手指即時動。這裡一次啟動、之後每個事件直接呼叫 InputManager.injectInputEvent（跟 scrcpy 同樣的做法）。
 * 這個類別跑在 app_process（不是 App 程序），所以用反射呼叫隱藏 API 不受限制；以 root 身分執行才有 INJECT_EVENTS 權限。
 * 狀態：尚未在手機實測；啟動失敗時 LiveInput 不會 ready，預覽窗退回舊的「放開後才送」做法。
 */
public final class InputServer {
    private InputServer() { }

    public static void main(String[] args) throws Exception {
        final int display = Integer.parseInt(args[0]);
        Object im;
        try {  // Android 14 以後用 InputManagerGlobal，更早的版本直接是 InputManager
            im = Class.forName("android.hardware.input.InputManagerGlobal").getMethod("getInstance").invoke(null);
        } catch (Throwable t) {
            im = Class.forName("android.hardware.input.InputManager").getMethod("getInstance").invoke(null);
        }
        final Method inject = im.getClass().getMethod("injectInputEvent", InputEvent.class, int.class);
        final Method setDisplay = InputEvent.class.getMethod("setDisplayId", int.class);
        System.out.println("ready");
        System.out.flush();
        BufferedReader r = new BufferedReader(new InputStreamReader(System.in));
        long downTime = 0;
        String line;
        while ((line = r.readLine()) != null) {
            String[] p = line.trim().split("\\s+");
            if (p.length == 0 || p[0].isEmpty()) continue;
            int action;
            switch (p[0]) {
                case "D": action = MotionEvent.ACTION_DOWN; break;
                case "M": action = MotionEvent.ACTION_MOVE; break;
                case "U": action = MotionEvent.ACTION_UP; break;
                case "C": action = MotionEvent.ACTION_CANCEL; break;
                default: continue;
            }
            float x = p.length > 1 ? Float.parseFloat(p[1]) : 0f, y = p.length > 2 ? Float.parseFloat(p[2]) : 0f;
            long now = SystemClock.uptimeMillis();
            if (action == MotionEvent.ACTION_DOWN) downTime = now;
            MotionEvent ev = MotionEvent.obtain(downTime, now, action, x, y, 0);
            ev.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            try {
                setDisplay.invoke(ev, display);
                inject.invoke(im, ev, 0);  // 0 = INJECT_INPUT_EVENT_MODE_ASYNC：不等結果，延遲最低
            } catch (Throwable t) {
                System.out.println("error " + t);
                System.out.flush();
            } finally {
                ev.recycle();
            }
        }
    }
}
