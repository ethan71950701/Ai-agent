package com.phoneai.overlay;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.view.Surface;

/**
 * 跟 root 的 DirectDisplay 交換東西（Binder／Surface 都能透過 Binder 傳遞）：
 *   "register"：背景螢幕主機（--host）把自己的 Binder 交給 App（DirectHost），App 回傳自己的 uid 與一個 Binder（主機用來偵測 App 重啟）
 *   "surface"：【實驗頁】把 DirectTestActivity 的 Surface 交給 DirectDisplay
 * 沒有匯出（exported=false），而且只回應 root（uid 0）：其他 App 拿不到這個畫面。
 */
public class SurfaceProvider extends ContentProvider {
    static final String AUTHORITY = "com.phoneai.overlay.surface";
    static volatile Surface surface;
    private static final Binder ALIVE = new Binder();

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (Binder.getCallingUid() != 0) return null;
        if ("register".equals(method) && extras != null && extras.getBinder("host") != null) {
            DirectHost.register(extras.getBinder("host"), extras.getInt("display", -1));
            Bundle b = new Bundle();
            b.putInt("uid", android.os.Process.myUid());
            b.putBinder("app", ALIVE);
            return b;
        }
        if (!"surface".equals(method) || surface == null) return null;
        Bundle b = new Bundle();
        b.putParcelable("surface", surface);
        return b;
    }

    @Override public boolean onCreate() { return true; }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
