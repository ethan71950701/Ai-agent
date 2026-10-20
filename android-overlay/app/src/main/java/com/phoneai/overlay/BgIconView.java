package com.phoneai.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/**
 * 背景模式開關鈕（放在輸入列最左邊，跟右邊的送出鈕對稱）：沒有文字、沒有底色，只有白色線條。
 *  - 開啟（背景模式）：兩支疊在一起的手機輪廓，一支在前、一支在後，意思是「在背面另一個畫面做事」。
 *  - 關閉：只有一個長方形（一支手機輪廓），就是在目前這個畫面上做事。
 */
public class BgIconView extends View {
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float d;
    private boolean on;

    public BgIconView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        setContentDescription("背景模式");
    }

    public void setOn(boolean v) {
        on = v;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float box = Math.min(getWidth(), getHeight());
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float pw = box * 0.24f, ph = box * 0.38f, cr = box * 0.05f;
        line.setStrokeWidth(Math.max(1.6f * d, box * 0.035f));
        if (on) {
            // 後面那支（偏右上、較淡）
            line.setColor(0x88FFFFFF);
            r.set(cx + box * 0.09f - pw / 2f, cy - box * 0.07f - ph / 2f, cx + box * 0.09f + pw / 2f, cy - box * 0.07f + ph / 2f);
            c.drawRoundRect(r, cr, cr, line);
            // 前面那支（偏左下）
            line.setColor(0xFFFFFFFF);
            r.set(cx - box * 0.04f - pw / 2f, cy + box * 0.03f - ph / 2f, cx - box * 0.04f + pw / 2f, cy + box * 0.03f + ph / 2f);
            c.drawRoundRect(r, cr, cr, line);
        } else {
            // 只有一個長方形，置中
            line.setColor(0xCCFFFFFF);
            r.set(cx - pw / 2f, cy - ph / 2f, cx + pw / 2f, cy + ph / 2f);
            c.drawRoundRect(r, cr, cr, line);
        }
    }
}
