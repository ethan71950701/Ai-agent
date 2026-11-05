package com.phoneai.overlay;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * 載入動畫（仿 Material 3 Expressive 的 Loading indicator）：主題色（莫奈取色）的圓角形狀一邊旋轉、一邊像花瓣一樣變形呼吸，
 * 底下一顆淡色圓底。比一圈細線的轉圈圈更有個性，顏色跟著系統桌布。
 */
final class ExpressiveLoader extends View {
    private final Paint shape = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint plate = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private ValueAnimator anim;
    private float t;   // 0..1 循環一次

    ExpressiveLoader(Context c) {
        super(c);
        shape.setColor(Ui.attr(c, com.google.android.material.R.attr.colorPrimary));
        plate.setColor(Ui.attr(c, com.google.android.material.R.attr.colorPrimaryContainer));
        setMinimumWidth(Ui.dp(c, 64));
        setMinimumHeight(Ui.dp(c, 64));
    }

    @Override
    protected void onMeasure(int w, int h) {
        int s = Ui.dp(getContext(), 64);
        setMeasuredDimension(resolveSize(s, w), resolveSize(s, h));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(3200);
        anim.setInterpolator(new LinearInterpolator());
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.addUpdateListener(a -> { t = (float) a.getAnimatedValue(); invalidate(); });
        anim.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (anim != null) anim.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f, half = Math.min(getWidth(), getHeight()) / 2f;
        canvas.drawCircle(cx, cy, half, plate);
        double turn = t * Math.PI * 2;
        // 花瓣數 7：振幅在 0.04 到 0.20 之間來回（接近圓 ↔ 明顯的花形），整體慢慢旋轉；再疊一點二次諧波讓形狀不那麼規則
        float amp = 0.12f + 0.08f * (float) Math.sin(turn * 2);
        float rad = half * 0.52f;
        path.reset();
        int steps = 96;
        for (int i = 0; i <= steps; i++) {
            double th = i * Math.PI * 2 / steps;
            double r = rad * (1 + amp * Math.cos(7 * (th + turn)) + 0.03 * Math.cos(3 * th - 2 * turn));
            float x = cx + (float) (r * Math.cos(th)), y = cy + (float) (r * Math.sin(th));
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        path.close();
        canvas.drawPath(path, shape);
    }
}
