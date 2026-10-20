package com.phoneai.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.widget.FrameLayout;

/** 圖示底板：邊緣像餅乾／花瓣的圓形（仿 Material 3 Expressive 的 cookie 形狀），中間放線條圖示。 */
final class Badge extends FrameLayout {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape = new Path();
    private int lastSize = -1;

    Badge(Context c, String icon, int sizeDp, int fillColor, int iconColor) {
        super(c);
        setWillNotDraw(false);
        fill.setColor(fillColor);
        int s = Ui.dp(c, sizeDp);
        int is = Math.round(s * 0.5f);
        addView(new IconView(c, icon, iconColor), new LayoutParams(is, is, android.view.Gravity.CENTER));
        setLayoutParams(new android.widget.LinearLayout.LayoutParams(s, s));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        if (w != lastSize) {
            lastSize = w;
            shape.reset();
            float r = w / 2f, amp = 0.055f;
            for (int deg = 0; deg <= 360; deg += 2) {
                double t = Math.toRadians(deg);
                float rr = r * (1f - amp + amp * (float) Math.cos(10 * t));
                float x = r + rr * (float) Math.cos(t), y = r + rr * (float) Math.sin(t);
                if (deg == 0) shape.moveTo(x, y); else shape.lineTo(x, y);
            }
            shape.close();
        }
        canvas.drawPath(shape, fill);
    }
}
