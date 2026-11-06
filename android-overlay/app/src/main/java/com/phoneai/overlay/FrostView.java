package com.phoneai.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RecordingCanvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/**
 * 頂欄的毛玻璃（實驗）：把「它底下那一塊內容」模糊後當背景，上面再蓋一層淡淡的底色與一條細線。
 * 不折射，只有模糊（比底欄的液態玻璃單純）。內容來源是捲動區；捲動時、版面變動時、換分頁後的一小段時間會重畫。
 */
final class FrostView extends View {
    private View source;
    private RenderNode node = new RenderNode("frost");
    private final Paint tint = new Paint();
    private final Paint line = new Paint();
    private final float d;
    private long until;

    FrostView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
        int surface = Ui.attr(c, com.google.android.material.R.attr.colorSurface);
        int onSurface = Ui.attr(c, com.google.android.material.R.attr.colorOnSurface);
        tint.setColor((surface & 0x00FFFFFF) | 0x88000000);   // 底色約 53% 不透明：模糊的內容隱約透出來，字還是看得清楚
        line.setColor((onSurface & 0x00FFFFFF) | 0x1A000000);   // 底部一條很淡的細線，分出玻璃的邊
        line.setStrokeWidth(Math.max(1f, d * 0.8f));
    }

    void setSource(View s) {
        source = s;
        if (s != null) s.getViewTreeObserver().addOnScrollChangedListener(this::invalidate);
    }

    /** 接下來 ms 毫秒內每一幀都重畫（換分頁、卡片進場動畫時內容在動，但不是捲動）。 */
    void kick(long ms) {
        until = Math.max(until, SystemClock.uptimeMillis() + ms);
        invalidate();
    }

    /** 回到前景：換新的繪圖節點（跟底欄玻璃一樣，避免舊的繪圖資源壞掉）。 */
    void reset() {
        try {
            node = new RenderNode("frost");
        } catch (Throwable ignored) { }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (source == null || w <= 0 || h <= 0) return;
        if (!canvas.isHardwareAccelerated()) {   // 沒有硬體加速：不模糊，只蓋底色
            canvas.drawRect(0, 0, w, h, tint);
            return;
        }
        try {
            int[] a = new int[2], b = new int[2];
            getLocationOnScreen(a);
            source.getLocationOnScreen(b);
            node.setPosition(0, 0, w, h);
            float r = 14f * d;
            node.setRenderEffect(RenderEffect.createBlurEffect(r, r, Shader.TileMode.MIRROR));
            RecordingCanvas rc = node.beginRecording(w, h);
            rc.drawColor(Ui.attr(getContext(), com.google.android.material.R.attr.colorSurface));
            rc.translate(b[0] - a[0], b[1] - a[1]);   // 捲動區在這塊玻璃座標裡的位置
            source.draw(rc);
            node.endRecording();
            canvas.drawRenderNode(node);
        } catch (Throwable t) {
            CrashLog.w("頂欄毛玻璃", t);
            canvas.drawRect(0, 0, w, h, tint);
        }
        canvas.drawRect(0, 0, w, h, tint);
        canvas.drawLine(0, h - line.getStrokeWidth() / 2f, w, h - line.getStrokeWidth() / 2f, line);
        if (SystemClock.uptimeMillis() < until) postInvalidateOnAnimation();
    }
}
