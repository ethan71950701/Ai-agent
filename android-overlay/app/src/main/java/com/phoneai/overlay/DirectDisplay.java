package com.phoneai.overlay;

import android.app.Application;
import android.app.Instrumentation;
import android.content.AttributionSource;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.hardware.display.VirtualDisplay;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;
import android.view.Surface;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 【實驗】不經過影片壓縮的背景螢幕：用 root 建立虛擬螢幕，畫面「直接」畫進 App 的 TextureView。
 * 由 DirectTestActivity 用 `su -c "CLASSPATH=<apk> app_process /system/bin com.phoneai.overlay.DirectDisplay 寬 高 dpi"` 啟動：
 *   1. 向 App 的 SurfaceProvider（ContentProvider）要 Surface（Surface 可以透過 Binder 傳遞）
 *   2. 以 root 建立虛擬螢幕（旗標跟 scrcpy 的 new_display 一樣），輸出直接是那個 Surface
 *   3. 印出 "display <編號>"，之後跟 InputServer 一樣從標準輸入讀 D/M/U/C 觸控指令即時注入
 *   4. 標準輸入關閉（App 結束）就釋放虛擬螢幕
 * 正式用法（背景模式，vdisplay.py 啟動）：`DirectDisplay --host 寬 高 dpi`：
 *   虛擬螢幕平常畫到自己的 ImageReader（立刻丟棄，幾乎不花力氣）；把自己的 Binder 註冊給 App（SurfaceProvider "register"），
 *   App 打開預覽時把預覽的 Surface 交過來（setSurface），畫面就直接畫進預覽；觸控也走 Binder 即時注入。
 *   印出 "display <編號>" 表示就緒；標準輸入關閉（vdisplay.py 收到停止信號）就釋放虛擬螢幕並結束。
 * 建立虛擬螢幕的寫法參考 scrcpy（Apache License 2.0，Genymobile）的 Workarounds／FakeContext／DisplayManager。
 */
public final class DirectDisplay {
    private DirectDisplay() { }

    private static final String SHELL_PACKAGE = "com.android.shell";

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "--host".equals(args[0])) {
            host(Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]));
            return;
        }
        int w = Integer.parseInt(args[0]), h = Integer.parseInt(args[1]), dpi = Integer.parseInt(args[2]);
        Looper.prepareMainLooper();
        Context ctx = fakeContext();
        Surface surface = askSurface(ctx);
        if (surface == null) throw new IllegalStateException("App 沒給 Surface");

        VirtualDisplay vd = createVd(ctx, w, h, dpi, surface);
        int id = vd.getDisplay().getDisplayId();
        System.out.println("display " + id);
        System.out.flush();
        hideIme(id);
        Object im = inputManager();
        Method inject = im.getClass().getMethod("injectInputEvent", InputEvent.class, int.class);
        Method setDisplay = InputEvent.class.getMethod("setDisplayId", int.class);

        BufferedReader r = new BufferedReader(new InputStreamReader(System.in));
        long downTime = 0;
        String line;
        while ((line = r.readLine()) != null) {
            String[] p = line.trim().split("\\s+");
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
            injectTouch(im, inject, setDisplay, id, downTime, now, action, x, y);
        }
        vd.release();
    }

    // ---- 正式的背景螢幕主機 ----
    private static volatile long hostDown;

    private static void host(int w, int h, int dpi) throws Exception {
        Looper.prepareMainLooper();
        final Handler main = new Handler(Looper.getMainLooper());
        final Context ctx = fakeContext();
        // 沒有預覽時畫到這裡：每張畫面拿到就丟（不複製、不壓縮）
        final ImageReader reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2);
        final long[] fps = {System.nanoTime(), 0};
        reader.setOnImageAvailableListener(r -> {
            Image img = r.acquireLatestImage();
            if (img != null) img.close();
            fps[1]++;   // 預覽關著時「背景螢幕實際產出」的幀率（印在 vdisplay.log）；沒在動時本來就是 0
            long now = System.nanoTime();
            if (now - fps[0] >= 3_000_000_000L) {
                System.out.println(String.format(java.util.Locale.US, "host fps=%.1f", fps[1] * 1e9 / (now - fps[0])));
                System.out.flush();
                fps[0] = now;
                fps[1] = 0;
            }
        }, main);
        final VirtualDisplay vd = createVd(ctx, w, h, dpi, reader.getSurface());
        final int id = vd.getDisplay().getDisplayId();
        hideIme(id);
        final Object im = inputManager();
        final Method inject = im.getClass().getMethod("injectInputEvent", InputEvent.class, int.class);
        final Method setDisplay = InputEvent.class.getMethod("setDisplayId", int.class);
        final int[] appUid = {-1};

        final Binder hostBinder = new Binder() {
            @Override
            protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
                if (Binder.getCallingUid() != appUid[0]) return false;  // 只聽 App 的
                if (code == DirectHost.TX_SURFACE) {
                    final Surface s = data.readInt() == 1 ? Surface.CREATOR.createFromParcel(data) : null;
                    main.post(() -> vd.setSurface(s != null ? s : reader.getSurface()));
                    return true;
                }
                if (code == DirectHost.TX_KEY) {
                    int key = data.readInt();
                    long now = SystemClock.uptimeMillis();
                    for (int act = 0; act <= 1; act++) {  // 0 = ACTION_DOWN, 1 = ACTION_UP
                        android.view.KeyEvent ke = new android.view.KeyEvent(now, now, act, key, 0);
                        try {
                            setDisplay.invoke(ke, id);
                            inject.invoke(im, ke, 0);
                        } catch (Throwable t) {
                            System.out.println("key error " + t);
                            System.out.flush();
                        }
                    }
                    return true;
                }
                if (code == DirectHost.TX_TOUCH) {
                    int action = data.readInt(), x = data.readInt(), y = data.readInt();
                    long now = SystemClock.uptimeMillis();
                    if (action == MotionEvent.ACTION_DOWN) hostDown = now;
                    injectTouch(im, inject, setDisplay, id, hostDown, now, action, x, y);
                    return true;
                }
                return false;
            }
        };

        // 註冊給 App；App 重啟（例如更新）後 Binder 會死，自動重新註冊
        final Runnable[] reg = new Runnable[1];
        reg[0] = () -> {
            try {
                Bundle ex = new Bundle();
                ex.putBinder("host", hostBinder);
                ex.putInt("display", id);
                Bundle res = callProvider(ctx, "register", ex);
                if (res == null || res.getBinder("app") == null) throw new IllegalStateException("App 沒回應");
                appUid[0] = res.getInt("uid", -1);
                res.getBinder("app").linkToDeath(() -> {
                    main.post(() -> vd.setSurface(reader.getSurface()));
                    main.postDelayed(reg[0], 2000);
                }, 0);
                System.out.println("registered");
            } catch (Throwable t) {
                System.out.println("register failed: " + t);
                main.postDelayed(reg[0], 3000);
            }
            System.out.flush();
        };
        reg[0].run();
        System.out.println("display " + id);
        System.out.flush();

        Thread stdin = new Thread(() -> {  // 標準輸入關閉 = 收掉
            try {
                while (System.in.read() >= 0) { }
            } catch (Exception ignored) { }
            main.post(() -> {
                vd.release();
                reader.close();
                System.exit(0);
            });
        }, "stdin");
        stdin.start();
        Looper.loop();
    }

    private static void injectTouch(Object im, Method inject, Method setDisplay, int id, long downTime, long now, int action, float x, float y) {
        MotionEvent ev = MotionEvent.obtain(downTime, now, action, x, y, 0);
        ev.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try {
            setDisplay.invoke(ev, id);
            inject.invoke(im, ev, 0);
        } catch (Throwable t) {
            System.out.println("error " + t);
            System.out.flush();
        } finally {
            ev.recycle();
        }
    }

    private static Object inputManager() throws Exception {
        try {
            return Class.forName("android.hardware.input.InputManagerGlobal").getMethod("getInstance").invoke(null);
        } catch (Throwable t) {
            return Class.forName("android.hardware.input.InputManager").getMethod("getInstance").invoke(null);
        }
    }

    /** 背景螢幕不顯示鍵盤（跟 scrcpy 的 display_ime_policy=hide 一樣；2 = DISPLAY_IME_POLICY_HIDE）。失敗不影響。 */
    private static void hideIme(int id) {
        try {
            IBinder b = (IBinder) Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "window");
            Object wm = Class.forName("android.view.IWindowManager$Stub").getMethod("asInterface", IBinder.class).invoke(null, b);
            wm.getClass().getMethod("setDisplayImePolicy", int.class, int.class).invoke(wm, id, 2);
        } catch (Throwable t) {
            System.out.println("ime policy: " + t);
            System.out.flush();
        }
    }

    private static VirtualDisplay createVd(Context ctx, int w, int h, int dpi, Surface surface) throws Exception {
        int flags = (1 << 0)      // PUBLIC
                | (1 << 1)        // PRESENTATION
                | (1 << 3)        // OWN_CONTENT_ONLY
                | (1 << 6)        // SUPPORTS_TOUCH
                | (1 << 7)        // ROTATES_WITH_CONTENT
                | (1 << 8)        // DESTROY_CONTENT_ON_REMOVAL：收掉時裡面的 App 一起關，不要跑到主螢幕
                | (1 << 10)       // TRUSTED
                | (1 << 11)       // OWN_DISPLAY_GROUP
                | (1 << 12)       // ALWAYS_UNLOCKED
                | (1 << 13)       // TOUCH_FEEDBACK_DISABLED
                | (1 << 14)       // OWN_FOCUS
                | (1 << 15);      // DEVICE_DISPLAY_GROUP
        Constructor<android.hardware.display.DisplayManager> ctor =
                android.hardware.display.DisplayManager.class.getDeclaredConstructor(Context.class);
        ctor.setAccessible(true);
        return ctor.newInstance(ctx).createVirtualDisplay("phoneai-direct", w, h, dpi, surface, flags);
    }

    /** 向 App 的 SurfaceProvider 要 Surface（root 可以存取沒匯出的 provider）。 */
    private static Surface askSurface(Context ctx) throws Exception {
        Bundle b = callProvider(ctx, "surface", null);
        if (b == null) return null;
        b.setClassLoader(Surface.class.getClassLoader());
        return b.getParcelable("surface", Surface.class);
    }

    private static Bundle callProvider(Context ctx, String method, Bundle extras) throws Exception {
        Object am = Class.forName("android.app.ActivityManagerNative").getDeclaredMethod("getDefault").invoke(null);
        IBinder token = new Binder();
        Method get = am.getClass().getMethod("getContentProviderExternal", String.class, int.class, IBinder.class, String.class);
        Object holder = get.invoke(am, SurfaceProvider.AUTHORITY, 0, token, null);
        if (holder == null) throw new IllegalStateException("找不到 SurfaceProvider");
        Field pf = holder.getClass().getDeclaredField("provider");
        pf.setAccessible(true);
        Object provider = pf.get(holder);
        Method call = provider.getClass().getMethod("call", AttributionSource.class, String.class, String.class, String.class, Bundle.class);
        Bundle b = (Bundle) call.invoke(provider, ctx.getAttributionSource(), SurfaceProvider.AUTHORITY, method, null, extras);
        try {
            am.getClass().getMethod("removeContentProviderExternal", String.class, IBinder.class).invoke(am, SurfaceProvider.AUTHORITY, token);
        } catch (Exception ignored) { }
        return b;
    }

    /** app_process 裡沒有 Context：仿 scrcpy 造一個 ActivityThread 與假裝成 com.android.shell 的 Context。 */
    private static Context fakeContext() throws Exception {
        Class<?> atc = Class.forName("android.app.ActivityThread");
        Constructor<?> c = atc.getDeclaredConstructor();
        c.setAccessible(true);
        Object at = c.newInstance();
        set(atc, at, "sCurrentActivityThread", at);
        Field sys = atc.getDeclaredField("mSystemThread");
        sys.setAccessible(true);
        sys.setBoolean(at, true);
        try {  // 有些三星機需要 ConfigurationController（scrcpy issue 4467）
            Class<?> ccc = Class.forName("android.app.ConfigurationController");
            Constructor<?> cc = ccc.getDeclaredConstructor(Class.forName("android.app.ActivityThreadInternal"));
            cc.setAccessible(true);
            set(atc, at, "mConfigurationController", cc.newInstance(at));
        } catch (Throwable ignored) { }
        try {
            Class<?> abd = Class.forName("android.app.ActivityThread$AppBindData");
            Constructor<?> bc = abd.getDeclaredConstructor();
            bc.setAccessible(true);
            Object bind = bc.newInstance();
            ApplicationInfo ai = new ApplicationInfo();
            ai.packageName = SHELL_PACKAGE;
            set(abd, bind, "appInfo", ai);
            set(atc, at, "mBoundApplication", bind);
        } catch (Throwable ignored) { }
        Method gsc = atc.getDeclaredMethod("getSystemContext");
        gsc.setAccessible(true);
        Context fake = new ContextWrapper((Context) gsc.invoke(at)) {
            @Override public String getPackageName() { return SHELL_PACKAGE; }
            @Override public String getOpPackageName() { return SHELL_PACKAGE; }
            @Override public AttributionSource getAttributionSource() {
                return new AttributionSource.Builder(2000).setPackageName(SHELL_PACKAGE).build();
            }
            @Override public Context getApplicationContext() { return this; }
        };
        try {
            Application app = Instrumentation.newApplication(Application.class, fake);
            set(atc, at, "mInitialApplication", app);
        } catch (Throwable ignored) { }
        return fake;
    }

    private static void set(Class<?> cls, Object obj, String name, Object v) throws Exception {
        Field f = cls.getDeclaredField(name);
        f.setAccessible(true);
        f.set(obj, v);
    }
}
