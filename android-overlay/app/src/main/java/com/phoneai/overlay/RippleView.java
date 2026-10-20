package com.phoneai.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;

/** 全螢幕透明畫布：畫點擊漣漪與滑動軌跡。座標是螢幕實際像素（與 input tap 相同）。 */
public class RippleView extends View {
    static final long RING_MS = 450;      // 每一圈擴散多久
    static final long RING_GAP = 90;      // 圈與圈的間隔
    static final int RINGS = 3;
    static final long SWIPE_MS = 600;
    static final int COLOR = 0xFF4FC3F7;  // 淺藍

    static final long HOLD_FADE_MS = 180;  // 長按結束後，外圈淡出的時間

    private static class Fx {
        final boolean swipe;
        final long holdMs;   // > 0 = 長按：一圈慢慢往外擴，剛好在放手時到最外圈
        final float x1, y1, x2, y2;
        final long start;
        Fx(boolean swipe, float x1, float y1, float x2, float y2, long holdMs, long delay) {
            this.swipe = swipe; this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2; this.holdMs = holdMs;
            this.start = SystemClock.uptimeMillis() + delay;  // delay：按住之後才開始的軌跡（按住拖曳）
        }
        long duration() { return holdMs > 0 ? holdMs + HOLD_FADE_MS : (swipe ? SWIPE_MS : RING_MS + RING_GAP * (RINGS - 1)); }
    }

    private final ArrayList<Fx> fx = new ArrayList<>();
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private final int[] loc = new int[2];

    public RippleView(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(3 * density);
        ring.setStrokeCap(Paint.Cap.ROUND);
        fill.setStyle(Paint.Style.FILL);
    }

    public void ripple(float x, float y) { add(new Fx(false, x, y, x, y, 0, 0)); }

    public void swipe(float x1, float y1, float x2, float y2) { add(new Fx(true, x1, y1, x2, y2, 0, 0)); }

    public void swipe(float x1, float y1, float x2, float y2, long delayMs) { add(new Fx(true, x1, y1, x2, y2, 0, delayMs)); }

    /** 長按：沒有按下當下的漣漪，只有一圈從手指位置慢慢往外擴，放手的那一刻剛好到最外圈，然後淡出。 */
    public void hold(float x, float y, long ms) { add(new Fx(false, x, y, x, y, Math.max(200, ms), 0)); }

    public void clear() {
        fx.clear();
        invalidate();
    }

    private void add(Fx f) {
        fx.add(f);
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas c) {
        // 視窗不一定從螢幕 (0,0) 開始（狀態列/挖孔），換算成視窗內座標
        getLocationOnScreen(loc);
        c.translate(-loc[0], -loc[1]);
        long now = SystemClock.uptimeMillis();
        Iterator<Fx> it = fx.iterator();
        while (it.hasNext()) {
            Fx f = it.next();
            long t = now - f.start;
            if (t < 0) continue;  // 還沒到開始時間
            if (t > f.duration()) {
                it.remove();
                continue;
            }
            if (f.holdMs > 0) drawHold(c, f, t); else if (f.swipe) drawSwipe(c, f, t); else drawRipple(c, f, t);
        }
        if (!fx.isEmpty()) postInvalidateOnAnimation();
    }

    private void drawHold(Canvas c, Fx f, long t) {
        float maxR = 56 * density;
        float p = Math.min(1f, t / (float) f.holdMs);
        float ease = p * p * (3 - 2 * p);  // 慢起慢收，剛好在放手時到最外圈
        float fade = t <= f.holdMs ? 1f : Math.max(0f, 1f - (t - f.holdMs) / (float) HOLD_FADE_MS);
        // 最外圈的參考線：細細的灰色圓，讓人知道擴散的圈會停在哪裡
        ring.setStrokeWidth(Math.max(1f, density));
        ring.setColor(withAlpha(0xFFB0B0B0, (int) (110 * fade)));
        c.drawCircle(f.x1, f.y1, maxR, ring);
        fill.setColor(withAlpha(COLOR, (int) (90 * fade)));
        c.drawCircle(f.x1, f.y1, 7 * density, fill);   // 手指按著的位置（淡淡的一點）
        ring.setStrokeWidth((3.2f - 1.4f * p) * density);
        ring.setColor(withAlpha(COLOR, (int) ((120 + 135 * p) * fade)));
        c.drawCircle(f.x1, f.y1, 7 * density + (maxR - 7 * density) * ease, ring);
        ring.setStrokeWidth(3 * density);
    }

    private void drawRipple(Canvas c, Fx f, long t) {
        float maxR = 48 * density;
        for (int i = 0; i < RINGS; i++) {
            float p = (t - i * RING_GAP) / (float) RING_MS;
            if (p < 0 || p > 1) continue;
            float ease = 1 - (1 - p) * (1 - p);
            ring.setColor(withAlpha(COLOR, (int) (255 * (1 - p))));
            c.drawCircle(f.x1, f.y1, 6 * density + maxR * ease, ring);
        }
        float p = t / (float) f.duration();
        fill.setColor(withAlpha(COLOR, (int) (200 * (1 - p))));
        c.drawCircle(f.x1, f.y1, 8 * density, fill);
    }

    private void drawSwipe(Canvas c, Fx f, long t) {
        float p = t / (float) SWIPE_MS;
        float grow = Math.min(1f, p * 2.5f);  // 前 40% 時間畫出軌跡，之後淡出
        float ex = f.x1 + (f.x2 - f.x1) * grow, ey = f.y1 + (f.y2 - f.y1) * grow;
        int a = (int) (255 * (p < 0.4f ? 1f : (1 - p) / 0.6f));
        ring.setColor(withAlpha(COLOR, a));
        ring.setStrokeWidth(6 * density);
        c.drawLine(f.x1, f.y1, ex, ey, ring);
        ring.setStrokeWidth(3 * density);
        fill.setColor(withAlpha(COLOR, a));
        c.drawCircle(ex, ey, 10 * density, fill);
    }

    private static int withAlpha(int color, int a) {
        return (Math.max(0, Math.min(255, a)) << 24) | (color & 0xFFFFFF);
    }
}
