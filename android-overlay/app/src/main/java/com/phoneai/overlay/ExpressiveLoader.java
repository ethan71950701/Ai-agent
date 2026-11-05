package com.phoneai.overlay;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * 載入動畫（仿 Material 3 Expressive 的 Loading indicator）：主題色（莫奈取色）的形狀在圓、橢圓、圓角五邊形、圓角方、軟三角之間
 * 依序變形，同時慢慢旋轉。所有形狀都沒有尖角；沒有底圓，所以比一圈細線的轉圈圈更有個性。
 */
final class ExpressiveLoader extends View {
    private static final int SAMPLES = 140;
    private static final int STEP_MS = 1400;   // 一個形狀換到下一個的週期：蓄力 → 突然加速旋轉並換形 → 輕輕回彈
    private static final double STEP_TURN = Math.PI * 2 / 5 * 2;   // 每次換形旋轉 144 度（跟五邊形的對稱一致，轉完看起來很自然）
    // 形狀順序（循環）：圓、圓角五邊形、橫橢圓、圓角五邊形、直橢圓
    private static final int[] SEQ = {0, 3, 1, 3, 2};
    private final Paint shape = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float sizePx;
    private ValueAnimator anim;
    private long start;
    private float time;

    ExpressiveLoader(Context c) {
        super(c);
        shape.setColor(Ui.attr(c, com.google.android.material.R.attr.colorPrimary));
        sizePx = Ui.dp(c, 96);
    }

    /** 形狀的相對半徑（角度 th，已扣掉旋轉）。0 圓、1 橫橢圓、2 直橢圓、3 圓角五邊形。 */
    private static double radius(int shape, double th) {
        switch (shape) {
            case 1: return ellipse(th, 1.22, 0.82);
            case 2: return ellipse(th + Math.PI / 2, 1.22, 0.82);
            case 3: return roundedPoly(th, 5, 14, 0.985);
            default: return 1.0;
        }
    }

    private static double ellipse(double th, double a, double b) {
        return a * b / Math.sqrt(Math.pow(b * Math.cos(th), 2) + Math.pow(a * Math.sin(th), 2));
    }

    /** 圓角多邊形：凸多邊形沿某方向的半徑是 1 / max(方向·邊的法線)，把 max 換成 k 次方的平滑最大值，角就變圓；scale 讓各形狀大小接近。 */
    private static double roundedPoly(double th, int n, double k, double scale) {
        double sum = 0;
        for (int i = 0; i < n; i++) {
            double d = Math.cos(th - i * 2 * Math.PI / n);
            if (d > 0) sum += Math.pow(d, k);
        }
        return scale / Math.pow(sum, 1.0 / k);
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(resolveSize((int) sizePx, w), resolveSize((int) sizePx, h));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        start = android.os.SystemClock.uptimeMillis();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(1000);
        anim.setInterpolator(new LinearInterpolator());
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.addUpdateListener(a -> { time = android.os.SystemClock.uptimeMillis() - start; invalidate(); });
        anim.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (anim != null) anim.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f, base = Math.min(getWidth(), getHeight()) * 0.36f;
        long steps = (long) (time / STEP_MS);
        float p = (time % STEP_MS) / STEP_MS;
        float u = Math.max(0f, Math.min(1f, (p - 0.28f) / 0.58f));   // 前 28% 蓄力不動，接著快速完成，最後一小段留給回彈
        double c1 = 1.2, c3 = c1 + 1;
        double back = 1 + c3 * Math.pow(u - 1, 3) + c1 * Math.pow(u - 1, 2);   // easeOutBack：衝過頭一點再回來
        float f = u * u * (3 - 2 * u);   // 形狀變化：在旋轉衝出去的過程中換好，轉過去時已經是新形狀
        int a = SEQ[(int) (steps % SEQ.length)], b = SEQ[(int) ((steps + 1) % SEQ.length)];
        double rot = (steps + back) * STEP_TURN;
        path.reset();
        for (int k = 0; k <= SAMPLES; k++) {
            double th = k * Math.PI * 2 / SAMPLES, ang = th - rot;
            double ra = radius(a, ang), rb = radius(b, ang);
            double r = base * (ra + (rb - ra) * f);
            float x = cx + (float) (r * Math.cos(th)), y = cy + (float) (r * Math.sin(th));
            if (k == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        path.close();
        canvas.drawPath(path, shape);
    }
}
