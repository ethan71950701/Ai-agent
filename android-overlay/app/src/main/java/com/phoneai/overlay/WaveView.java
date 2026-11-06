package com.phoneai.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

/**
 * 聲波條（取代以前的光球）：聽的時候一排圓角直條隨音量上下跳動，思考時變成一道來回掃過的波。
 * 狀態與 {@link #setState}／{@link #setLevel} 沿用光球的用法，所以面板其他程式不用改。
 */
public class WaveView extends View {
    public static final int IDLE = 0, LISTENING = 1, THINKING = 2, DONE = 3;

    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float d;
    private int state = IDLE;
    private float amp, ampTarget;
    private long last;

    public WaveView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        bar.setColor(0xFFFFFFFF);
    }

    public void setState(int s) {
        state = s;
        invalidate();
    }

    /** 0~1，音量。 */
    public void setLevel(float v) { ampTarget = Math.max(0f, Math.min(1f, v)); }

    @Override
    protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        float dt = last == 0 ? 0.016f : Math.min(0.05f, (now - last) / 1000f);
        last = now;
        float t = now / 1000f;
        float goal = state == LISTENING ? ampTarget : (state == THINKING ? 0.45f : 0f);
        amp += (goal - amp) * Math.min(1f, dt * 12f);

        float w = getWidth(), h = getHeight();
        float bw = 3.2f * d, gap = 4.6f * d;
        int n = Math.max(5, (int) ((w + gap) / (bw + gap)));
        float total = n * bw + (n - 1) * gap;
        float x0 = (w - total) / 2f, cy = h / 2f;
        float minH = 4f * d, maxH = h * 0.92f;
        for (int i = 0; i < n; i++) {
            float u = (i + 0.5f) / n;                                   // 0~1，由左到右
            float env = 0.35f + 0.65f * (float) Math.sin(Math.PI * u);  // 中間高、兩邊低
            float wob;
            if (state == THINKING) {
                wob = 0.5f + 0.5f * (float) Math.sin(t * 5.2f - i * 0.55f);   // 一道來回掃的波
            } else {
                // 每條有自己的相位，聽的時候不會整排一起動
                wob = 0.5f + 0.5f * (float) Math.sin(t * (7f + (i % 5)) + i * 1.7f) * (0.6f + 0.4f * (float) Math.sin(t * 2.3f + i));
            }
            float hh = minH + (maxH - minH) * Math.max(0f, Math.min(1f, amp * env * (0.25f + 0.75f * wob) * 1.15f));
            float x = x0 + i * (bw + gap);
            bar.setAlpha(state == IDLE ? 90 : 255);
            r.set(x, cy - hh / 2f, x + bw, cy + hh / 2f);
            c.drawRoundRect(r, bw / 2f, bw / 2f, bar);
        }
        if (state != IDLE || amp > 0.01f) postInvalidateOnAnimation();
    }
}
