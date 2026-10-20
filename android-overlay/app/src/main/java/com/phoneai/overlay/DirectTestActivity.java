package com.phoneai.overlay;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;

/**
 * 【實驗】直接顯示背景螢幕：不經過 scrcpy、影片壓縮與 Termux 轉送。
 * 開啟後用 root 啟動 DirectDisplay 建立虛擬螢幕（輸出 = 這頁的 TextureView），在上面開「設定」，觸控即時送過去。
 * 啟動：su -c "am start -n com.phoneai.overlay/.DirectTestActivity"；離開這頁就收掉虛擬螢幕。
 */
public class DirectTestActivity extends Activity implements TextureView.SurfaceTextureListener {
    private static final String TAG = OverlayService.TAG;
    private static final int VW = 720, VH = 1200;   // 跟背景模式的預設（720x1200/280）一樣，方便對照
    private TextureView tv;
    private TextView status;
    private Process proc;
    private Writer out;
    private volatile int displayId = -1;
    private long lastMove;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF202020);
        int w = (int) (getResources().getDisplayMetrics().widthPixels * 0.8f);
        tv = new TextureView(this);
        tv.setSurfaceTextureListener(this);
        root.addView(tv, new FrameLayout.LayoutParams(w, w * VH / VW, Gravity.CENTER));
        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(15);
        status.setPadding(40, 120, 40, 40);
        status.setText("直接顯示實驗：啟動中…");
        root.addView(status, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
        setContentView(root);
    }

    private void show(String s) {
        Log.i(TAG, "direct test: " + s);
        runOnUiThread(() -> status.setText("直接顯示實驗：" + s));
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture st, int width, int height) {
        st.setDefaultBufferSize(VW, VH);
        SurfaceProvider.surface = new Surface(st);
        int dpi = getResources().getDisplayMetrics().densityDpi * VW / getResources().getDisplayMetrics().widthPixels;
        final String cmd = "CLASSPATH=" + getPackageCodePath() + " app_process /system/bin com.phoneai.overlay.DirectDisplay "
                + VW + " " + VH + " " + dpi;
        new Thread(() -> run(cmd), "direct-test").start();
    }

    private void run(String cmd) {
        try {
            proc = new ProcessBuilder(Root.su(), "-c", cmd).redirectErrorStream(true).start();
            out = new OutputStreamWriter(proc.getOutputStream());
            try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                StringBuilder err = new StringBuilder();
                while ((line = r.readLine()) != null) {
                    Log.i(TAG, "direct test out: " + line);
                    if (line.startsWith("display ")) {
                        displayId = Integer.parseInt(line.substring(8).trim());
                        show("背景螢幕 " + displayId + " 已建立，正在開設定…（在下面畫面上滑動試試）");
                        new ProcessBuilder(Root.su(), "-c", "am start --display " + displayId
                                + " -n com.android.settings/.Settings").redirectErrorStream(true).start().waitFor();
                    } else if (err.length() < 600) {
                        err.append(line).append('\n');
                        if (displayId < 0) show("失敗（請截圖或看 logcat）：\n" + err);
                    }
                }
            }
            show("已結束");
        } catch (Exception e) {
            show("錯誤 " + e);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        Writer w = out;
        if (w == null || displayId < 0) return super.dispatchTouchEvent(e);
        int[] loc = new int[2];
        tv.getLocationOnScreen(loc);
        int x = (int) ((e.getRawX() - loc[0]) * VW / tv.getWidth());
        int y = (int) ((e.getRawY() - loc[1]) * VH / tv.getHeight());
        String line = null;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: line = "D " + x + " " + y; break;
            case MotionEvent.ACTION_MOVE:
                if (e.getEventTime() - lastMove >= 8) { lastMove = e.getEventTime(); line = "M " + x + " " + y; }
                break;
            case MotionEvent.ACTION_UP: line = "U " + x + " " + y; break;
            case MotionEvent.ACTION_CANCEL: line = "C"; break;
        }
        if (line != null) {
            try { w.write(line + "\n"); w.flush(); } catch (Exception ignored) { }
        }
        return true;
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
        stop();
        return true;
    }

    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) { }
    private long fpsT0 = System.nanoTime();
    private int fpsN;

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture st) {
        fpsN++;   // 跟預覽視窗同樣的量法（A/B 對照：同一個背景螢幕，在一般 Activity 裡能到幾 fps）
        long now = System.nanoTime();
        if (now - fpsT0 >= 3_000_000_000L) {
            Log.i(TAG, String.format(java.util.Locale.US, "direct test fps=%.1f", fpsN * 1e9 / (now - fpsT0)));
            fpsT0 = now;
            fpsN = 0;
        }
    }

    @Override
    protected void onDestroy() {
        stop();
        super.onDestroy();
    }

    private void stop() {
        Process p = proc;
        proc = null;
        out = null;
        if (p != null) {
            try { p.getOutputStream().close(); } catch (Exception ignored) { }  // DirectDisplay 讀到結尾就釋放虛擬螢幕
            p.destroy();
        }
        SurfaceProvider.surface = null;
    }
}
