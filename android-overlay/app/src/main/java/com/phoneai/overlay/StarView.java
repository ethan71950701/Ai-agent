package com.phoneai.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import androidx.dynamicanimation.animation.DynamicAnimation;

/** 圓角的星星：實心＝開，空心＝關。切換時用彈簧彈一下（setOn 的 animate = true）。 */
final class StarView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final int onColor, offColor;
    private boolean on;

    StarView(Context c, int onColor, int offColor) {
        super(c);
        this.onColor = onColor;
        this.offColor = offColor;
        fill.setStyle(Paint.Style.FILL);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setStrokeCap(Paint.Cap.ROUND);
    }

    boolean isOn() { return on; }

    void setOn(boolean on, boolean animate) {
        this.on = on;
        invalidate();
        if (animate) {
            setScaleX(0.5f);
            setScaleY(0.5f);
            Anim.spring(this, DynamicAnimation.SCALE_X, 1f, 500f, 0.45f);
            Anim.spring(this, DynamicAnimation.SCALE_Y, 1f, 500f, 0.45f);
        }
    }

    @Override
    protected void onMeasure(int w, int h) {
        int s = Ui.dp(getContext(), 36);
        setMeasuredDimension(s, s);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float dp = getResources().getDisplayMetrics().density;
        float cx = getWidth() / 2f, cy = getHeight() / 2f + 0.04f * getHeight();   // 星星的重心偏下，略往下移才看起來置中
        float outer = Math.min(getWidth(), getHeight()) * 0.40f, inner = outer * 0.52f;
        path.reset();
        for (int i = 0; i < 10; i++) {
            double ang = Math.PI * i / 5 - Math.PI / 2;
            float r = (i % 2 == 0) ? outer : inner;
            float x = cx + (float) Math.cos(ang) * r, y = cy + (float) Math.sin(ang) * r;
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        path.close();
        CornerPathEffect round = new CornerPathEffect(outer * 0.30f);   // 尖角削成圓角，不要太尖銳
        fill.setPathEffect(round);
        line.setPathEffect(round);
        line.setStrokeWidth(1.8f * dp);
        if (on) {
            fill.setColor(onColor);
            line.setColor(onColor);
            canvas.drawPath(path, fill);
            canvas.drawPath(path, line);   // 外框也畫一次，圓角更飽滿
        } else {
            line.setColor(offColor);
            canvas.drawPath(path, line);
        }
    }
}
