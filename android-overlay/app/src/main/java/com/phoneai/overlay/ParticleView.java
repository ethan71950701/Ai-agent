package com.phoneai.overlay;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import java.util.Random;

/** 底部的粒子特效：一些小光點慢慢往上飄、忽明忽暗（很淡，不搶內容）。 */
final class ParticleView extends View {
    private static final int N = 14;
    private final float dens;
    private final float[] x = new float[N], y = new float[N], r = new float[N], sp = new float[N], ph = new float[N];
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rnd = new Random(7);
    private int color = 0xFFFFFFFF;

    ParticleView(Context c) {
        super(c);
        dens = c.getResources().getDisplayMetrics().density;
        for (int i = 0; i < N; i++) {
            x[i] = rnd.nextFloat();
            y[i] = rnd.nextFloat();
            r[i] = (2f + rnd.nextFloat() * 2.5f) * dens;   // 大一點，才看得見
            sp[i] = 0.02f + rnd.nextFloat() * 0.04f;
            ph[i] = rnd.nextFloat() * 6.28f;
        }
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(60000);
        va.setRepeatCount(ValueAnimator.INFINITE);
        va.addUpdateListener(a -> invalidate());
        va.start();
    }

    void setColor(int c) { color = c; invalidate(); }

    @Override
    protected void onDraw(Canvas canvas) {
        long t = System.currentTimeMillis();
        float w = getWidth(), h = getHeight();
        for (int i = 0; i < N; i++) {
            // 往上飄，走到頂端就從底部重來
            float yy = (y[i] - (t / 1000f) * sp[i]) % 1f;
            if (yy < 0) yy += 1f;
            float alpha = 0.4f + 0.3f * (float) Math.sin(t / 900.0 + ph[i]);
            p.setColor(color);
            p.setAlpha((int) (255 * Math.max(0.15f, Math.min(0.7f, alpha)) * (1f - yy * 0.5f)));
            canvas.drawCircle(x[i] * w, yy * h, r[i], p);
        }
        postInvalidateOnAnimation();
    }
}
