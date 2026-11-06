package com.phoneai.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.view.View;

/** 首頁中間的標誌：App 圖示那三條點對稱的波（頻率 1.25／2.25／3.25），振幅緩緩起伏，不改變對稱。 */
public class MarkView extends View {
    private static final float[] FREQ = {1.25f, 2.25f, 3.25f};
    private static final float[] WIDTH = {6.2f, 4.2f, 2.8f};
    private static final int[] ALPHA = {255, 190, 130};
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float d;

    public MarkView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(Ui.attr(c, com.google.android.material.R.attr.colorPrimary));
    }

    @Override
    protected void onDraw(Canvas c) {
        float t = SystemClock.uptimeMillis() / 1000f;
        float w = getWidth(), h = getHeight(), cy = h / 2f;
        int steps = 60;
        for (int k = 0; k < 3; k++) {
            float breathe = 0.6f + 0.4f * (float) Math.sin(t * (1.7f + 0.55f * k) + k * 2.1f);   // 振幅明顯起伏
            float wob = 0.55f * (float) Math.sin(t * (1.1f + 0.4f * k) + k);            // 相位來回擺，平均起來仍是點對稱
            path.reset();
            for (int i = 0; i <= steps; i++) {
                float u = i / (float) steps * 2f - 1f;   // -1~1
                float env = (float) Math.pow(Math.cos(u * Math.PI / 2), 2);   // 兩端 0、中間 1，左右對稱
                float y = cy + h * 0.4f * breathe * env * (float) Math.sin(FREQ[k] * Math.PI * u + wob);   // 奇函數：點對稱
                float x = (u + 1f) / 2f * w;
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            p.setStrokeWidth(WIDTH[k] * d * 0.8f);
            p.setAlpha(ALPHA[k]);
            c.drawPath(path, p);
        }
        postInvalidateOnAnimation();
    }
}
