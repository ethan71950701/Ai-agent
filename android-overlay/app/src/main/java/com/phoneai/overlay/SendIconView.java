package com.phoneai.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** 送出鈕：沒有底色，只有白色線條的紙飛機，尖端朝正右方。倒數送出時外面有一圈細線慢慢縮短（setProgress 1→0）。 */
public class SendIconView extends View {
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path body = new Path();
    private final RectF oval = new RectF();
    private final float d;
    private float progress;

    public SendIconView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        line.setColor(0xFFFFFFFF);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        ring.setColor(0x99FFFFFF);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeCap(Paint.Cap.ROUND);
        ring.setStrokeWidth(2 * d);
    }

    /** 0 = 不顯示；1 = 整圈。 */
    public void setProgress(float p) {
        progress = Math.max(0f, Math.min(1f, p));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float box = Math.min(getWidth(), getHeight());
        float s = box * 0.46f;                                   // 圖示大小
        float ox = (getWidth() - s) / 2f, oy = (getHeight() - s) / 2f;
        line.setStrokeWidth(Math.max(1.6f * d, s * 0.075f));
        body.reset();
        // 外框：左上、尖端（正右方）、左下、內凹點，封閉
        body.moveTo(ox + 0.04f * s, oy + 0.10f * s);
        body.lineTo(ox + 0.98f * s, oy + 0.50f * s);
        body.lineTo(ox + 0.04f * s, oy + 0.90f * s);
        body.lineTo(ox + 0.26f * s, oy + 0.50f * s);
        body.close();
        c.drawPath(body, line);
        // 摺痕：從內凹點水平畫到尖端
        c.drawLine(ox + 0.26f * s, oy + 0.50f * s, ox + 0.98f * s, oy + 0.50f * s, line);

        if (progress > 0f) {
            float pad = 2 * d;
            oval.set(pad, pad, getWidth() - pad, getHeight() - pad);
            c.drawArc(oval, -90f, 360f * progress, false, ring);
        }
    }
}
