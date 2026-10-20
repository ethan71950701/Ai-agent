package com.phoneai.overlay;

import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;
import android.view.Surface;

/**
 * App 這邊對「直接顯示」背景螢幕主機（root 的 DirectDisplay --host）的連線。
 * 主機建好虛擬螢幕後透過 SurfaceProvider 把自己的 Binder 交給 App（register）；之後 App 用它：
 *   setSurface：預覽打開時把預覽的 Surface 交過去（畫面直接畫進預覽，不經影片壓縮），關閉時交 null（主機改畫到自己的 ImageReader）
 *   touch：即時觸控（單向呼叫，不等回應）
 * 主機沒註冊、已經死掉或螢幕編號對不上時 ready() 為 false，呼叫端退回舊做法（H.264 串流、LiveInput）。
 */
final class DirectHost {
    static final int TX_SURFACE = IBinder.FIRST_CALL_TRANSACTION, TX_TOUCH = IBinder.FIRST_CALL_TRANSACTION + 1,
            TX_KEY = IBinder.FIRST_CALL_TRANSACTION + 2;
    private static volatile IBinder host;
    private static volatile int display = -1;

    private DirectHost() { }

    static void register(IBinder b, int displayId) {
        host = b;
        display = displayId;
        Log.i(OverlayService.TAG, "direct host registered, display " + displayId);
    }

    static boolean ready(int displayId) {
        IBinder b = host;
        return b != null && displayId > 0 && display == displayId && b.isBinderAlive();
    }

    static boolean setSurface(Surface s) {
        IBinder b = host;
        if (b == null) return false;
        Parcel p = Parcel.obtain();
        try {
            if (s != null) { p.writeInt(1); s.writeToParcel(p, 0); }
            else p.writeInt(0);
            return b.transact(TX_SURFACE, p, null, 0);
        } catch (Exception e) {
            Log.w(OverlayService.TAG, "direct host surface: " + e);
            return false;
        } finally {
            p.recycle();
        }
    }

    /** 按一下實體鍵（例如 4 = 返回）；送出去了回 true。 */
    static boolean key(int keycode) {
        IBinder b = host;
        if (b == null) return false;
        Parcel p = Parcel.obtain();
        try {
            p.writeInt(keycode);
            b.transact(TX_KEY, p, null, IBinder.FLAG_ONEWAY);
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            p.recycle();
        }
    }

    /** action：0 按下、1 放開、2 移動、3 取消（同 MotionEvent）；x、y 是背景螢幕座標。 */
    static void touch(int action, int x, int y) {
        IBinder b = host;
        if (b == null) return;
        Parcel p = Parcel.obtain();
        try {
            p.writeInt(action);
            p.writeInt(x);
            p.writeInt(y);
            b.transact(TX_TOUCH, p, null, IBinder.FLAG_ONEWAY);
        } catch (Exception ignored) {
        } finally {
            p.recycle();
        }
    }
}
