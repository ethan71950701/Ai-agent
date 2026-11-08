package com.phoneai.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/**
 * 助理面板輸入列最左邊的「＋」鈕（跟右邊的送出鈕對稱）：沒有底色，只有白色線條。
 * 點開選單：在這個畫面／背景螢幕操作、上傳圖片。選單打開時 ＋ 轉 45° 變成 ×；背景模式開著時右上角有一個小亮點，提醒任務會在第二個螢幕做。
 */
public class PlusView extends View {
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;
    private boolean badge;

    public PlusView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setColor(0xE6FFFFFF);
        dot.setStyle(Paint.Style.FILL);
        setContentDescription(L.t("更多"));
    }

    /** 背景模式開著時顯示小亮點。 */
    public void setBadge(boolean on, int color) {
        badge = on;
        dot.setColor(color);
        invalidate();
    }

    /** 選單打開（轉成 ×）或關閉（回到 ＋）。 */
    public void setOpen(boolean open) {
        animate().rotation(open ? 45f : 0f).setDuration(180).start();
    }

    @Override
    protected void onDraw(Canvas c) {
        float box = Math.min(getWidth(), getHeight());
        float cx = getWidth() / 2f, cy = getHeight() / 2f, h = box * 0.20f;
        line.setStrokeWidth(Math.max(1.8f * d, box * 0.045f));
        c.drawLine(cx - h, cy, cx + h, cy, line);
        c.drawLine(cx, cy - h, cx, cy + h, line);
        if (badge) {
            c.save();
            c.rotate(-getRotation(), cx, cy);   // 旋轉時亮點留在右上角
            c.drawCircle(cx + box * 0.26f, cy - box * 0.26f, Math.max(3.5f * d, box * 0.075f), dot);
            c.restore();
        }
    }
}
